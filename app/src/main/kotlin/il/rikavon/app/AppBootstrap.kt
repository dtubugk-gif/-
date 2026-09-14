package il.rikavon.app

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.di.ApplicationScope
import il.rikavon.core.data.repo.DayRolloverUseCase
import il.rikavon.feature.blocker.engine.BlockerEvent
import il.rikavon.feature.blocker.engine.BlockerEvents
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.blocker.service.MidnightAlarmScheduler
import il.rikavon.feature.blocker.service.ServiceReviverWorker
import il.rikavon.feature.blocker.service.ServiceStarter
import il.rikavon.feature.mascot.registry.MascotRegistry
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.notifications.DailySummaryScheduler
import il.rikavon.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Process-start wiring: locale, rollover catch-up, service, alarms, widget refresh. */
@Singleton
class AppBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rollover: DayRolloverUseCase,
    private val starter: ServiceStarter,
    private val midnight: MidnightAlarmScheduler,
    private val summaryScheduler: DailySummaryScheduler,
    private val registry: MascotRegistry,
    private val selectedMascot: SelectedMascot,
    private val scoreProvider: FocusScoreProvider,
    private val widgetUpdater: WidgetUpdater,
    private val events: BlockerEvents,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            registry.load()
            rollover.runIfDue()
            midnight.schedule()
            summaryScheduler.reschedule()
            ServiceReviverWorker.enqueue(context)
            starter.startIfConfigured()
            widgetUpdater.update()
        }
        scope.launch {
            combine(
                scoreProvider.score.map { it.total }.distinctUntilChanged(),
                selectedMascot.skin.map { it?.id }.distinctUntilChanged(),
            ) { score, mascot -> score to mascot }
                .debounce(WIDGET_DEBOUNCE_MILLIS)
                .collect { widgetUpdater.update() }
        }
        scope.launch {
            events.events.collect { event ->
                when (event) {
                    is BlockerEvent.DayRolledOver -> {
                        widgetUpdater.update()
                        summaryScheduler.reschedule()
                    }
                    BlockerEvent.ServiceStarted -> widgetUpdater.update()
                    else -> Unit
                }
            }
        }
    }

    companion object {
        private const val WIDGET_DEBOUNCE_MILLIS = 1_500L
    }
}
