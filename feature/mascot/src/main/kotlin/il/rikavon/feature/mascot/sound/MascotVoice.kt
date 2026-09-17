package il.rikavon.feature.mascot.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.repo.CloudKey
import il.rikavon.core.data.repo.CloudKeysRepository
import il.rikavon.core.data.repo.VoiceChoicesRepository
import il.rikavon.feature.mascot.model.VoiceProfile
import il.rikavon.feature.mascot.registry.SelectedMascot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pet's voice. By default the device's own text-to-speech engine, with the best voice it has for the
 * language ([DeviceVoices]) tuned per mascot (pitch and rate), so every line the pet says on screen can also be
 * said out loud and nothing leaves the device. With the user's own cloud key ([CloudKey.VOICE]: Azure Speech
 * with its native Hebrew voices, or OpenAI) the lines are spoken by a natural voice instead, cast per pet by the
 * provider ([AzureSpeech], [VoiceCasting]: the user's pick, the manifest's, or the one that fits the
 * personality), the next line synthesised while the current one plays; the device engine takes over the
 * moment the cloud voice fails, mid-sentence-list if need be. Ambient lines go out on the media stream (the
 * one users actually turn up); call lines go out on the voice-call stream so they follow the earpiece /
 * speaker routing and the call volume. Speech ducks whatever is playing and reports through [issue] instead
 * of failing silently.
 */
@Singleton
class MascotVoice @Inject constructor(
    @ApplicationContext private val context: Context,
    keys: CloudKeysRepository,
    private val choices: VoiceChoicesRepository,
    private val selectedMascot: SelectedMascot,
) : VoiceOutput {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ClipPlayer()
    private var neuralJob: Job? = null

    @Volatile
    private var synthesizer: SpeechSynthesizer? = null
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    @Volatile
    private var queued = 0

    @Volatile
    private var activeFocus: AudioFocusRequest? = null
    private val _issue = MutableStateFlow<VoiceIssue?>(null)
    override val issue: StateFlow<VoiceIssue?> = _issue.asStateFlow()
    private val _speaker = MutableStateFlow<Speaker?>(null)

    /** Which engine said the last line, and why the device's when the cloud voice was set up; null until one did. */
    val speaker: StateFlow<Speaker?> = _speaker.asStateFlow()

    @Volatile
    override var onSpeakingChanged: ((Boolean) -> Unit)? = null

    @Volatile
    override var onLineStarted: ((Int) -> Unit)? = null

    private val audio: AudioManager? get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mediaAttributes = attributes(AudioAttributes.USAGE_MEDIA)
    private val callAttributes = attributes(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    private val mediaFocus = focus(mediaAttributes)
    private val callFocus = focus(callAttributes)

    init {
        scope.launch {
            combine(keys.key(CloudKey.VOICE), keys.voiceRegion) { key, region ->
                key?.let { NeuralSpeech.synthesizer(it, region) }
            }.collect { synthesizer = it }
        }
    }

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
        val cloud = synthesizer
        if (cloud != null) {
            speakNeural(spoken, profile, languageTag, inCall, cloud)
        } else {
            _speaker.value = Speaker.Device
            sayWithEngine(spoken, profile, languageTag, inCall, offset = 0)
        }
    }

    override fun stop() {
        neuralJob?.cancel()
        neuralJob = null
        player.stop()
        runCatching { engine?.stop() }
        finished()
    }

    /**
     * The realistic voice: each line is fetched as a clip with the pet's voice and directions and played, the
     * next one fetched meanwhile so the gaps stay short. The first clip that cannot be fetched hands the rest
     * of the lines to the device engine.
     */
    private fun speakNeural(
        lines: List<String>,
        profile: VoiceProfile,
        languageTag: String,
        inCall: Boolean,
        cloud: SpeechSynthesizer,
    ) {
        neuralJob?.cancel()
        runCatching { engine?.stop() }
        player.stop()
        queued = lines.size
        takeFocus(if (inCall) callFocus else mediaFocus)
        val attributes = if (inCall) callAttributes else mediaAttributes
        neuralJob =
            scope.launch {
                val choice = selectedMascot.current()?.id?.let { choices.current(it) }
                var next = async { cloud.attempt(lines[0], profile, languageTag, choice) }
                for (index in lines.indices) {
                    val attempt = next.await()
                    if (index + 1 < lines.size) {
                        next = async { cloud.attempt(lines[index + 1], profile, languageTag, choice) }
                    }
                    if (attempt is VoiceAttempt.Failed) {
                        next.cancel()
                        _speaker.value = Speaker.DeviceAfterRefusal(attempt.summary())
                        sayWithEngine(lines.drop(index), profile, languageTag, inCall, offset = index)
                        return@launch
                    }
                    val clip = (attempt as VoiceAttempt.Clip).bytes
                    _speaker.value = Speaker.Cloud
                    onLineStarted?.invoke(index)
                    onSpeakingChanged?.invoke(true)
                    _issue.value = if (muted(inCall)) VoiceIssue.MUTED else null
                    player.play(clip, attributes)
                }
                finished()
            }
    }

    private fun sayWithEngine(
        lines: List<String>,
        profile: VoiceProfile,
        languageTag: String,
        inCall: Boolean,
        offset: Int,
    ) {
        if (ready) {
            sayNow(lines, profile, languageTag, inCall, offset)
        } else {
            pending = { sayNow(lines, profile, languageTag, inCall, offset) }
            ensureEngine()
        }
    }

    /** Hands [lines] to the device engine; [offset] is the index of the first one, for [onLineStarted]. */
    private fun sayNow(lines: List<String>, profile: VoiceProfile, languageTag: String, inCall: Boolean, offset: Int) {
        val tts = engine ?: return report(VoiceIssue.NO_ENGINE)
        if (tts.setLanguage(localeFor(languageTag)) < TextToSpeech.LANG_AVAILABLE) return report(VoiceIssue.NO_LANGUAGE)
        bestVoice(tts, languageTag, profile.personality)?.let { runCatching { tts.setVoice(it) } }
        tts.setPitch(profile.pitch)
        tts.setSpeechRate(profile.rate)
        tts.setAudioAttributes(if (inCall) callAttributes else mediaAttributes)
        queued = lines.size
        takeFocus(if (inCall) callFocus else mediaFocus)
        val accepted =
            lines
                .mapIndexed { index, line ->
                    val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    tts.speak(line, mode, null, "$UTTERANCE_ID${offset + index}")
                }.all { it == TextToSpeech.SUCCESS }
        if (!accepted) {
            // The engine's service is gone; drop it so the next line binds a fresh one.
            release()
            return report(VoiceIssue.NO_ENGINE)
        }
        _issue.value = if (muted(inCall)) VoiceIssue.MUTED else null
    }

    /**
     * The engine's best voice for the language rather than its default; engines are allowed to have none, or
     * to throw while listing them, and then the language set above stands.
     */
    private fun bestVoice(tts: TextToSpeech, languageTag: String, personality: String): Voice? {
        val voices = runCatching { tts.voices?.toList() }.getOrNull().orEmpty()
        if (voices.isEmpty()) return null
        val candidates =
            voices.map {
                DeviceVoice(it.name, it.locale.language, it.quality, it.latency, it.isNetworkConnectionRequired)
            }
        val pick = DeviceVoices.pick(candidates, languageTag, personality, online()) ?: return null
        return voices.firstOrNull { it.name == pick.name }
    }

    private fun online(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
