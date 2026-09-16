package il.rikavon.feature.blocker.engine

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.model.DayUsageSnapshot
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.data.repo.Pause
import il.rikavon.core.data.repo.PauseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class EnforcementEngineTest {
    private val engine = EnforcementEngine()
    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val monday: ZonedDateTime = ZonedDateTime.of(2026, 3, 9, 14, 0, 0, 0, zone)

    private fun snapshot(foreground: String?, vararg usage: Pair<String, Int>) =
        DayUsageSnapshot(
            date = monday.toLocalDate(),
            computedAt = monday.toInstant().toEpochMilli(),
            perApp =
                usage.associate { (pkg, minutes) ->
                    pkg to
                        AppUsage(pkg, minutes, opens = 1, openTimestamps = emptyList())
                },
            foregroundPackage = foreground,
            screenOn = true,
        )

    private fun limit(pkg: String, minutes: Int, fullBlock: Boolean = false, enabled: Boolean = true) =
        AppLimit(pkg, minutes, fullBlock, enabled, createdAt = 0)

    private fun schedule(
        start: Int,
        end: Int,
        days: Set<DayOfWeek> =
            setOf(
                DayOfWeek.MONDAY,
            ),
        packages: Set<String> = setOf("a"),
    ) =
        Schedule(1, "s", ScheduleType.CUSTOM, days, start, end, packages, enabled = true)

    @Test
    fun `under the limit nothing is blocked`() {
        assertNull(engine.evaluate(snapshot("a", "a" to 10), listOf(limit("a", 30)), emptyList(), monday))
    }

    @Test
    fun `reaching the limit blocks until midnight`() {
        val decision = engine.evaluate(snapshot("a", "a" to 30), listOf(limit("a", 30)), emptyList(), monday)!!
        assertEquals(BlockReason.LIMIT_REACHED, decision.reason)
        assertEquals(
            monday
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli(),
            decision.retryAtMillis,
        )
    }

    @Test
    fun `an explicit package is judged even when usage stats still name another app`() {
        val decision =
            engine.evaluate(
                snapshot("launcher", "a" to 30),
                listOf(limit("a", 30)),
                emptyList(),
                monday,
                packageName = "a",
            )
        assertEquals(BlockReason.LIMIT_REACHED, decision?.reason)
        assertNull(
            engine.evaluate(snapshot("a", "a" to 30), listOf(limit("a", 30)), emptyList(), monday, packageName = "b"),
        )
    }

    @Test
    fun `full block applies with zero usage`() {
        val decision = engine.evaluate(snapshot("a"), listOf(limit("a", 30, fullBlock = true)), emptyList(), monday)!!
        assertEquals(BlockReason.FULL_BLOCK, decision.reason)
    }

    @Test
    fun `disabled limits and other foreground apps are ignored`() {
        assertNull(
            engine.evaluate(snapshot("a", "a" to 99), listOf(limit("a", 30, enabled = false)), emptyList(), monday),
        )
        assertNull(engine.evaluate(snapshot("b", "a" to 99), listOf(limit("a", 30)), emptyList(), monday))
        assertNull(engine.evaluate(snapshot(null, "a" to 99), listOf(limit("a", 30)), emptyList(), monday))
    }

    @Test
    fun `active schedule blocks until its end`() {
        val decision = engine.evaluate(snapshot("a"), emptyList(), listOf(schedule(9 * 60, 17 * 60)), monday)!!
        assertEquals(BlockReason.SCHEDULE, decision.reason)
        assertEquals(
            monday
                .withHour(17)
                .withMinute(0)
                .toInstant()
                .toEpochMilli(),
            decision.retryAtMillis,
        )
    }

    @Test
    fun `schedule on another day does not apply`() {
        assertNull(
            engine.evaluate(
                snapshot("a"),
                emptyList(),
                listOf(schedule(9 * 60, 17 * 60, setOf(DayOfWeek.TUESDAY))),
                monday,
            ),
        )
    }

    @Test
    fun `sleep schedule crossing midnight is active before and after midnight`() {
        val sleep = schedule(23 * 60, 7 * 60, setOf(DayOfWeek.MONDAY))
        val lateMonday = monday.withHour(23).withMinute(30)
        val earlyTuesday = monday.plusDays(1).withHour(6).withMinute(0)
        val tuesdayNoon = monday.plusDays(1).withHour(12)
        assertTrue(engine.isActive(sleep, lateMonday))
        assertTrue(engine.isActive(sleep, earlyTuesday))
        assertFalse(engine.isActive(sleep, tuesdayNoon))
        assertEquals(earlyTuesday.withHour(7).toInstant().toEpochMilli(), engine.endMillis(sleep, lateMonday))
    }

    @Test
    fun `a pause blocks until it ends, and a break reads as a session block`() {
        val nowMillis = monday.toInstant().toEpochMilli()
        val manual = mapOf("a" to Pause("a", nowMillis + 60_000L, PauseReason.MANUAL))
        val decision = engine.evaluate(snapshot("a"), emptyList(), emptyList(), monday, pauses = manual)!!
        assertEquals(BlockReason.PAUSED, decision.reason)
        assertEquals(nowMillis + 60_000L, decision.retryAtMillis)
        val rest = mapOf("a" to Pause("a", nowMillis + 60_000L, PauseReason.BREAK))
        assertEquals(
            BlockReason.SESSION,
            engine.evaluate(snapshot("a"), emptyList(), emptyList(), monday, pauses = rest)?.reason,
        )
        val expired = mapOf("a" to Pause("a", nowMillis - 1L, PauseReason.MANUAL))
        assertNull(engine.evaluate(snapshot("a"), emptyList(), emptyList(), monday, pauses = expired))
    }

    @Test
    fun `the open past the daily open count is the one that gets blocked`() {
        val capped = limit("a", 60).copy(maxOpens = 3)
        val under = snapshot("a", "a" to 1).withOpens("a", 3)
        assertNull(engine.evaluate(under, listOf(capped), emptyList(), monday))
        val over = snapshot("a", "a" to 1).withOpens("a", 4)
        assertEquals(BlockReason.OPENS_REACHED, engine.evaluate(over, listOf(capped), emptyList(), monday)?.reason)
        assertTrue("a" in engine.blockedPackages(over, listOf(capped), emptyList(), monday))
    }

    @Test
    fun `a sitting past the session length blocks for a break`() {
        val nowMillis = monday.toInstant().toEpochMilli()
        val capped = limit("a", 240).copy(sessionMinutes = 20)
        val fresh = snapshot("a", "a" to 5).withOpenAt("a", nowMillis - 19 * 60_000L)
        assertNull(engine.evaluate(fresh, listOf(capped), emptyList(), monday))
        val long = snapshot("a", "a" to 25).withOpenAt("a", nowMillis - 20 * 60_000L)
        val decision = engine.evaluate(long, listOf(capped), emptyList(), monday)!!
        assertEquals(BlockReason.SESSION, decision.reason)
        assertEquals(nowMillis + AppLimit.BREAK_MINUTES * 60_000L, decision.retryAtMillis)
    }

    private fun DayUsageSnapshot.withOpens(pkg: String, opens: Int) =
        copy(perApp = perApp + (pkg to usageOf(pkg).copy(opens = opens)))

    private fun DayUsageSnapshot.withOpenAt(pkg: String, at: Long) =
        copy(perApp = perApp + (pkg to usageOf(pkg).copy(openTimestamps = listOf(at))))

    @Test
    fun `blocked packages include limit and schedule hits`() {
        val blocked =
            engine.blockedPackages(
                snapshot("x", "a" to 30, "b" to 5),
                listOf(limit("a", 30), limit("b", 30), limit("c", 1, fullBlock = true)),
                listOf(schedule(9 * 60, 17 * 60, packages = setOf("d"))),
                monday,
                pauses = mapOf("e" to Pause("e", monday.toInstant().toEpochMilli() + 1_000L, PauseReason.MANUAL)),
            )
        assertEquals(setOf("a", "c", "d", "e"), blocked)
    }

    @Test
    fun `max usage ratio ignores full blocks`() {
        val ratio =
            engine.maxUsageRatio(
                snapshot("x", "a" to 24, "b" to 100),
                listOf(limit("a", 30), limit("b", 5, fullBlock = true)),
            )
        assertEquals(0.8f, ratio, 0.001f)
    }
}

