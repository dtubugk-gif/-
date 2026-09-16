package il.rikavon.feature.blocker.contact

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.model.DayUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageReminderPolicyTest {
    private val policy = UsageReminderPolicy()
    private val limit = AppLimit("a", 60, fullBlock = false, enabled = true, createdAt = 0)
    private val start = 1_000_000L

    private fun snapshot(foreground: String?, openedAt: Long = start) =
        DayUsageSnapshot(
            date = LocalDate.of(2026, 3, 9),
            computedAt = 0L,
            perApp = mapOf("a" to AppUsage("a", 5, opens = 1, openTimestamps = listOf(openedAt))),
            foregroundPackage = foreground,
            screenOn = true,
        )

    @Test
    fun `nudges once per interval of one sitting, never twice for the same tick`() {
        assertNull(policy.evaluate(snapshot("a"), listOf(limit), 10, start + minutes(9)))
        assertEquals(PetContact.Nudge("a", 10), policy.evaluate(snapshot("a"), listOf(limit), 10, start + minutes(10)))
        assertNull(policy.evaluate(snapshot("a"), listOf(limit), 10, start + minutes(14)))
        assertEquals(PetContact.Nudge("a", 20), policy.evaluate(snapshot("a"), listOf(limit), 10, start + minutes(21)))
    }

    @Test
    fun `a new sitting starts the count over`() {
        policy.evaluate(snapshot("a"), listOf(limit), 10, start + minutes(10))
        val later = start + minutes(30)
        assertNull(policy.evaluate(snapshot("a", openedAt = later), listOf(limit), 10, later + minutes(5)))
        assertEquals(
            PetContact.Nudge("a", 10),
            policy.evaluate(snapshot("a", openedAt = later), listOf(limit), 10, later + minutes(10)),
        )
    }

    @Test
    fun `only limited apps in front, and only when the nudge is on`() {
        assertNull(policy.evaluate(snapshot("b"), listOf(limit), 10, start + minutes(30)))
        assertNull(policy.evaluate(snapshot(null), listOf(limit), 10, start + minutes(30)))
        assertNull(policy.evaluate(snapshot("a"), listOf(limit), 0, start + minutes(30)))
        assertNull(policy.evaluate(snapshot("a"), listOf(limit.copy(enabled = false)), 10, start + minutes(30)))
    }

    private fun minutes(n: Int): Long = n * 60_000L
}
