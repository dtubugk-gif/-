package il.rikavon.feature.blocker.contact

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager

/**
 * Makes the call sound like one: the audio mode of a voice call, the loudspeaker or the earpiece on demand,
 * and the screen off against the cheek while the earpiece is in use. Every step is best-effort; a phone that
 * refuses any of it still plays the voice.
 */
class CallAudioRoute(context: Context) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val proximity: PowerManager.WakeLock? =
        context.getSystemService(PowerManager::class.java)?.let { power ->
            if (power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
            } else {
                null
            }
        }
    private var previousMode = AudioManager.MODE_NORMAL
    private var active = false

    /** The route in effect, so the activity applies a change only when the user flips the switch. */
    var speaker: Boolean = true
        private set

    fun begin(speaker: Boolean) {
        if (active) return
        active = true
        previousMode = audio?.mode ?: AudioManager.MODE_NORMAL
        runCatching { audio?.mode = AudioManager.MODE_IN_COMMUNICATION }
        apply(speaker)
    }

    fun apply(speaker: Boolean) {
        if (!active) return
        this.speaker = speaker
        runCatching { route(speaker) }
        if (speaker) releaseProximity() else acquireProximity()
    }

    fun end() {
        if (!active) return
        active = false
        releaseProximity()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio?.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audio?.isSpeakerphoneOn = false
            }
        }
        runCatching { audio?.mode = previousMode }
    }

    private fun route(speaker: Boolean) {
        val manager = audio ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wanted = if (speaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            manager.availableCommunicationDevices.firstOrNull { it.type == wanted }?.let {
                manager
                    .setCommunicationDevice(
                        it,
                    )
            }
        } else {
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = speaker
        }
    }

    private fun acquireProximity() {
        val lock = proximity ?: return
        if (!lock.isHeld) runCatching { lock.acquire(MAX_CALL_MILLIS) }
    }

    private fun releaseProximity() {
        val lock = proximity ?: return
        if (lock.isHeld) runCatching { lock.release() }
    }

    private companion object {
        const val WAKE_LOCK_TAG = "rikavon:call"
        const val MAX_CALL_MILLIS = 10L * 60L * 1_000L
    }
}
