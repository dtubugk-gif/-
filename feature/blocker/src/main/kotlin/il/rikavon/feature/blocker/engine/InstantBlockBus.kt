package il.rikavon.feature.blocker.engine

import il.rikavon.core.data.model.FeedFeature
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the accessibility service asks the enforcement service to do for something it just saw. */
sealed interface InstantRequest {
    val packageName: String

    /** The package is blocked; it was already sent home, raise the block screen. */
    data class Block(override val packageName: String) : InstantRequest

    /** The package's limit just changed; hold it behind the breathing pause. */
    data class Gate(override val packageName: String) : InstantRequest

    /** The browser showed a blocked site; it was already backed out of, say why. */
    data class Site(override val packageName: String, val domain: String) : InstantRequest

    /** The app showed a blocked feed (Shorts, Reels); it was already backed out of, say why. */
    data class Feed(override val packageName: String, val feature: FeedFeature) : InstantRequest

    /** An app the pet begs about just came to the front; ring. */
    data class Plead(override val packageName: String) : InstantRequest
}

/**
 * What the polling service and the accessibility service share: the packages that are blocked right now,
 * the ones waiting for a breathing pause, the blocked websites and feeds (all published after every poll and
 * read on the accessibility event thread without any IO), plus requests going the other way.
 */
@Singleton
class InstantBlockBus @Inject constructor() {
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    private val _gated = MutableStateFlow<Set<String>>(emptySet())
    val gated: StateFlow<Set<String>> = _gated.asStateFlow()

    private val _sites = MutableStateFlow<Set<String>>(emptySet())
    val sites: StateFlow<Set<String>> = _sites.asStateFlow()

    private val _feeds = MutableStateFlow<Set<FeedFeature>>(emptySet())
    val feeds: StateFlow<Set<FeedFeature>> = _feeds.asStateFlow()

    private val _pleading = MutableStateFlow<Set<String>>(emptySet())

    /** Apps the pet begs about the moment they open ("calls on every open"), minus the ones blocked anyway. */
    val pleading: StateFlow<Set<String>> = _pleading.asStateFlow()

    private val _requests = MutableSharedFlow<InstantRequest>(extraBufferCapacity = BUFFER)
    val requests: SharedFlow<InstantRequest> = _requests.asSharedFlow()

    fun publish(
        blocked: Set<String>,
        gated: Set<String> = emptySet(),
        sites: Set<String> = emptySet(),
        feeds: Set<FeedFeature> = emptySet(),
        pleading: Set<String> = emptySet(),
    ) {
        _blocked.value = blocked
        _gated.value = gated
        _sites.value = sites
        _feeds.value = feeds
        _pleading.value = pleading
    }

    fun request(request: InstantRequest) {
        _requests.tryEmit(request)
    }

    private companion object {
        const val BUFFER = 8
    }
}
