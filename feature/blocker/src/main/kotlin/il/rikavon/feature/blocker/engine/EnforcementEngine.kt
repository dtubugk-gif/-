package il.rikavon.feature.blocker.engine

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.model.DayUsageSnapshot
import il.rikavon.core.data.model.Schedule
import java.time.ZonedDateTime

data class BlockDecision(
    val packageName: String,
    val reason: BlockReason,
    /** When the user may try again (next midnight for limits, schedule end for schedules). */
    val retryAtMillis: Long,
    val scheduleId: Long?,
)

/** Pure decision logic: given what is on screen and what the user configured, should we block? */
class EnforcementEngine {
    /**
     * Decision for [packageName] (by default whatever usage stats say is in front; the accessibility path
     * passes the package it just saw, which is fresher than the stats).
     */
    fun evaluate(
        snapshot: DayUsageSnapshot,
        limits: List<AppLimit>,
        schedules: List<Schedule>,
        now: ZonedDateTime,
        packageName: String? = snapshot.foregroundPackage,
    ): BlockDecision? {
        val foreground = packageName ?: return null
        scheduleBlocking(foreground, schedules, now)?.let { return it }
        val limit = limits.firstOrNull { it.packageName == foreground && it.enabled } ?: return null
        val usage = snapshot.usageOf(foreground)
        val midnight =
            now
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(now.zone)
                .toInstant()
                .toEpochMilli()
        return when {
            limit.fullBlock -> BlockDecision(foreground, BlockReason.FULL_BLOCK, midnight, null)
            usage.minutes >= limit.limitMinutes -> BlockDecision(foreground, BlockReason.LIMIT_REACHED, midnight, null)
            else -> null
        }
    }

    /** Every package that would be blocked right now if it came to the foreground. */
    fun blockedPackages(
        snapshot: DayUsageSnapshot,
        limits: List<AppLimit>,
        schedules: List<Schedule>,
        now: ZonedDateTime,
    ): Set<String> {
        val byLimit =
            limits
                .filter { it.enabled }
                .filter { it.fullBlock || snapshot.usageOf(it.packageName).minutes >= it.limitMinutes }
                .map { it.packageName }
        val bySchedule = schedules.filter { it.enabled && isActive(it, now) }.flatMap { it.packages }
        return (byLimit + bySchedule).toSet()
    }

    /** Highest used/limit ratio among enabled limits; 0 when nothing is tracked. */
    fun maxUsageRatio(snapshot: DayUsageSnapshot, limits: List<AppLimit>): Float =
        limits.filter { it.enabled && !it.fullBlock }.maxOfOrNull { limit ->
            snapshot.usageOf(limit.packageName).minutes.toFloat() / limit.limitMinutes.coerceAtLeast(1)
        } ?: 0f

    fun isActive(schedule: Schedule, now: ZonedDateTime): Boolean {
        if (!schedule.enabled || schedule.days.isEmpty()) return false
        val minute = now.hour * MINUTES_PER_HOUR + now.minute
        val today = now.dayOfWeek
        return if (schedule.crossesMidnight) {
            (today in schedule.days && minute >= schedule.startMinute) ||
                (today.minus(1) in schedule.days && minute < schedule.endMinute)
        } else {
            today in schedule.days && minute >= schedule.startMinute && minute < schedule.endMinute
        }
    }

    /** Epoch millis at which an active schedule ends. */
    fun endMillis(schedule: Schedule, now: ZonedDateTime): Long {
        val minute = now.hour * MINUTES_PER_HOUR + now.minute
        val endDate =
            if (schedule.crossesMidnight && minute >= schedule.startMinute) {
                now.toLocalDate().plusDays(1)
            } else {
                now.toLocalDate()
            }
        return endDate
            .atStartOfDay(now.zone)
            .plusMinutes(schedule.endMinute.toLong())
            .toInstant()
            .toEpochMilli()
    }

    private fun scheduleBlocking(packageName: String, schedules: List<Schedule>, now: ZonedDateTime): BlockDecision? {
        val active =
            schedules.firstOrNull { it.enabled && packageName in it.packages && isActive(it, now) } ?: return null
        return BlockDecision(packageName, BlockReason.SCHEDULE, endMillis(active, now), active.id)
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60
    }
}
