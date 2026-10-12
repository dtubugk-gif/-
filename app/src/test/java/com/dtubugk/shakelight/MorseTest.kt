package com.dtubugk.shakelight

import com.dtubugk.shakelight.core.Morse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseTest {

    @Test
    fun sosHasStandardTiming() {
        val u = 100L
        val expected = listOf(
            true to u, false to u, true to u, false to u, true to u, // S
            false to 3 * u,
            true to 3 * u, false to u, true to 3 * u, false to u, true to 3 * u, // O
            false to 3 * u,
            true to u, false to u, true to u, false to u, true to u, // S
        )
        assertEquals(expected, Morse.timings("sos", u))
    }

    @Test
    fun wordsAreSeparatedBySevenUnits() {
        val steps = Morse.timings("E E", 10)
        assertEquals(listOf(true to 10L, false to 70L, true to 10L), steps)
    }

    @Test
    fun unsupportedTextGivesNothing() {
        assertTrue(Morse.timings("שלום", 10).isEmpty())
        assertTrue(Morse.timings("   ", 10).isEmpty())
    }
}
