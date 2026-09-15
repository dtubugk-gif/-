package il.rikavon.feature.blocker.engine

/**
 * Decides, on the accessibility event thread, whether a window that just came to the front must be sent
 * home. Pure and allocation-free on the hot path: only a package that is already blocked qualifies, and
 * our own windows (the block screen itself) never do.
 */
class InstantBlockPolicy(private val ownPackage: String) {
    fun shouldSendHome(packageName: CharSequence?, blocked: Set<String>): Boolean {
        val target = packageName?.toString() ?: return false
        return target != ownPackage && target in blocked
    }
}
