package com.dtubugk.shakelight

import com.dtubugk.shakelight.shake.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ShakeDetectorTest {

    private val gravity = 9.80665f
    private var shakes = 0
    private lateinit var detector: ShakeDetector

    @Before
    fun setUp() {
        shakes = 0
        detector = ShakeDetector { shakes++ }.apply {
            thresholdG = 2.8f
            requiredPeaks = 3
        }
    }

    /** Feeds alternating strong/calm samples: one peak every [periodMs]. */
    private fun shake(peaks: Int, startMs: Long, periodMs: Long, peakG: Float = 3.2f): Long {
        var t = startMs
        repeat(peaks) {
            detector.onSample(0f, 0f, peakG * gravity, t)
            detector.onSample(0f, 0f, 1f * gravity, t + periodMs / 2)
            t += periodMs
        }
        return t
    }

    @Test
    fun deliberateShakeTriggersOnce() {
        shake(peaks = 3, startMs = 10_000, periodMs = 200)
        assertEquals(1, shakes)
    }

    @Test
    fun tooFewPeaksDoNotTrigger() {
        shake(peaks = 2, startMs = 10_000, periodMs = 200)
        assertEquals(0, shakes)
    }

    @Test
    fun walkingLevelForcesAreIgnored() {
        shake(peaks = 20, startMs = 10_000, periodMs = 500, peakG = 1.6f)
        assertEquals(0, shakes)
    }

    @Test
    fun slowSeparatePeaksOutsideWindowDoNotTrigger() {
        shake(peaks = 5, startMs = 10_000, periodMs = 1300)
        assertEquals(0, shakes)
    }

    @Test
    fun oneLongJoltCountsAsOnePeak() {
        for (i in 0 until 30) detector.onSample(0f, 0f, 3.5f * gravity, 10_000L + i * 20)
        assertEquals(0, shakes)
    }

    @Test
    fun cooldownPreventsDoubleToggle() {
        val end = shake(peaks = 6, startMs = 10_000, periodMs = 150)
        assertEquals(1, shakes)
        shake(peaks = 3, startMs = end + ShakeDetector.COOLDOWN_MS, periodMs = 200)
        assertEquals(2, shakes)
    }

    @Test
    fun suppressedShakeIsIgnored() {
        detector.isSuppressed = { true }
        shake(peaks = 3, startMs = 10_000, periodMs = 200)
        assertEquals(0, shakes)
    }

    @Test
    fun sensitivityMapsToThreshold() {
        assertEquals(3.6f, ShakeDetector.thresholdFor(1), 0.001f)
        assertEquals(1.8f, ShakeDetector.thresholdFor(10), 0.001f)
        assertEquals(1.8f, ShakeDetector.thresholdFor(99), 0.001f)
    }
}
