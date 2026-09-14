package il.rikavon.feature.mascot.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.feature.mascot.model.VoiceProfile
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pet's voice: the device's own text-to-speech engine, tuned per mascot (pitch and rate), so every
 * line the pet says on screen can also be said out loud. Nothing leaves the device; if the engine or the
 * language pack is missing the call is silently dropped.
 */
@Singleton
class MascotVoice @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    /** Speaks [text] in [languageTag] ("he" / "en") with the character of [profile]; interrupts the previous line. */
    fun speak(text: String, profile: VoiceProfile, languageTag: String) {
        if (text.isBlank() || !ringerAllowsSound()) return
        if (ready) {
            sayNow(text, profile, languageTag)
        } else {
            pending = { sayNow(text, profile, languageTag) }
            ensureEngine()
        }
    }

    private fun sayNow(text: String, profile: VoiceProfile, languageTag: String) {
        val tts = engine ?: return
        val language = tts.setLanguage(Locale.forLanguageTag(languageTag))
        if (language < TextToSpeech.LANG_AVAILABLE) return
        tts.setPitch(profile.pitch)
        tts.setSpeechRate(profile.rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    private fun ensureEngine() {
        if (engine != null) return
        engine =
            TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    engine?.setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    pending?.invoke()
                }
                pending = null
            }
    }

    private fun ringerAllowsSound(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private companion object {
        const val UTTERANCE_ID = "mascot_line"
    }
}
