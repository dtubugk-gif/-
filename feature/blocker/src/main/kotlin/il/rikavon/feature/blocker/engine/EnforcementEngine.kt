package il.rikavon.feature.blocker.engine

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.model.DayUsageSnapshot
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.repo.Pause
import il.rikavon.core.data.repo.PauseReason
import java.time.ZonedDateTime

data class BlockDecision(
    val packageName: String,
    val reason: BlockReason,
    /** When the user may try again (next midnight for limits, schedule end for schedules, 0 for never/n.a.). */
    val retryAtMillis: Long,
    val scheduleId: Long?,
)

/**
 * Pure decision logic: given what is on screen and what the user configured, should we block? In order of
 * precedence: an active schedule, a pause ("block now" or a session break), then the app's own limit (full
 * block, minutes, opens per day) and finally a sitting that ran past the session length.
 */
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
        pauses: Map<String, Pause> = emptyMap(),
    ): BlockDecision? {
        val foreground = packageName ?: return null
        return scheduleBlocking(foreground, schedules, now)
            ?: pauseBlocking(foreground, pauses, now)
            ?: limitBlocking(foreground, snapshot, limits, now)
    }

    /** Every package that would be blocked right now if it came to the foreground. */
    fun blockedPackages(
        snapshot: DayUsageSnapshot,
        limits: List<AppLimit>,
        schedules: List<Schedule>,
        now: ZonedDateTime,
        pauses: Map<String, Pause> = emptyMap(),
    ): Set<String> {
        val nowMillis = now.toInstant().toEpochMilli()
        val byLimit =
            limits
                .filter { it.enabled }
                .filter { spent(it, snapshot.usageOf(it.packageName)) }
                .map { it.packageName }
        val bySchedule = schedules.filter { it.enabled && isActive(it, now) }.flatMap { it.packages }
        val byPause = pauses.values.filter { it.untilMillis > nowMillis }.map { it.packageName }
        return (byLimit + bySchedule + byPause).toSet()
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

    /** Minutes of the current sitting: since the last open counted today, while the app is in front. */
    fun sessionMinutes(usage: AppUsage, nowMillis: Long): Int {
        val start = usage.openTimestamps.maxOrNull() ?: return 0
        return ((nowMillis - start) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)
    }

    private fun scheduleBlocking(packageName: String, schedules: List<Schedule>, now: ZonedDateTime): BlockDecision? {
        val active =
            schedules.firstOrNull { it.enabled && packageName in it.packages && isActive(it, now) } ?: return null
        return BlockDecision(packageName, BlockReason.SCHEDULE, endMillis(active, now), active.id)
    }

    private fun pauseBlocking(packageName: String, pauses: Map<String, Pause>, now: ZonedDateTime): BlockDecision? {
        val pause = pauses[packageName]?.takeIf { it.untilMillis > now.toInstant().toEpochMilli() } ?: return null
        val reason = if (pause.reason == PauseReason.BREAK) BlockReason.SESSION else BlockReason.PAUSED
        return BlockDecision(packageName, reason, pause.untilMillis, null)
    }

    private fun limitBlocking(
        packageName: String,
        snapshot: DayUsageSnapshot,
        limits: List<AppLimit>,
        now: ZonedDateTime,
    ): BlockDecision? {
        val limit = limits.firstOrNull { it.packageName == packageName && it.enabled } ?: return null
        val usage = snapshot.usageOf(packageName)
        val nowMillis = now.toInstant().toEpochMilli()
        val midnight =
            now
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(now.zone)
                .toInstant()
                .toEpochMilli()
        return when {
            limit.fullBlock -> BlockDecision(packageName, BlockReason.FULL_BLOCK, midnight, null)
            usage.minutes >= limit.limitMinutes -> BlockDecision(packageName, BlockReason.LIMIT_REACHED, midnight, null)
            opensSpent(limit, usage) -> BlockDecision(packageName, BlockReason.OPENS_REACHED, midnight, null)
            sessionSpent(limit, usage, nowMillis) ->
                BlockDecision(
                    packageName,
                    BlockReason.SESSION,
                    nowMillis + AppLimit.BREAK_MINUTES * MILLIS_PER_MINUTE,
                    null,
                )
            else -> null
        }
    }

    /** The limit is used up for the day, whichever way: the app cannot open again until midnight. */
    private fun spent(limit: AppLimit, usage: AppUsage): Boolean =
        limit.fullBlock || usage.minutes >= limit.limitMinutes || opensSpent(limit, usage)

    /** The allowed opens are used; the one past the cap is the one that gets blocked. */
    private fun opensSpent(limit: AppLimit, usage: AppUsage): Boolean =
        limit.maxOpens > 0 && usage.opens > limit.maxOpens

    private fun sessionSpent(limit: AppLimit, usage: AppUsage, nowMillis: Long): Boolean =
        limit.sessionMinutes > 0 && sessionMinutes(usage, nowMillis) >= limit.sessionMinutes

    companion object {
        private const val MINUTES_PER_HOUR = 60
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
