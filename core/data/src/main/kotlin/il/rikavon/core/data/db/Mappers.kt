package il.rikavon.core.data.db

import il.rikavon.core.data.model.Achievement
import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.BlockEvent
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.model.DailyAppUsage
import il.rikavon.core.data.model.DailySummary
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate

internal fun AppLimitEntity.toModel() = AppLimit(packageName, limitMinutes, fullBlock, enabled, createdAt)

internal fun AppLimit.toEntity() = AppLimitEntity(packageName, limitMinutes, fullBlock, enabled, createdAt)

internal fun DailyUsageEntity.toModel() = DailyAppUsage(LocalDate.parse(date), packageName, minutes, opens)

internal fun DailyAppUsage.toEntity() = DailyUsageEntity(date.toString(), packageName, minutes, opens)

internal fun ScheduleEntity.toModel() =
    Schedule(
        id = id,
        name = name,
        type = runCatching { ScheduleType.valueOf(type) }.getOrDefault(ScheduleType.CUSTOM),
        days = DayOfWeek.entries.filter { daysMask and (1 shl it.ordinal) != 0 }.toSet(),
        startMinute = startMinute,
        endMinute = endMinute,
        packages = packagesCsv.split(',').filter { it.isNotBlank() }.toSet(),
        enabled = enabled,
    )

internal fun Schedule.toEntity() =
    ScheduleEntity(
        id = id,
        name = name,
        type = type.name,
        daysMask = days.fold(0) { acc, day -> acc or (1 shl day.ordinal) },
        startMinute = startMinute,
        endMinute = endMinute,
        packagesCsv = packages.joinToString(","),
        enabled = enabled,
    )

internal fun DailySummaryEntity.toModel() =
    DailySummary(
        date = LocalDate.parse(date),
        score = score,
        allUnderLimit = allUnderLimit,
        totalMinutes = totalMinutes,
        totalOpens = totalOpens,
        firstOpenMinute = firstOpenMinute,
        nightOpens = nightOpens,
        blocksTriggered = blocksTriggered,
        minScore = minScore,
        recoveredFromLow = recoveredFromLow,
        schedulesKept = schedulesKept,
    )

internal fun DailySummary.toEntity() =
    DailySummaryEntity(
        date = date.toString(),
        score = score,
        allUnderLimit = allUnderLimit,
        totalMinutes = totalMinutes,
        totalOpens = totalOpens,
        firstOpenMinute = firstOpenMinute,
        nightOpens = nightOpens,
        blocksTriggered = blocksTriggered,
        minScore = minScore,
        recoveredFromLow = recoveredFromLow,
        schedulesKept = schedulesKept,
    )

internal fun AchievementEntity.toModel(): Achievement? =
    AchievementId.fromKey(id)?.let { Achievement(it, unlockedAt) }

internal fun BlockEventEntity.toModel() =
    BlockEvent(
        timestamp = timestamp,
        packageName = packageName,
        reason = runCatching { BlockReason.valueOf(reason) }.getOrDefault(BlockReason.LIMIT_REACHED),
    )
