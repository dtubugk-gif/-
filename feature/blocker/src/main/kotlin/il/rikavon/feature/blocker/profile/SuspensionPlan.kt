package il.rikavon.feature.blocker.profile

/**
 * Which packages to grey out and which to restore, given what the user tracks, what is blocked right now and
 * what the system reports as suspended. Only tracked packages are ever touched.
 */
object SuspensionPlan {
    data class Plan(val toSuspend: Set<String>, val toUnsuspend: Set<String>)

    fun plan(tracked: Set<String>, blocked: Set<String>, suspendedNow: Set<String>): Plan {
        val wanted = tracked intersect blocked
        return Plan(
            toSuspend = wanted - suspendedNow,
            toUnsuspend = (suspendedNow intersect tracked) - wanted,
        )
    }
}

/** What the settings screen needs to know about the focus profile from either side of it. */
data class FocusProfileState(
    /** This copy of the app runs inside the focus profile and owns it. */
    val insideProfile: Boolean = false,
    /** Personal side: a focus profile with a copy of this app exists. */
    val hasProfile: Boolean = false,
    /** Personal side: the device lets this app create one. */
    val canCreate: Boolean = false,
)
