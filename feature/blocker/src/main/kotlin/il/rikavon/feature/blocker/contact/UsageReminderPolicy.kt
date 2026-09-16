package il.rikavon.feature.blocker.contact

import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.DayUsageSnapshot

/**
 * The nudge inside an app: every [intervalMinutes] of one sitting in a limited app, one message from the pet.
 * A sitting starts at the app's last counted open; leaving and coming back starts a new one. Stateful only
 * to avoid repeating a tick.
 */
class UsageReminderPolicy {
    private val lastTick = mutableMapOf<String, Pair<Long, Int>>()

    fun evaluate(
        snapshot: DayUsageSnapshot,
        limits: List<AppLimit>,
        intervalMinutes: Int,
        nowMillis: Long,
    ): PetContact.Nudge? {
        val pkg = snapshot.foregroundPackage
        val tracked = pkg != null && limits.any { it.enabled && it.packageName == pkg }
        val start = pkg?.let { snapshot.usageOf(it).openTimestamps.maxOrNull() }
        if (intervalMinutes <= 0 || pkg == null || !tracked || start == null) return null
        val minutes = ((nowMillis - start) / MILLIS_PER_MINUTE).toInt()
        val tick = minutes / intervalMinutes
        val previous = lastTick[pkg]
        val due = tick > 0 && (previous == null || previous.first != start || previous.second < tick)
        if (!due) return null
        lastTick[pkg] = start to tick
        return PetContact.Nudge(pkg, minutes = tick * intervalMinutes)
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
