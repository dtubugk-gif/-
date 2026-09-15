package il.rikavon.feature.blocker.engine

/**
 * Adaptive polling. Cheap when nothing is at risk, fast when it matters, off when the screen is off.
 *
 *  - screen off: no polling at all
 *  - some app is already blockable and the accessibility service is off (polling is the only thing
 *    standing between the user and that app): 1s
 *  - the app on screen is tracked, or some app is already blockable, or usage >= 95% of a limit: 2s
 *  - usage >= 80% of a limit: 5s
 *  - otherwise: 15s
 */
class PollingPolicy {
    fun intervalMillis(
        screenOn: Boolean,
        foregroundTracked: Boolean,
        anyBlockable: Boolean,
        maxUsageRatio: Float,
        instantPath: Boolean = false,
    ): Long? =
        when {
            !screenOn -> null
            anyBlockable && !instantPath -> INSTANT_MILLIS
            foregroundTracked || anyBlockable || maxUsageRatio >= CRITICAL_RATIO -> FAST_MILLIS
            maxUsageRatio >= WARNING_RATIO -> MEDIUM_MILLIS
            else -> NORMAL_MILLIS
        }

    companion object {
        const val NORMAL_MILLIS = 15_000L
        const val MEDIUM_MILLIS = 5_000L
        const val FAST_MILLIS = 2_000L
        const val INSTANT_MILLIS = 1_000L
        const val WARNING_RATIO = 0.8f
        const val CRITICAL_RATIO = 0.95f
    }
}
