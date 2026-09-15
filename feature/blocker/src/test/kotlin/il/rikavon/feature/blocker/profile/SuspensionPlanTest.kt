package il.rikavon.feature.blocker.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class SuspensionPlanTest {
    @Test
    fun `blocked tracked packages are suspended once and restored when unblocked`() {
        val first = SuspensionPlan.plan(tracked = setOf("a", "b"), blocked = setOf("a"), suspendedNow = emptySet())
        assertEquals(setOf("a"), first.toSuspend)
        assertEquals(emptySet<String>(), first.toUnsuspend)

        val steady = SuspensionPlan.plan(tracked = setOf("a", "b"), blocked = setOf("a"), suspendedNow = setOf("a"))
        assertEquals(emptySet<String>(), steady.toSuspend)
        assertEquals(emptySet<String>(), steady.toUnsuspend)

        val midnight = SuspensionPlan.plan(tracked = setOf("a", "b"), blocked = emptySet(), suspendedNow = setOf("a"))
        assertEquals(setOf("a"), midnight.toUnsuspend)
    }

    @Test
    fun `packages the user does not track are never touched`() {
        val plan = SuspensionPlan.plan(tracked = setOf("a"), blocked = setOf("a", "z"), suspendedNow = setOf("y"))
        assertEquals(setOf("a"), plan.toSuspend)
        assertEquals(emptySet<String>(), plan.toUnsuspend)
    }
}
