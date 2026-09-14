package il.rikavon.core.data.repo

import il.rikavon.core.data.db.DailyUsageDao
import il.rikavon.core.data.db.DailyUsageEntity
import il.rikavon.core.data.db.toModel
import il.rikavon.core.data.domain.UsageSessionAggregator
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.model.DailyAppUsage
import il.rikavon.core.data.model.DayUsageSnapshot
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.time.TimeSource
import il.rikavon.core.data.usage.UsageStatsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns today's live usage (in memory, incremental) and the 90-day history (Room).
 */
@Singleton
class UsageRepository @Inject constructor(
    private val source: UsageStatsSource,
    private val dao: DailyUsageDao,
    private val time: TimeSource,
    private val permissions: PermissionChecker,
) {
    private val aggregator = UsageSessionAggregator()
    private var aggregatorDate: LocalDate? = null
    private var lastQueryEnd: Long = 0
    private var lastPersistedAt: Long = 0
    private val mutex = Mutex()

    private val _today = MutableStateFlow(DayUsageSnapshot.empty(time.today(), time.nowMillis()))
    val today: StateFlow<DayUsageSnapshot> = _today.asStateFlow()

    /** Pulls new usage events since the last call and republishes [today]. Safe to call every few seconds. */
    suspend fun refresh(): DayUsageSnapshot =
        mutex.withLock {
            val date = time.today()
            val now = time.nowMillis()
            if (aggregatorDate != date) {
                aggregator.reset()
                aggregatorDate = date
                lastQueryEnd = time.dayStartMillis(date)
            }
            if (!permissions.hasUsageAccess()) {
                return@withLock DayUsageSnapshot.empty(date, now).also { _today.value = it }
            }
            val events = source.events(lastQueryEnd, now)
            aggregator.feed(events)
            lastQueryEnd = now
            val snapshot =
                DayUsageSnapshot(
                    date = date,
                    computedAt = now,
                    perApp = aggregator.snapshot(now),
                    foregroundPackage = aggregator.foregroundPackage,
                    screenOn = aggregator.screenOn,
                )
            _today.value = snapshot
            snapshot
        }

    fun markScreenOn() {
        aggregator.markScreenOn()
    }

    /** Full recomputation of an arbitrary day from the system event log (empty if the system no longer has it). */
    fun computeDay(date: LocalDate): Map<String, AppUsage> {
        val start = time.dayStartMillis(date)
        val end = minOf(time.dayEndMillis(date), time.nowMillis())
        val local = UsageSessionAggregator()
        local.feed(source.events(start, end))
        return local.snapshot(end)
    }

    /** Persists the live snapshot into the daily table, throttled unless [force]. */
    suspend fun persistToday(force: Boolean = false) {
        val snapshot = _today.value
        val now = time.nowMillis()
        if (!force && now - lastPersistedAt < PERSIST_INTERVAL_MILLIS) return
        val rows =
            snapshot.perApp.values
                .filter { it.minutes > 0 || it.opens > 0 }
                .map { DailyUsageEntity(snapshot.date.toString(), it.packageName, it.minutes, it.opens) }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
        lastPersistedAt = now
    }

    suspend fun persistDay(date: LocalDate, usage: Map<String, AppUsage>) {
        val rows =
            usage.values
                .filter { it.minutes > 0 || it.opens > 0 }
                .map { DailyUsageEntity(date.toString(), it.packageName, it.minutes, it.opens) }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
    }

    fun observeHistory(days: Int): Flow<List<DailyAppUsage>> {
        val to = time.today()
        val from = to.minusDays((days - 1).toLong())
        return dao.observeRange(from.toString(), to.toString()).map { rows -> rows.map { it.toModel() } }
    }

    suspend fun history(from: LocalDate, to: LocalDate): List<DailyAppUsage> =
        dao.range(from.toString(), to.toString()).map { it.toModel() }

    suspend fun dayUsageFromHistory(date: LocalDate): Map<String, AppUsage> =
        history(date, date).associate { row ->
            row.packageName to AppUsage(row.packageName, row.minutes, row.opens, emptyList())
        }

    suspend fun pruneOlderThan(days: Int) {
        dao.deleteBefore(time.today().minusDays(days.toLong()).toString())
    }

    suspend fun exportAll(): List<DailyAppUsage> = dao.all().map { it.toModel() }

    suspend fun importAll(rows: List<DailyAppUsage>) {
        dao.upsertAll(rows.map { DailyUsageEntity(it.date.toString(), it.packageName, it.minutes, it.opens) })
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val RETENTION_DAYS = 90
        private const val PERSIST_INTERVAL_MILLIS = 60_000L
    }
}
