package il.rikavon.feature.blocker.ui.common

import il.rikavon.core.ui.anim.AnimationSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Strict mode's change-your-mind delay: [start] counts down out loud and then runs the action unless
 * [cancel] came first. One at a time; a new start replaces the pending action.
 */
class StrictCountdown(
    private val scope: CoroutineScope,
    private val seconds: Int = AnimationSpecs.STRICT_MODE_DELAY_SECONDS,
) {
    private val _remaining = MutableStateFlow<Int?>(null)

    /** Seconds left, or null when nothing is pending. */
    val remaining: StateFlow<Int?> = _remaining.asStateFlow()
    private var pending: (suspend () -> Unit)? = null

    fun start(action: suspend () -> Unit) {
        pending = action
        scope.launch {
            for (left in seconds downTo 1) {
                if (pending !== action) return@launch
                _remaining.value = left
                delay(ONE_SECOND_MILLIS)
            }
            if (pending !== action) return@launch
            _remaining.value = null
            pending = null
            action()
        }
    }

    fun cancel() {
        pending = null
        _remaining.value = null
    }

    private companion object {
        const val ONE_SECOND_MILLIS = 1_000L
    }
}
