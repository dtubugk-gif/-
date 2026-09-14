package il.rikavon.core.data.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decides when a day rollover is due. Correctness never depends on the midnight alarm firing:
 * every process start compares the stored rollover date to the current local date.
 */
class DailyResetPolicy {
    /** Dates that still need to be finalised, oldest first. Empty when nothing is due. */
    fun pendingRolloverDates(lastRolloverDate: LocalDate?, today: LocalDate): List<LocalDate> {
        if (lastRolloverDate == null) return emptyList()
        if (!lastRolloverDate.isBefore(today)) return emptyList()
        val pending = mutableListOf<LocalDate>()
        var day: LocalDate = lastRolloverDate
        while (day.isBefore(today) && pending.size < MAX_CATCH_UP_DAYS) {
            pending += day
            day = day.plusDays(1)
        }
        return pending
    }

    fun needsRollover(lastRolloverDate: LocalDate?, today: LocalDate): Boolean =
        lastRolloverDate != null && lastRolloverDate.isBefore(today)

    /** Epoch millis of the next local midnight strictly after [now]. */
    fun nextMidnightMillis(now: ZonedDateTime): Long =
        now
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(now.zone)
            .toInstant()
            .toEpochMilli()

    fun dayStartMillis(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        /** Anything older than this is considered abandoned history and is not back-filled. */
        const val MAX_CATCH_UP_DAYS = 90
    }
}
