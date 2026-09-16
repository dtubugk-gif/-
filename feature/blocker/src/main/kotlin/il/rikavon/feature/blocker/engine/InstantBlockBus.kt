package il.rikavon.feature.blocker.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the accessibility service asks the enforcement service to do for a package it just saw open. */
sealed interface InstantRequest {
    val packageName: String

    /** The package is blocked; it was already sent home, raise the block screen. */
    data class Block(override val packageName: String) : InstantRequest

    /** The package's limit just changed; hold it behind the breathing pause. */
    data class Gate(override val packageName: String) : InstantRequest
}

/**
 * What the polling service and the accessibility service share: the packages that are blocked right now
 * and the ones waiting for a breathing pause (both published after every poll and read on the accessibility
 * event thread without any IO), plus requests going the other way.
 */
@Singleton
class InstantBlockBus @Inject constructor() {
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    private val _gated = MutableStateFlow<Set<String>>(emptySet())
    val gated: StateFlow<Set<String>> = _gated.asStateFlow()

    private val _requests = MutableSharedFlow<InstantRequest>(extraBufferCapacity = BUFFER)
    val requests: SharedFlow<InstantRequest> = _requests.asSharedFlow()

    fun publish(blocked: Set<String>, gated: Set<String> = emptySet()) {
        _blocked.value = blocked
        _gated.value = gated
    }

    fun request(request: InstantRequest) {
        _requests.tryEmit(request)
    }

    private companion object {
        const val BUFFER = 8
    }
}
