package il.rikavon.core.data.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyResetPolicyTest {
    private val policy = DailyResetPolicy()
    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")

    @Test
    fun `same day needs no rollover`() {
        val today = LocalDate.of(2026, 3, 10)
        assertFalse(policy.needsRollover(today, today))
        assertTrue(policy.pendingRolloverDates(today, today).isEmpty())
    }

    @Test
    fun `first launch with no stored date needs no rollover`() {
        assertFalse(policy.needsRollover(null, LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun `phone off at midnight rolls over the missed day on next launch`() {
        val last = LocalDate.of(2026, 3, 9)
        val today = LocalDate.of(2026, 3, 10)
        assertTrue(policy.needsRollover(last, today))
        assertEquals(listOf(last), policy.pendingRolloverDates(last, today))
    }

    @Test
    fun `several missed days are finalised oldest first`() {
        val last = LocalDate.of(2026, 3, 7)
        val today = LocalDate.of(2026, 3, 10)
        assertEquals(
            listOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 9)),
            policy.pendingRolloverDates(last, today),
        )
    }

    @Test
    fun `catch up is capped at the retention window`() {
        val last = LocalDate.of(2020, 1, 1)
        val today = LocalDate.of(2026, 3, 10)
        assertEquals(DailyResetPolicy.MAX_CATCH_UP_DAYS, policy.pendingRolloverDates(last, today).size)
    }

    @Test
    fun `next midnight is local midnight of the following day`() {
        val now = ZonedDateTime.of(2026, 3, 10, 23, 59, 30, 0, zone)
        val expected = ZonedDateTime.of(2026, 3, 11, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, policy.nextMidnightMillis(now))
    }

    @Test
    fun `next midnight handles daylight saving transitions`() {
        // Israel switched to DST on 2026-03-27; the "day" is 23 hours long.
        val now = ZonedDateTime.of(2026, 3, 26, 12, 0, 0, 0, zone)
        val expected =
            LocalDate
                .of(2026, 3, 27)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(expected, policy.nextMidnightMillis(now))
    }
}
