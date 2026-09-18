package il.rikavon.feature.blocker.contact

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** How the last ring reached the user, or failed to. */
enum class RingPath {
    /** The call took the screen (the overlay permission is granted and the phone was unlocked). */
    OVERLAY,

    /** Only a notification could be posted; the phone decides whether it pops up. */
    NOTIFICATION,

    /** Neither the screen nor the shade was available, so the ring had no way to show. */
    NOTHING,
}

data class Ring(val packageName: String, val atMillis: Long, val path: RingPath)

data class Open(val packageName: String, val atMillis: Long)

/** What the call machinery last did, so Settings can say why the pet is or is not calling. */
data class CallHealthState(
    /** The tracking loop's last pass; zero until it ran once. */
    val lastPollAt: Long = 0L,
    /** The app usage stats last reported in front. */
    val lastForeground: String? = null,
    /** The last "it just opened" that was noticed for an app the pet begs about. */
    val lastOpen: Open? = null,
    /** The last ring, and how it went out. */
    val lastRing: Ring? = null,
)

/** The live record behind [CallHealthState]; the service and the notifier write, Settings reads. */
@Singleton
class CallHealth @Inject constructor() {
    private val _state = MutableStateFlow(CallHealthState())
    val state: StateFlow<CallHealthState> = _state.asStateFlow()

    fun polled(atMillis: Long, foreground: String?) =
        _state.update { it.copy(lastPollAt = atMillis, lastForeground = foreground) }

    fun opened(packageName: String, atMillis: Long) = _state.update { it.copy(lastOpen = Open(packageName, atMillis)) }

    fun rang(packageName: String, atMillis: Long, path: RingPath) =
        _state.update { it.copy(lastRing = Ring(packageName, atMillis, path)) }
}
