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
            policy.evaluate(snapshot(48), listOf(limit), emptyMap()),
        )
        assertTrue(policy.evaluate(snapshot(50), listOf(limit), emptyMap()).isEmpty())
        assertEquals(
            listOf(PetContact.Message("a", MessageKind.AT_LIMIT, 0)),
            policy.evaluate(snapshot(60), listOf(limit), emptyMap()),
        )
        assertTrue(policy.evaluate(snapshot(70), listOf(limit), emptyMap()).isEmpty())
    }

    @Test
    fun `the near-limit call needs the app on screen`() {
        assertTrue(policy.evaluate(snapshot(58), listOf(limit), emptyMap()).none { it is PetContact.Call })
        val contacts = policy.evaluate(snapshot(58, foreground = "a"), listOf(limit), emptyMap())
        assertEquals(
            listOf(PetContact.Call("a", CallReason.NEAR_LIMIT, 2)),
            contacts.filterIsInstance<PetContact.Call>(),
        )
        assertTrue(policy.evaluate(snapshot(59, foreground = "a"), listOf(limit), emptyMap()).isEmpty())
    }

    @Test
    fun `every third block of the same app rings once`() {
        assertTrue(policy.evaluate(snapshot(0), emptyList(), mapOf("a" to 2)).isEmpty())
        assertEquals(
            listOf(PetContact.Call("a", CallReason.REPEATED_BLOCKS, 0)),
            policy.evaluate(snapshot(0), emptyList(), mapOf("a" to 3)),
        )
        assertTrue(policy.evaluate(snapshot(0), emptyList(), mapOf("a" to 3)).isEmpty())
        assertEquals(1, policy.evaluate(snapshot(0), emptyList(), mapOf("a" to 6)).size)
    }

    @Test
    fun `a new day starts over`() {
        policy.evaluate(snapshot(60), listOf(limit), emptyMap())
        assertTrue(policy.evaluate(snapshot(60), listOf(limit), emptyMap()).isEmpty())
        assertEquals(1, policy.evaluate(snapshot(60, date = monday.plusDays(1)), listOf(limit), emptyMap()).size)
    }
}
