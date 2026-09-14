package il.rikavon.core.data.domain

import il.rikavon.core.data.model.AppUsage

enum class UsageEventKind { RESUMED, PAUSED, SCREEN_OFF, OTHER }

data class UsageEvent(val timestamp: Long, val packageName: String, val kind: UsageEventKind)

/**
 * Turns a raw stream of foreground events into per-app minutes and open counts.
 *
 * Opens are counted on [UsageEventKind.RESUMED] with de-duplication: a resume that happens within
 * [DEDUPE_WINDOW_MILLIS] of the same package's previous resume or pause is a "return", not a new open.
 * In-app activity switches (resume while the package is already in the foreground) never count.
 *
 * The aggregator is incremental: feed events in timestamp order across multiple calls and read a
 * consistent snapshot at any time with [snapshot].
 */
class UsageSessionAggregator {
    private class AppState(
        var foregroundMillis: Long = 0,
        var opens: Int = 0,
        val openTimestamps: MutableList<Long> = mutableListOf(),
        var lastResume: Long = NEVER,
        var lastPause: Long = NEVER,
    )

    private val apps = HashMap<String, AppState>()
    private var current: String? = null
    private var currentSince: Long = 0
    private var lastEventTimestamp: Long = 0
    var screenOn: Boolean = true
        private set

    val foregroundPackage: String? get() = current

    val lastTimestamp: Long get() = lastEventTimestamp

    fun feed(events: List<UsageEvent>) {
        events.forEach(::feed)
    }

    fun feed(event: UsageEvent) {
        lastEventTimestamp = maxOf(lastEventTimestamp, event.timestamp)
        when (event.kind) {
            UsageEventKind.RESUMED -> onResumed(event)
            UsageEventKind.PAUSED -> onPaused(event)
            UsageEventKind.SCREEN_OFF -> onScreenOff(event.timestamp)
            UsageEventKind.OTHER -> Unit
        }
    }

    fun markScreenOn() {
        screenOn = true
    }

    private fun onResumed(event: UsageEvent) {
        screenOn = true
        val state = apps.getOrPut(event.packageName) { AppState() }
        val wasForeground = current == event.packageName
        if (!wasForeground) {
            closeCurrentSession(event.timestamp)
            current = event.packageName
            currentSince = event.timestamp
            val isReturn = within(state.lastResume, event.timestamp) || within(state.lastPause, event.timestamp)
            if (!isReturn) {
                state.opens += 1
                state.openTimestamps += event.timestamp
            }
        }
        state.lastResume = event.timestamp
    }

    private fun onPaused(event: UsageEvent) {
        val state = apps.getOrPut(event.packageName) { AppState() }
        state.lastPause = event.timestamp
        if (current == event.packageName) {
            closeCurrentSession(event.timestamp)
            current = null
        }
    }

    private fun onScreenOff(timestamp: Long) {
        closeCurrentSession(timestamp)
        current = null
        screenOn = false
    }

    /** True when [previous] is a real timestamp less than the dedupe window before [now]. */
    private fun within(previous: Long, now: Long): Boolean =
        previous != NEVER && now - previous < DEDUPE_WINDOW_MILLIS

    private fun closeCurrentSession(endTimestamp: Long) {
        val pkg = current ?: return
        val state = apps.getOrPut(pkg) { AppState() }
        state.foregroundMillis += (endTimestamp - currentSince).coerceAtLeast(0)
        currentSince = endTimestamp
    }

    /** Per-app usage as of [nowMillis]; an open foreground session is counted up to now. */
    fun snapshot(nowMillis: Long): Map<String, AppUsage> {
        val liveExtra = current?.let { it to (nowMillis - currentSince).coerceAtLeast(0) }
        return apps.mapValues { (pkg, state) ->
            val extra = if (liveExtra?.first == pkg) liveExtra.second else 0L
            AppUsage(
                packageName = pkg,
                minutes = ((state.foregroundMillis + extra) / MILLIS_PER_MINUTE).toInt(),
                opens = state.opens,
                openTimestamps = state.openTimestamps.toList(),
            )
        }
    }

    fun reset() {
        apps.clear()
        current = null
        currentSince = 0
        lastEventTimestamp = 0
        screenOn = true
    }

    companion object {
        const val DEDUPE_WINDOW_MILLIS = 2_000L
        private const val NEVER = Long.MIN_VALUE
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
