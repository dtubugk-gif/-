package il.rikavon.core.data.domain

/**
 * Product tiers. The free tier is complete and usable; premium removes the caps.
 * Mascots beyond the free ones are earned through achievements on every tier;
 * premium simply unlocks all of them immediately.
 */
enum class Tier(val maxLimitedApps: Int, val maxSchedules: Int, val allMascotsUnlocked: Boolean) {
    FREE(maxLimitedApps = 3, maxSchedules = 1, allMascotsUnlocked = false),
    PREMIUM(maxLimitedApps = Int.MAX_VALUE, maxSchedules = Int.MAX_VALUE, allMascotsUnlocked = true),
    ;

    fun canAddLimit(currentCount: Int): Boolean = currentCount < maxLimitedApps

    fun canAddSchedule(currentCount: Int): Boolean = currentCount < maxSchedules

    companion object {
        fun of(premium: Boolean): Tier = if (premium) PREMIUM else FREE
    }
}
