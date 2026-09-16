package il.rikavon.feature.blocker.engine

/**
 * Spots "this app just opened" on the polling path: the foreground moved to a begged-about package from
 * somewhere else. This app's own screens (the call, the block screen) are transparent, so coming back from
 * the pet's call is not a new open, and a gap with no foreground (an activity switch, the screen off) is not
 * a departure. The first observation only primes it, so a service restart never rings on its own.
 */
class OpenDetector(private val self: String) {
    private var primed = false
    private var front: String? = null

    /** The package that just opened, if the app in front is one of [pleading] and was not in front before. */
    fun opened(foreground: String?, pleading: Set<String>): String? {
        val previous = front
        if (foreground != null && foreground != self) front = foreground
        val wasPrimed = primed
        primed = true
        if (!wasPrimed || foreground == null || foreground == self || foreground == previous) return null
        return foreground.takeIf { it in pleading }
    }
}
