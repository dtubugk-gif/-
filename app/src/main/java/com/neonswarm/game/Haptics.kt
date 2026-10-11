package com.neonswarm.game

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(ctx: Context, private val prefs: Prefs) {
    private val vib: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ctx.getSystemService(Vibrator::class.java)
        }
    private val hasAmplitude = vib?.hasAmplitudeControl() == true
    private var last = 0L

    /** [strength] is 1..255. Short pulses are throttled so rapid kills don't blur into a buzz. */
    fun pulse(ms: Long, strength: Int) {
        val v = vib ?: return
        if (!prefs.vibration || !v.hasVibrator()) return
        val now = SystemClock.uptimeMillis()
        if (ms < 60 && now - last < 45) return
        last = now
        val duration = if (!hasAmplitude && strength < 100) ms.coerceAtMost(12) else ms
        val amp = if (hasAmplitude) strength.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        try {
            v.vibrate(VibrationEffect.createOneShot(duration, amp))
        } catch (_: Exception) {
        }
    }
}
