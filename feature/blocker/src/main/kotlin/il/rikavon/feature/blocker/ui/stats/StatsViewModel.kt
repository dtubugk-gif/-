package il.rikavon.feature.blocker.ui.stats

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.domain.FocusScore
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.model.DailyAppUsage
import il.rikavon.core.data.model.DayUsageSnapshot
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class StatsRange(val days: Int) { WEEK(7), MONTH(30) }

data class DayPoint(val date: LocalDate, val minutes: Int, val opens: Int)

data class AppStat(val packageName: String, val label: String, val usage: AppUsage, val limit: AppLimit?)

data class WeekComparison(val minutes: Int, val previousMinutes: Int, val opens: Int, val previousOpens: Int)

data class StatsUiState(
    val range: StatsRange = StatsRange.WEEK,
    val days: List<DayPoint> = emptyList(),
    val todayApps: List<AppStat> = emptyList(),
    val opensByHour: List<Int> = List(HOURS) { 0 },
    val comparison: WeekComparison? = null,
    val score: FocusScore = FocusScore.PERFECT,
    val hasUsagePermission: Boolean = true,
    val hasLimits: Boolean = false,
    val streak: Int = 0,
    val todayMinutes: Int = 0,
    val todayOpens: Int = 0,
    val peakHour: Int? = null,
    /** Tracked minutes this week vs. the previous week, in percent; null without history. */
    val weekDeltaPercent: Int? = null,
) {
    companion object {
        const val HOURS = 24
    }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val usage: UsageRepository,
    limits: LimitsRepository,
    private val installed: InstalledAppsSource,
    private val time: TimeSource,
    scoreProvider: FocusScoreProvider,
    permissions: PermissionChecker,
    settings: SettingsRepository,
) : ViewModel() {
    private val range = MutableStateFlow(StatsRange.WEEK)
    private val hasPermission = permissions.hasUsageAccess()

    private val history = range.flatMapLatest { usage.observeHistory(TWO_WEEKS.coerceAtLeast(it.days)) }

    val state: StateFlow<StatsUiState> =
        combine(range, history, usage.today, limits.limits, scoreProvider.score) { r, rows, today, limitList, score ->
            val tracked = limitList.map { it.packageName }.toSet()
            val byPackage = limitList.associateBy { it.packageName }
            val trackedRows = rows.filter { it.packageName in tracked }
            val live = today.perApp.values.filter { it.packageName in tracked }
            val hourly = opensByHour(live)
            val comparison = comparison(trackedRows, today, tracked)
            StatsUiState(
                range = r,
                days = dayPoints(trackedRows, today, tracked, r.days),
                todayApps =
                    limitList
                        .map { limit ->
                            AppStat(
                                limit.packageName,
                                installed.label(limit.packageName),
                                today.usageOf(limit.packageName),
                                byPackage[limit.packageName],
                            )
                        }.sortedByDescending { it.usage.minutes },
                opensByHour = hourly,
                comparison = comparison,
                score = score,
                hasUsagePermission = hasPermission,
                hasLimits = limitList.isNotEmpty(),
                todayMinutes = live.sumOf { it.minutes },
                todayOpens = live.sumOf { it.opens },
                peakHour =
                    hourly
                        .withIndex()
                        .filter { it.value > 0 }
                        .maxByOrNull { it.value }
                        ?.index,
                weekDeltaPercent =
                    comparison?.takeIf { it.previousMinutes > 0 }?.let {
                        ((it.minutes - it.previousMinutes) * PERCENT / it.previousMinutes)
                    },
            )
        }.combine(settings.settings) { s, prefs -> s.copy(streak = prefs.currentStreak) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatsUiState())

    init {
        viewModelScope.launch { usage.refresh() }
    }

    fun icon(packageName: String): Drawable? = installed.icon(packageName)

    fun setRange(value: StatsRange) {
        range.value = value
    }

    private fun dayPoints(
        rows: List<DailyAppUsage>,
        today: DayUsageSnapshot,
        tracked: Set<String>,
        days: Int,
    ): List<DayPoint> {
        val end = time.today()
        val byDate = rows.groupBy { it.date }
        return (days - 1 downTo 0).map { back ->
            val date = end.minusDays(back.toLong())
            if (date == end) {
                val live = today.perApp.values.filter { it.packageName in tracked }
                DayPoint(date, live.sumOf { it.minutes }, live.sumOf { it.opens })
            } else {
                val dayRows = byDate[date].orEmpty()
                DayPoint(date, dayRows.sumOf { it.minutes }, dayRows.sumOf { it.opens })
            }
        }
    }

    private fun opensByHour(apps: List<AppUsage>): List<Int> {
        val counts = IntArray(StatsUiState.HOURS)
        apps.flatMap { it.openTimestamps }.forEach { ts ->
            counts[time.minuteOfDay(ts) / MINUTES_PER_HOUR] += 1
        }
        return counts.toList()
    }

    private fun comparison(
        rows: List<DailyAppUsage>,
        today: DayUsageSnapshot,
        tracked: Set<String>,
    ): WeekComparison? {
        val end = time.today()
        val thisWeekStart = end.minusDays((WEEK - 1).toLong())
        val lastWeekStart = thisWeekStart.minusDays(WEEK.toLong())
        val thisWeek = rows.filter { !it.date.isBefore(thisWeekStart) && it.date != end }
        val lastWeek = rows.filter { !it.date.isBefore(lastWeekStart) && it.date.isBefore(thisWeekStart) }
        if (thisWeek.isEmpty() && lastWeek.isEmpty()) return null
        val live = today.perApp.values.filter { it.packageName in tracked }
        return WeekComparison(
            minutes = thisWeek.sumOf { it.minutes } + live.sumOf { it.minutes },
            previousMinutes = lastWeek.sumOf { it.minutes },
            opens = thisWeek.sumOf { it.opens } + live.sumOf { it.opens },
            previousOpens = lastWeek.sumOf { it.opens },
        )
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val WEEK = 7
        private const val TWO_WEEKS = 14
        private const val MINUTES_PER_HOUR = 60
        private const val PERCENT = 100
    }
}
