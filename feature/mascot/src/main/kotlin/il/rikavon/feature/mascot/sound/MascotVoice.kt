package il.rikavon.feature.mascot.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pet's voice: the device's own text-to-speech engine, tuned per mascot (pitch and rate), so every
 * line the pet says on screen can also be said out loud. Ambient lines go out on the media stream (the one
 * users actually turn up); call lines go out on the voice-call stream so they follow the earpiece / speaker
 * routing and the call volume. Speech ducks whatever is playing and reports through [issue] instead of
 * failing silently. Nothing leaves the device.
 */
@Singleton
class MascotVoice @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceOutput {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    @Volatile
    private var queued = 0

    @Volatile
    private var activeFocus: AudioFocusRequest? = null
    private val _issue = MutableStateFlow<VoiceIssue?>(null)
    override val issue: StateFlow<VoiceIssue?> = _issue.asStateFlow()

    @Volatile
    override var onSpeakingChanged: ((Boolean) -> Unit)? = null

    @Volatile
    override var onLineStarted: ((Int) -> Unit)? = null

    private val audio: AudioManager? get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mediaAttributes = attributes(AudioAttributes.USAGE_MEDIA)
    private val callAttributes = attributes(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    private val mediaFocus = focus(mediaAttributes)
    private val callFocus = focus(callAttributes)

    /** One line, the common case outside a call. */
    fun speak(text: String, profile: VoiceProfile, languageTag: String, prompted: Boolean = false) =
        speakLines(listOf(text), profile, languageTag, prompted)

    override fun speakLines(
        lines: List<String>,
        profile: VoiceProfile,
        languageTag: String,
        prompted: Boolean,
        inCall: Boolean,
    ) {
        val spoken = lines.filter { it.isNotBlank() }
        if (spoken.isEmpty() || (!prompted && !ringerAllowsSound())) return
        if (ready) {
            sayNow(spoken, profile, languageTag, inCall)
        } else {
            pending = { sayNow(spoken, profile, languageTag, inCall) }
            ensureEngine()
        }
    }

    override fun stop() {
        runCatching { engine?.stop() }
        finished()
    }

    private fun sayNow(lines: List<String>, profile: VoiceProfile, languageTag: String, inCall: Boolean) {
        val tts = engine ?: return report(VoiceIssue.NO_ENGINE)
        if (tts.setLanguage(localeFor(languageTag)) < TextToSpeech.LANG_AVAILABLE) return report(VoiceIssue.NO_LANGUAGE)
        tts.setPitch(profile.pitch)
        tts.setSpeechRate(profile.rate)
        tts.setAudioAttributes(if (inCall) callAttributes else mediaAttributes)
        queued = lines.size
        takeFocus(if (inCall) callFocus else mediaFocus)
        val accepted =
            lines
                .mapIndexed { index, line ->
                    val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    tts.speak(line, mode, null, "$UTTERANCE_ID$index")
                }.all { it == TextToSpeech.SUCCESS }
        if (!accepted) {
            // The engine's service is gone; drop it so the next line binds a fresh one.
            release()
            return report(VoiceIssue.NO_ENGINE)
        }
        _issue.value = if (muted(inCall)) VoiceIssue.MUTED else null
    }

    private fun report(issue: VoiceIssue) {
        _issue.value = issue
        finished()
    }

    private fun ensureEngine() {
        if (engine != null) return
        engine =
            TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    engine?.setOnUtteranceProgressListener(progress)
                    pending?.invoke()
                } else {
                    release()
                    report(VoiceIssue.NO_ENGINE)
                }
                pending = null
            }
    }

    private fun release() {
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    private fun takeFocus(request: AudioFocusRequest) {
        val previous = activeFocus
        if (previous != null && previous !== request) runCatching { audio?.abandonAudioFocusRequest(previous) }
        activeFocus = request
        runCatching { audio?.requestAudioFocus(request) }
    }

    /** One utterance ended, however it ended; the last one hands the audio back. */
    private fun utteranceEnded() {
        queued = (queued - 1).coerceAtLeast(0)
        if (queued == 0) finished()
    }

    private fun finished() {
        queued = 0
        activeFocus?.let { request -> runCatching { audio?.abandonAudioFocusRequest(request) } }
        activeFocus = null
        onSpeakingChanged?.invoke(false)
    }

    private val progress =
        object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.removePrefix(UTTERANCE_ID)?.toIntOrNull()?.let { index -> onLineStarted?.invoke(index) }
                onSpeakingChanged?.invoke(true)
            }

            override fun onDone(utteranceId: String?) = utteranceEnded()

            override fun onStop(utteranceId: String?, interrupted: Boolean) = utteranceEnded()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = utteranceEnded()

            override fun onError(utteranceId: String?, errorCode: Int) {
                _issue.value = VoiceIssue.NO_ENGINE
                utteranceEnded()
            }
        }

    private fun ringerAllowsSound(): Boolean = audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    private fun muted(inCall: Boolean): Boolean {
        val manager = audio ?: return false
        val stream = if (inCall) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
        return manager.getStreamVolume(stream) == 0 || manager.isStreamMute(stream)
    }

    private companion object {
        const val UTTERANCE_ID = "mascot_line"

        fun attributes(usage: Int): AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

        fun focus(attributes: AudioAttributes): AudioFocusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()

        /** Engines list Hebrew under the legacy "iw" code as often as "he"; a country-qualified Locale matches both. */
        fun localeFor(tag: String): Locale =
            if (tag.startsWith("he") || tag.startsWith("iw")) Locale("he", "IL") else Locale.forLanguageTag(tag)
    }
}
