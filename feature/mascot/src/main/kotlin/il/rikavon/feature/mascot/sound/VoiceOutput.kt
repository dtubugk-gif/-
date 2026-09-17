package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.coroutines.flow.StateFlow

/** Why the pet could not be heard on the last attempt; the UI turns each into a hint with a fix. */
enum class VoiceIssue {
    /** No text-to-speech engine answered, or it refused the line. */
    NO_ENGINE,

    /** The engine has no voice for the requested language. */
    NO_LANGUAGE,

    /** The line was spoken, but the volume it plays on is at zero. */
    MUTED,
}

/** Which engine said the pet's last line, so the settings screen can show what is really being heard. */
sealed interface Speaker {
    /** The realistic (cloud) voice answered and spoke. */
    data object Cloud : Speaker

    /** The device's own engine, because no cloud voice is set up. */
    data object Device : Speaker

    /** The device's own engine, because the cloud voice refused; [reason] is its status and what it said. */
    data class DeviceAfterRefusal(val reason: String) : Speaker
}

/** Something that can say the pet's lines out loud; [MascotVoice] on a device, a fake in tests. */
interface VoiceOutput {
    /** The result of the last attempt to speak: null when the pet was heard. */
    val issue: StateFlow<VoiceIssue?>

    /** Told when the pet starts and stops talking (possibly from another thread). One observer at a time. */
    var onSpeakingChanged: ((Boolean) -> Unit)?

    /** Told with the index of each line as it starts (possibly from another thread). One observer at a time. */
    var onLineStarted: ((Int) -> Unit)?

    /**
     * Speaks [lines] one after another in [languageTag] ("he" / "en") with the character of [profile]; the
     * first interrupts whatever was playing. Unprompted lines (a tap on the pet, the block screen) stay quiet
     * while the ringer is on vibrate or silent; a line the user asked for ([prompted]) always plays. [inCall]
     * routes the voice like a phone call (earpiece or speaker, the call volume) instead of the media stream.
     */
    fun speakLines(
        lines: List<String>,
        profile: VoiceProfile,
        languageTag: String,
        prompted: Boolean = false,
        inCall: Boolean = false,
    )

    fun stop()
}
