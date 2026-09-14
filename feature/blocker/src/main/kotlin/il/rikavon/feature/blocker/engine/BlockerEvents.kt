package il.rikavon.feature.blocker.engine

import il.rikavon.core.data.model.AchievementId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BlockerEvent {
    data class Blocked(val packageName: String) : BlockerEvent

    data class DayRolledOver(val newlyUnlocked: Set<AchievementId>) : BlockerEvent

    data object ServiceStarted : BlockerEvent

    data object UsageRefreshed : BlockerEvent
}

/** In-process event stream the app shell uses to refresh widgets and notifications. */
@Singleton
class BlockerEvents @Inject constructor() {
    private val _events = MutableSharedFlow<BlockerEvent>(extraBufferCapacity = BUFFER)
    val events: SharedFlow<BlockerEvent> = _events.asSharedFlow()

    fun emit(event: BlockerEvent) {
        _events.tryEmit(event)
    }

    companion object {
        private const val BUFFER = 16
    }
}
