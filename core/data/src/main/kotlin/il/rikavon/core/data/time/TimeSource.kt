package il.rikavon.core.data.time

import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable wall clock so that day boundaries are testable. */
@Singleton
class TimeSource @Inject constructor(private val clock: Clock) {
    val zone: ZoneId get() = clock.zone

    fun nowMillis(): Long = clock.millis()

    fun now(): ZonedDateTime = ZonedDateTime.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)

    fun localTime(): LocalTime = LocalTime.now(clock)

    fun todayStartMillis(): Long = today().atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayStartMillis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayEndMillis(date: LocalDate): Long =
        date
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    fun minuteOfDay(millis: Long): Int {
        val time =
            java.time.Instant
                .ofEpochMilli(millis)
                .atZone(zone)
                .toLocalTime()
        return time.hour * MINUTES_PER_HOUR + time.minute
    }

    companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
