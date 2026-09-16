package il.rikavon.feature.blocker.contact

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.model.DayUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PetContactPolicyTest {
    private val policy = PetContactPolicy()
    private val monday: LocalDate = LocalDate.of(2026, 3, 9)
    private val limit = AppLimit("a", 60, fullBlock = false, enabled = true, createdAt = 0)

    private fun snapshot(minutes: Int, foreground: String? = null, date: LocalDate = monday) =
        DayUsageSnapshot(
            date = date,
            computedAt = 0L,
            perApp = mapOf("a" to AppUsage("a", minutes, opens = 1, openTimestamps = emptyList())),
            foregroundPackage = foreground,
            screenOn = true,
        )

    @Test
    fun `a message at eighty percent and another at the limit, each once`() {
        assertEquals(
            listOf(PetContact.Message("a", MessageKind.NEAR_LIMIT, 12)),
            policy.evaluate(snapshot(48), listOf(limit)),
        )
        assertTrue(policy.evaluate(snapshot(50), listOf(limit)).isEmpty())
        assertEquals(
            listOf(PetContact.Message("a", MessageKind.AT_LIMIT, 0)),
            policy.evaluate(snapshot(60), listOf(limit)),
        )
        assertTrue(policy.evaluate(snapshot(70), listOf(limit)).isEmpty())
    }

    @Test
    fun `the near-limit call needs the app on screen`() {
        assertTrue(policy.evaluate(snapshot(58), listOf(limit)).none { it is PetContact.Call })
        val contacts = policy.evaluate(snapshot(58, foreground = "a"), listOf(limit))
        assertEquals(
            listOf(PetContact.Call("a", CallReason.NEAR_LIMIT, 2)),
            contacts.filterIsInstance<PetContact.Call>(),
        )
        assertTrue(policy.evaluate(snapshot(59, foreground = "a"), listOf(limit)).isEmpty())
    }

    @Test
    fun `a full block never messages, the block itself rings`() {
        val full = limit.copy(fullBlock = true)
        assertTrue(policy.evaluate(snapshot(0, foreground = "a"), listOf(full)).isEmpty())
    }

    @Test
    fun `a new day starts over`() {
        policy.evaluate(snapshot(60), listOf(limit))
        assertTrue(policy.evaluate(snapshot(60), listOf(limit)).isEmpty())
        assertEquals(1, policy.evaluate(snapshot(60, date = monday.plusDays(1)), listOf(limit)).size)
    }
}
