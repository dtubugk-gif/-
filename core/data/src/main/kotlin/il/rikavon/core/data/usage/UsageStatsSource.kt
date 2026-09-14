package il.rikavon.core.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.domain.UsageEvent
import il.rikavon.core.data.domain.UsageEventKind
import javax.inject.Inject
import javax.inject.Singleton

/** Thin wrapper over [UsageStatsManager] that yields normalised [UsageEvent]s. */
@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: UsageStatsManager
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Events in [from, to), in timestamp order. Returns an empty list without the permission. */
    fun events(from: Long, to: Long): List<UsageEvent> {
        if (to <= from) return emptyList()
        val result = ArrayList<UsageEvent>()
        val query = runCatching { manager.queryEvents(from, to) }.getOrNull() ?: return result
        val event = UsageEvents.Event()
        while (query.hasNextEvent()) {
            query.getNextEvent(event)
            val kind = event.kind() ?: continue
            result += UsageEvent(event.timeStamp, event.packageName ?: continue, kind)
        }
        return result
    }

    private fun UsageEvents.Event.kind(): UsageEventKind? =
        when (eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUMED
            UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventKind.PAUSED
            UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventKind.SCREEN_OFF
            else ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    eventType == UsageEvents.Event.ACTIVITY_STOPPED
                ) {
                    UsageEventKind.PAUSED
                } else {
                    null
                }
        }
}
