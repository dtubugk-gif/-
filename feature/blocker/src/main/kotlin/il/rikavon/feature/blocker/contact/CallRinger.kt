package il.rikavon.feature.blocker.contact

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Rings like the phone does: the device's own ringtone, looped, on the ringtone stream (so the ring volume
 * and silent mode apply), plus the call vibration. Vibrate mode vibrates only; silent mode does neither.
 * Best-effort throughout: a phone that refuses any of it still shows the call.
 */
class CallRinger(private val context: Context) {
    private var ringtone: Ringtone? = null

    /** Starts ringing; [tone] false vibrates only (when something else already plays the sound). */
    fun start(tone: Boolean = true) {
        stop()
        val mode =
            context.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                if (tone) playTone()
                vibrate()
            }
            AudioManager.RINGER_MODE_VIBRATE -> vibrate()
            else -> Unit
        }
    }

    fun stop() {
        ringtone?.let { runCatching { it.stop() } }
        ringtone = null
        runCatching { vibrator()?.cancel() }
    }

    private fun playTone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        val tone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull() ?: return
        tone.audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
        runCatching { tone.play() }.onSuccess { ringtone = tone }
    }

    private fun vibrate() {
        val effect = VibrationEffect.createWaveform(RING_PATTERN, 0)
        runCatching { vibrator()?.vibrate(effect) }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private companion object {
        val RING_PATTERN = longArrayOf(0, 600, 400, 600, 1_200)
    }
}
