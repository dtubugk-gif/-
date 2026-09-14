package il.rikavon.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {
    @Upsert
    suspend fun upsertAll(rows: List<DailyUsageEntity>)

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    fun observeDay(date: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: String, to: String): Flow<List<DailyUsageEntity>>

    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun range(from: String, to: String): List<DailyUsageEntity>

    @Query("SELECT * FROM daily_usage")
    suspend fun all(): List<DailyUsageEntity>

    @Query("DELETE FROM daily_usage WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM daily_usage")
    suspend fun clear()
}

@Dao
interface AppLimitDao {
    @Upsert
    suspend fun upsert(limit: AppLimitEntity)

    @Upsert
    suspend fun upsertAll(limits: List<AppLimitEntity>)

    @Delete
    suspend fun delete(limit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("SELECT * FROM app_limits ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits ORDER BY createdAt ASC")
    suspend fun all(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    suspend fun byPackage(packageName: String): AppLimitEntity?

    @Query("DELETE FROM app_limits")
    suspend fun clear()
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    @Upsert
    suspend fun upsertAll(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM schedules ORDER BY id ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY id ASC")
    suspend fun all(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun byId(id: Long): ScheduleEntity?

    @Query("DELETE FROM schedules")
    suspend fun clear()
}

@Dao
interface DailySummaryDao {
    @Upsert
    suspend fun upsert(summary: DailySummaryEntity)

    @Upsert
    suspend fun upsertAll(summaries: List<DailySummaryEntity>)

    @Query("SELECT * FROM daily_summary ORDER BY date ASC")
    fun observeAll(): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summary ORDER BY date ASC")
    suspend fun all(): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun byDate(date: String): DailySummaryEntity?

    @Query("DELETE FROM daily_summary WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM daily_summary")
    suspend fun clear()
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<AchievementEntity>)

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun all(): List<AchievementEntity>

    @Query("DELETE FROM achievements")
    suspend fun clear()
}

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEventEntity)

    @Query("SELECT COUNT(*) FROM block_events WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("SELECT * FROM block_events WHERE date = :date ORDER BY timestamp ASC")
    suspend fun forDate(date: String): List<BlockEventEntity>

    @Query("DELETE FROM block_events WHERE date < :before")
    suspend fun deleteBefore(before: String)

    @Query("DELETE FROM block_events")
    suspend fun clear()
}
