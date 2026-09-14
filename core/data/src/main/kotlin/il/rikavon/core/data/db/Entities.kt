package il.rikavon.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage", primaryKeys = ["date", "packageName"])
data class DailyUsageEntity(val date: String, val packageName: String, val minutes: Int, val opens: Int)

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val limitMinutes: Int,
    val fullBlock: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val packagesCsv: String,
    val enabled: Boolean,
)

@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val date: String,
    val score: Int,
    val allUnderLimit: Boolean,
    val totalMinutes: Int,
    val totalOpens: Int,
    val firstOpenMinute: Int?,
    val nightOpens: Int,
    val blocksTriggered: Int,
    val minScore: Int,
    val recoveredFromLow: Boolean,
    val schedulesKept: Int,
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)

@Entity(tableName = "block_events")
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val date: String,
    val packageName: String,
    val reason: String,
)
