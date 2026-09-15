package il.rikavon.feature.blocker.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the polling service and the accessibility service share: the set of packages that are blocked right
 * now (published after every poll, read on the accessibility event thread without any IO) and requests to
 * raise the block screen for a package the accessibility service has just sent home.
 */
@Singleton
class InstantBlockBus @Inject constructor() {
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    private val _requests = MutableSharedFlow<String>(extraBufferCapacity = BUFFER)
    val requests: SharedFlow<String> = _requests.asSharedFlow()

    fun publish(blocked: Set<String>) {
        _blocked.value = blocked
    }

    fun request(packageName: String) {
        _requests.tryEmit(packageName)
    }

    private companion object {
        const val BUFFER = 8
    }
}
