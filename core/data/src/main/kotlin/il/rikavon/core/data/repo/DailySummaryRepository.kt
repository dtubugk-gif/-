package il.rikavon.core.data.repo

import il.rikavon.core.data.db.BlockEventDao
import il.rikavon.core.data.db.BlockEventEntity
import il.rikavon.core.data.db.DailySummaryDao
import il.rikavon.core.data.db.toEntity
import il.rikavon.core.data.db.toModel
import il.rikavon.core.data.model.BlockEvent
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.model.DailySummary
import il.rikavon.core.data.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailySummaryRepository @Inject constructor(
    private val summaryDao: DailySummaryDao,
    private val blockEventDao: BlockEventDao,
    private val time: TimeSource,
) {
    val summaries: Flow<List<DailySummary>> = summaryDao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<DailySummary> = summaryDao.all().map { it.toModel() }

    suspend fun byDate(date: LocalDate): DailySummary? = summaryDao.byDate(date.toString())?.toModel()

    suspend fun save(summary: DailySummary) = summaryDao.upsert(summary.toEntity())

    suspend fun importAll(list: List<DailySummary>) = summaryDao.upsertAll(list.map { it.toEntity() })

    suspend fun recordBlock(packageName: String, reason: BlockReason) {
        val now = time.nowMillis()
        blockEventDao.insert(
            BlockEventEntity(
                timestamp = now,
                date = time.today().toString(),
                packageName = packageName,
                reason = reason.name,
            ),
        )
    }

    suspend fun blocksOn(date: LocalDate): List<BlockEvent> =
        blockEventDao.forDate(date.toString()).map {
            it.toModel()
        }

    suspend fun blockCount(date: LocalDate): Int = blockEventDao.countForDate(date.toString())

    suspend fun pruneOlderThan(days: Int) {
        val cutoff = time.today().minusDays(days.toLong()).toString()
        summaryDao.deleteBefore(cutoff)
        blockEventDao.deleteBefore(cutoff)
    }

    suspend fun clear() {
        summaryDao.clear()
        blockEventDao.clear()
    }
}
