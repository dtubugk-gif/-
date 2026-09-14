package il.rikavon.core.data.repo

import il.rikavon.core.data.domain.AchievementEvaluator
import il.rikavon.core.data.domain.DailyResetPolicy
import il.rikavon.core.data.domain.FocusScoreCalculator
import il.rikavon.core.data.domain.ScoredApp
import il.rikavon.core.data.domain.StreakCalculator
import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.model.DailySummary
import il.rikavon.core.data.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finalises finished days: summary, streak, achievements, retention pruning.
 * Runs on process start, on the midnight alarm and on every service tick; the date comparison makes it
 * idempotent, so a phone that was off at midnight catches up on the next launch.
 */
@Singleton
class DayRolloverUseCase @Inject constructor(
    private val usage: UsageRepository,
    private val limits: LimitsRepository,
    private val summaries: DailySummaryRepository,
    private val achievements: AchievementRepository,
    private val settings: SettingsRepository,
    private val dayState: DayStateRepository,
    private val time: TimeSource,
) {
    private val policy = DailyResetPolicy()
    private val scoreCalculator = FocusScoreCalculator()
    private val streakCalculator = StreakCalculator()
    private val evaluator = AchievementEvaluator()
    private val mutex = Mutex()

    data class Result(val rolledOver: Boolean, val newlyUnlocked: Set<AchievementId>)

    suspend fun runIfDue(): Result =
        mutex.withLock {
            val today = time.today()
            val current = settings.current()
            val pending = policy.pendingRolloverDates(current.lastRolloverDate, today)
            if (current.lastRolloverDate == null) {
                settings.setLastRolloverDate(today)
                return Result(rolledOver = false, newlyUnlocked = emptySet())
            }
            if (pending.isEmpty()) return Result(rolledOver = false, newlyUnlocked = emptySet())

            pending.forEach { finalizeDay(it) }
            settings.setLastRolloverDate(today)
            val streak = streakCalculator.compute(summaries.all())
            settings.setStreak(streak.current, streak.best)
            val newly = evaluateAchievements()
            usage.pruneOlderThan(UsageRepository.RETENTION_DAYS)
            summaries.pruneOlderThan(UsageRepository.RETENTION_DAYS)
            Result(rolledOver = true, newlyUnlocked = newly)
        }

    /** Re-evaluates achievements against everything persisted so far. */
    suspend fun evaluateAchievements(): Set<AchievementId> {
        val current = settings.current()
        val earned =
            evaluator.earned(
                AchievementEvaluator.Input(
                    summaries = summaries.all(),
                    currentStreak = current.currentStreak,
                    hasAnyLimit = limits.all().isNotEmpty(),
                ),
            )
        return achievements.unlock(earned)
    }

    private suspend fun finalizeDay(date: LocalDate) {
        if (summaries.byDate(date) != null) return
        val fromSystem = usage.computeDay(date)
        val usageMap = if (fromSystem.isNotEmpty()) fromSystem else usage.dayUsageFromHistory(date)
        if (fromSystem.isNotEmpty()) usage.persistDay(date, fromSystem)

        val limitList = limits.all().filter { it.enabled }
        val scored = limitList.map { ScoredApp(it, usageMap[it.packageName] ?: AppUsage.empty(it.packageName)) }
        val dayStart = time.dayStartMillis(date)
        val dayEnd = time.dayEndMillis(date)
        val scoreInput =
            if (fromSystem.isEmpty()) {
                scored.map {
                    it.withEstimatedTimestamps(dayStart, dayEnd)
                }
            } else {
                scored
            }
        val score = scoreCalculator.calculate(scoreInput, dayStart, dayEnd)

        val trackedUsages = scored.map { it.usage }
        val allUnderLimit =
            scored.all { app ->
                if (app.limit.fullBlock) app.usage.minutes == 0 else app.usage.minutes < app.limit.limitMinutes
            }
        val openMinutes = trackedUsages.flatMap { it.openTimestamps }.map { time.minuteOfDay(it) }
        val state = dayState.stateFor(date)
        summaries.save(
            DailySummary(
                date = date,
                score = score.total,
                allUnderLimit = allUnderLimit && limitList.isNotEmpty(),
                totalMinutes = trackedUsages.sumOf { it.minutes },
                totalOpens = trackedUsages.sumOf { it.opens },
                firstOpenMinute = openMinutes.minOrNull(),
                nightOpens = openMinutes.count { it >= NIGHT_START_MINUTE || it < NIGHT_END_MINUTE },
                blocksTriggered = summaries.blockCount(date),
                minScore = state?.minScore ?: score.total,
                recoveredFromLow = state?.recoveredFromLow ?: false,
                schedulesKept = state?.schedulesKept ?: 0,
            ),
        )
    }

    /** When the system no longer holds the events, spread the opens evenly across the day. */
    private fun ScoredApp.withEstimatedTimestamps(dayStart: Long, dayEnd: Long): ScoredApp {
        if (usage.opens == 0 || usage.openTimestamps.isNotEmpty()) return this
        val step = (dayEnd - dayStart) / (usage.opens + 1)
        val stamps = (1..usage.opens).map { dayStart + step * it }
        return copy(usage = usage.copy(openTimestamps = stamps))
    }

    companion object {
        private const val NIGHT_START_MINUTE = 23 * 60
        private const val NIGHT_END_MINUTE = 6 * 60
    }
}
