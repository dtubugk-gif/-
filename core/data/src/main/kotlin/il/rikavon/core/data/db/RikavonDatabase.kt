package il.rikavon.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DailyUsageEntity::class,
        AppLimitEntity::class,
        ScheduleEntity::class,
        DailySummaryEntity::class,
        AchievementEntity::class,
        BlockEventEntity::class,
    ],
    version = 3,
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

        /** Open-count and session limits on app_limits, both off by default. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE app_limits ADD COLUMN maxOpens INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE app_limits ADD COLUMN sessionMinutes INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** The "calls on every open" switch on app_limits, off by default. */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE app_limits ADD COLUMN callOnOpen INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}
