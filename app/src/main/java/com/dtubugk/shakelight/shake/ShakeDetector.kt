package com.dtubugk.shakelight.shake

import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a deliberate shake from raw accelerometer samples.
 *
 * A "peak" is the acceleration rising above [thresholdG]. It only counts again after the
 * reading has dropped back below [REARM_RATIO] of the threshold, so one long jolt can't
 * count as several peaks. [requiredPeaks] peaks inside [WINDOW_MS] trigger [onShake].
 * Walking, running and a phone bouncing in a bag rarely produce that pattern.
 */
class ShakeDetector(private val onShake: () -> Unit) {

    var thresholdG = thresholdFor(5)
    var requiredPeaks = 3

    /** Return true to ignore shakes right now (for example, while the phone is in a pocket). */
    var isSuppressed: () -> Boolean = { false }

    private val peaks = ArrayDeque<Long>()
    private var armed = true
    private var lastPeakMs = 0L
    private var lastTriggerMs = -COOLDOWN_MS

    fun onSample(x: Float, y: Float, z: Float, timeMs: Long) {
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        if (!armed) {
            if (g < thresholdG * REARM_RATIO) armed = true
            return
        }
        if (g < thresholdG) return
        armed = false

        if (timeMs - lastTriggerMs < COOLDOWN_MS) return
        if (timeMs - lastPeakMs < MIN_PEAK_GAP_MS) return
        lastPeakMs = timeMs

        peaks.addLast(timeMs)
        while (peaks.isNotEmpty() && timeMs - peaks.first() > WINDOW_MS) peaks.removeFirst()

        if (peaks.size >= requiredPeaks) {
            peaks.clear()
            lastTriggerMs = timeMs
            if (!isSuppressed()) onShake()
        }
    }

    fun reset() {
        peaks.clear()
        armed = true
    }

    companion object {
        const val WINDOW_MS = 1200L
        const val MIN_PEAK_GAP_MS = 80L
        const val COOLDOWN_MS = 1500L
        const val REARM_RATIO = 0.75f

        /** Sensitivity 1 (needs a hard shake, 3.6 g) … 10 (light shake, 1.8 g). */
        fun thresholdFor(sensitivity: Int): Float = 3.6f - (sensitivity.coerceIn(1, 10) - 1) * 0.2f
    }
}
