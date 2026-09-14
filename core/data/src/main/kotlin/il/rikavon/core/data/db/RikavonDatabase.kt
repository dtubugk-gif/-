package il.rikavon.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailyUsageEntity::class,
        AppLimitEntity::class,
        ScheduleEntity::class,
        DailySummaryEntity::class,
        AchievementEntity::class,
        BlockEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RikavonDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao

    abstract fun appLimitDao(): AppLimitDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun dailySummaryDao(): DailySummaryDao

    abstract fun achievementDao(): AchievementDao

    abstract fun blockEventDao(): BlockEventDao

    companion object {
        const val NAME = "rikavon.db"
    }
}
