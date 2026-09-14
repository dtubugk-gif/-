package il.rikavon.core.data.domain

import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.model.DailySummary

/**
 * Evaluates which achievements are earned given the finished-day history.
 * Pure: the caller persists newly earned ids.
 */
class AchievementEvaluator {
    data class Input(
        /** Finished days, newest last. */
        val summaries: List<DailySummary>,
        val currentStreak: Int,
        val hasAnyLimit: Boolean,
    )

    fun earned(input: Input): Set<AchievementId> {
        val result = mutableSetOf<AchievementId>()
        val days = input.summaries.sortedBy { it.date }
        if (input.hasAnyLimit) result += AchievementId.FIRST_LIMIT
        if (days.any { it.allUnderLimit }) result += AchievementId.FIRST_CLEAN_DAY
        if (input.currentStreak >= STREAK_3) result += AchievementId.STREAK_3
        if (input.currentStreak >= STREAK_7) result += AchievementId.STREAK_7
        if (input.currentStreak >= STREAK_30) result += AchievementId.STREAK_30
        if (days.any { it.score >= SCORE_90 }) result += AchievementId.SCORE_90
        if (days.any { it.score >= FocusScoreCalculator.MAX_SCORE }) result += AchievementId.PERFECT_100
        if (days.any { it.recoveredFromLow }) result += AchievementId.RECOVERY
        if (consecutive(days, QUIET_NIGHTS) { it.nightOpens == 0 }) result += AchievementId.NIGHT_QUIET_3
        if (days.sumOf { it.schedulesKept } >= SCHEDULES_KEPT) result += AchievementId.SCHEDULE_KEPT_5
        if (days.count { (it.firstOpenMinute ?: NO_OPENS_MINUTE) >= LATE_START_MINUTE } >= LATE_START_DAYS) {
            result += AchievementId.LATE_START_5
        }
        if (fewerOpensThanPreviousWeek(days) >= FEWER_OPENS) result += AchievementId.FEWER_OPENS_50
        return result
    }

    /** Opens in the last 7 finished days minus the 7 before that; positive means fewer opens now. */
    fun fewerOpensThanPreviousWeek(days: List<DailySummary>): Int {
        if (days.size < WEEK * 2) return 0
        val sorted = days.sortedBy { it.date }
        val thisWeek = sorted.takeLast(WEEK).sumOf { it.totalOpens }
        val lastWeek = sorted.dropLast(WEEK).takeLast(WEEK).sumOf { it.totalOpens }
        return lastWeek - thisWeek
    }

    private fun consecutive(days: List<DailySummary>, count: Int, predicate: (DailySummary) -> Boolean): Boolean {
        var run = 0
        for (day in days) {
            run = if (predicate(day)) run + 1 else 0
            if (run >= count) return true
        }
        return false
    }

    companion object {
        const val STREAK_3 = 3
        const val STREAK_7 = 7
        const val STREAK_30 = 30
        const val SCORE_90 = 90
        const val QUIET_NIGHTS = 3
        const val SCHEDULES_KEPT = 5
        const val LATE_START_DAYS = 5
        const val LATE_START_MINUTE = 9 * 60
        const val FEWER_OPENS = 50
        const val WEEK = 7
        private const val NO_OPENS_MINUTE = 24 * 60
    }
}
