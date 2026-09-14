package il.rikavon.core.data.domain

import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.model.DailySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AchievementAndStreakTest {
    private fun day(
        offset: Int,
        score: Int = 80,
        underLimit: Boolean = true,
        opens: Int = 10,
        nightOpens: Int = 0,
        firstOpen: Int? = 10 * 60,
        recovered: Boolean = false,
        kept: Int = 0,
    ) = DailySummary(
        date = LocalDate.of(2026, 1, 1).plusDays(offset.toLong()),
        score = score,
        allUnderLimit = underLimit,
        totalMinutes = 30,
        totalOpens = opens,
        firstOpenMinute = firstOpen,
        nightOpens = nightOpens,
        blocksTriggered = 0,
        minScore = score,
        recoveredFromLow = recovered,
        schedulesKept = kept,
    )

    @Test
    fun `streak counts consecutive clean days and breaks on a dirty day`() {
        val days = listOf(day(0), day(1), day(2, underLimit = false), day(3), day(4))
        val result = StreakCalculator().compute(days)
        assertEquals(2, result.current)
        assertEquals(2, result.best)
    }

    @Test
    fun `streak breaks on a missing day`() {
        val days = listOf(day(0), day(1), day(3))
        assertEquals(1, StreakCalculator().compute(days).current)
    }

    @Test
    fun `three and seven day streaks unlock in order`() {
        val evaluator = AchievementEvaluator()
        val three =
            evaluator.earned(
                AchievementEvaluator.Input(
                    List(3) {
                        day(it)
                    },
                    currentStreak = 3,
                    hasAnyLimit = true,
                ),
            )
        assertTrue(AchievementId.STREAK_3 in three)
        assertFalse(AchievementId.STREAK_7 in three)
        val seven =
            evaluator.earned(
                AchievementEvaluator.Input(
                    List(7) {
                        day(it)
                    },
                    currentStreak = 7,
                    hasAnyLimit = true,
                ),
            )
        assertTrue(AchievementId.STREAK_7 in seven)
    }

    @Test
    fun `fifty fewer opens than the previous week unlocks`() {
        val lastWeek = List(7) { day(it, opens = 20) }
        val thisWeek = List(7) { day(7 + it, opens = 12) }
        val evaluator = AchievementEvaluator()
        assertEquals(56, evaluator.fewerOpensThanPreviousWeek(lastWeek + thisWeek))
        assertTrue(
            AchievementId.FEWER_OPENS_50 in evaluator.earned(AchievementEvaluator.Input(lastWeek + thisWeek, 0, true)),
        )
    }

    @Test
    fun `quiet nights need three consecutive days`() {
        val evaluator = AchievementEvaluator()
        val broken = listOf(day(0), day(1, nightOpens = 2), day(2), day(3))
        assertFalse(AchievementId.NIGHT_QUIET_3 in evaluator.earned(AchievementEvaluator.Input(broken, 0, true)))
        val quiet = listOf(day(0), day(1), day(2))
        assertTrue(AchievementId.NIGHT_QUIET_3 in evaluator.earned(AchievementEvaluator.Input(quiet, 0, true)))
    }

    @Test
    fun `late start counts days whose first open is after nine`() {
        val evaluator = AchievementEvaluator()
        val days = List(4) { day(it, firstOpen = 9 * 60 + 5) } + day(4, firstOpen = null)
        assertTrue(AchievementId.LATE_START_5 in evaluator.earned(AchievementEvaluator.Input(days, 0, true)))
    }

    @Test
    fun `perfect and recovery come from single days`() {
        val evaluator = AchievementEvaluator()
        val earned =
            evaluator.earned(
                AchievementEvaluator.Input(listOf(day(0, score = 100, recovered = true)), 1, true),
            )
        assertTrue(AchievementId.PERFECT_100 in earned)
        assertTrue(AchievementId.SCORE_90 in earned)
        assertTrue(AchievementId.RECOVERY in earned)
        assertTrue(AchievementId.FIRST_CLEAN_DAY in earned)
        assertTrue(AchievementId.FIRST_LIMIT in earned)
    }
}
