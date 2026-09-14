package il.rikavon.core.data.domain

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage

/** One tracked app as seen by the score. */
data class ScoredApp(val limit: AppLimit, val usage: AppUsage)

data class FocusScore(
    val total: Int,
    val minutesPart: Int,
    val opensPart: Int,
    val streakPart: Int,
    val minutesRatio: Float,
    val opensRatio: Float,
    val longestCleanStreakMinutes: Int,
) {
    companion object {
        val PERFECT =
            FocusScore(
                total = FocusScoreCalculator.MAX_SCORE,
                minutesPart = FocusScoreCalculator.MINUTES_WEIGHT,
                opensPart = FocusScoreCalculator.OPENS_WEIGHT,
                streakPart = FocusScoreCalculator.STREAK_WEIGHT,
                minutesRatio = 0f,
                opensRatio = 0f,
                longestCleanStreakMinutes = FocusScoreCalculator.STREAK_TARGET_MINUTES,
            )
    }
}

/**
 * Focus score 0..100, a pure function of today's usage.
 *
 *  - 50 points: minutes used vs. the daily limit, aggregated over all tracked apps.
 *  - 30 points: number of opens vs. a target of one open per [OPENS_TARGET_MINUTES_PER_OPEN]
 *    minutes of limit (never fewer than [MIN_TARGET_OPENS] per app).
 *  - 20 points: the longest gap between opens of tracked apps today, against
 *    [STREAK_TARGET_MINUTES].
 *
 * A fully blocked app counts as a limit of [FULL_BLOCK_EQUIVALENT_MINUTES] minute.
 */
class FocusScoreCalculator {
    fun calculate(
        apps: List<ScoredApp>,
        dayStartMillis: Long,
        nowMillis: Long,
    ): FocusScore {
        val enabled = apps.filter { it.limit.enabled }
        if (enabled.isEmpty()) return FocusScore.PERFECT

        val totalLimit = enabled.sumOf { it.effectiveLimitMinutes() }.coerceAtLeast(1)
        val totalUsed = enabled.sumOf { it.usage.minutes }
        val minutesRatio = totalUsed.toFloat() / totalLimit
        val minutesPart = (MINUTES_WEIGHT * (1f - minutesRatio).coerceIn(0f, 1f)).roundToIntHalfUp()

        val targetOpens = enabled.sumOf { it.targetOpens() }.coerceAtLeast(1)
        val totalOpens = enabled.sumOf { it.usage.opens }
        val opensRatio = totalOpens.toFloat() / targetOpens
        val opensPart = (OPENS_WEIGHT * (1f - opensRatio).coerceIn(0f, 1f)).roundToIntHalfUp()

        val streakMinutes =
            longestCleanStreakMinutes(
                openTimestamps = enabled.flatMap { it.usage.openTimestamps },
                dayStartMillis = dayStartMillis,
                nowMillis = nowMillis,
            )
        val streakPart =
            (STREAK_WEIGHT * (streakMinutes.toFloat() / STREAK_TARGET_MINUTES).coerceIn(0f, 1f))
                .roundToIntHalfUp()

        return FocusScore(
            total = (minutesPart + opensPart + streakPart).coerceIn(0, MAX_SCORE),
            minutesPart = minutesPart,
            opensPart = opensPart,
            streakPart = streakPart,
            minutesRatio = minutesRatio,
            opensRatio = opensRatio,
            longestCleanStreakMinutes = streakMinutes,
        )
    }

    /** Longest gap (minutes) between consecutive opens, bounded by day start and now. */
    fun longestCleanStreakMinutes(openTimestamps: List<Long>, dayStartMillis: Long, nowMillis: Long): Int {
        val points =
            buildList {
                add(dayStartMillis)
                addAll(openTimestamps.filter { it in dayStartMillis..nowMillis }.sorted())
                add(nowMillis)
            }
        var longest = 0L
        for (i in 1 until points.size) {
            longest = maxOf(longest, points[i] - points[i - 1])
        }
        return (longest / MILLIS_PER_MINUTE).toInt()
    }

    private fun ScoredApp.effectiveLimitMinutes(): Int =
        if (limit.fullBlock) FULL_BLOCK_EQUIVALENT_MINUTES else limit.limitMinutes

    private fun ScoredApp.targetOpens(): Int =
        if (limit.fullBlock) {
            0
        } else {
            (limit.limitMinutes / OPENS_TARGET_MINUTES_PER_OPEN).coerceAtLeast(MIN_TARGET_OPENS)
        }

    private fun Float.roundToIntHalfUp(): Int = kotlin.math.floor(this + ROUND_HALF).toInt()

    companion object {
        const val MAX_SCORE = 100
        const val MINUTES_WEIGHT = 50
        const val OPENS_WEIGHT = 30
        const val STREAK_WEIGHT = 20
        const val STREAK_TARGET_MINUTES = 120
        const val OPENS_TARGET_MINUTES_PER_OPEN = 10
        const val MIN_TARGET_OPENS = 3
        const val FULL_BLOCK_EQUIVALENT_MINUTES = 1
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val ROUND_HALF = 0.5f
    }
}