class PollingPolicyTest {
    private val policy = PollingPolicy()

    @Test
    fun `screen off stops polling`() {
        assertNull(
            policy.intervalMillis(screenOn = false, foregroundTracked = true, anyBlockable = true, maxUsageRatio = 1f),
        )
    }

    @Test
    fun `normal medium and fast tiers follow the usage ratio`() {
        assertEquals(PollingPolicy.NORMAL_MILLIS, policy.intervalMillis(true, false, false, 0.5f))
        assertEquals(PollingPolicy.MEDIUM_MILLIS, policy.intervalMillis(true, false, false, 0.8f))
        assertEquals(PollingPolicy.FAST_MILLIS, policy.intervalMillis(true, false, false, 0.95f))
    }

    @Test
    fun `a tracked app on screen or a blockable app forces the fast tier`() {
        assertEquals(PollingPolicy.FAST_MILLIS, policy.intervalMillis(true, true, false, 0f, instantPath = true))
        assertEquals(PollingPolicy.WATCH_MILLIS, policy.intervalMillis(true, false, false, 0.5f, watchingOpens = true))
        assertEquals(
            PollingPolicy.NORMAL_MILLIS,
            policy.intervalMillis(true, false, false, 0.5f, instantPath = true, watchingOpens = true),
        )
        assertEquals(PollingPolicy.FAST_MILLIS, policy.intervalMillis(true, false, true, 0f, instantPath = true))
    }

    @Test
    fun `a blockable app without the accessibility service polls every second`() {
        assertEquals(PollingPolicy.INSTANT_MILLIS, policy.intervalMillis(true, false, true, 0f))
        assertEquals(PollingPolicy.FAST_MILLIS, policy.intervalMillis(true, false, true, 0f, instantPath = true))
    }
}
