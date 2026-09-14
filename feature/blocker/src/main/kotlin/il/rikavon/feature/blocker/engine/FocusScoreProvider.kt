package il.rikavon.feature.blocker.engine

import il.rikavon.core.data.di.ApplicationScope
import il.rikavon.core.data.domain.FocusScore
import il.rikavon.core.data.domain.FocusScoreCalculator
import il.rikavon.core.data.domain.ScoredApp
import il.rikavon.core.data.repo.DayStateRepository
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.ui.gallery.CurrentStageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Live focus score and mascot stage, derived from today's usage and the configured limits. */
@Singleton
class FocusScoreProvider @Inject constructor(
    usage: UsageRepository,
    limits: LimitsRepository,
    private val dayState: DayStateRepository,
    private val time: TimeSource,
    @ApplicationScope private val scope: CoroutineScope,
) : CurrentStageSource {
    private val calculator = FocusScoreCalculator()

    val score: StateFlow<FocusScore> =
        combine(usage.today, limits.limits) { snapshot, limitList ->
            calculator.calculate(
                apps = limitList.map { ScoredApp(it, snapshot.usageOf(it.packageName)) },
                dayStartMillis = time.dayStartMillis(snapshot.date),
                nowMillis = snapshot.computedAt,
            )
        }.stateIn(scope, SharingStarted.Eagerly, FocusScore.PERFECT)

    override val stage: Flow<MascotStage> = score.map { MascotStage.fromScore(it.total) }.distinctUntilChanged()

    init {
        scope.launch {
            score.map { it.total }.distinctUntilChanged().collect { dayState.observeScore(it) }
        }
    }

    fun currentStage(): MascotStage = MascotStage.fromScore(score.value.total)
}
