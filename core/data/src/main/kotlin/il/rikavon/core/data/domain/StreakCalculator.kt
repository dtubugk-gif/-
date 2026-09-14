package il.rikavon.core.data.domain

import il.rikavon.core.data.model.DailySummary

/** Streak = consecutive finished days on which every tracked app stayed under its limit. */
class StreakCalculator {
    data class Result(val current: Int, val best: Int)

    fun compute(summaries: List<DailySummary>): Result {
        val sorted = summaries.sortedBy { it.date }
        var current = 0
        var best = 0
        var previousDate: java.time.LocalDate? = null
        for (day in sorted) {
            val contiguous = previousDate == null || previousDate.plusDays(1) == day.date
            current =
                if (day.allUnderLimit && contiguous) {
                    current + 1
                } else if (day.allUnderLimit) {
                    1
                } else {
                    0
                }
            best = maxOf(best, current)
            previousDate = day.date
        }
        return Result(current, best)
    }
}
