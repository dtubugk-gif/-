package il.rikavon.feature.blocker.ui.limits

import il.rikavon.core.data.model.AppLimit

/** Whether a change to a limit gives the user more room: the direction the settings lock guards. */
object LimitLoosening {
    fun isLooser(before: AppLimit?, after: AppLimit): Boolean {
        if (before == null) return false
        return (!after.enabled && before.enabled) ||
            (!after.fullBlock && before.fullBlock) ||
            (!after.callOnOpen && before.callOnOpen) ||
            (!after.fullBlock && after.limitMinutes > before.limitMinutes) ||
            raised(before.maxOpens, after.maxOpens) ||
            raised(before.sessionMinutes, after.sessionMinutes)
    }

    /** A cap that was on and is now off or higher. */
    private fun raised(before: Int, after: Int): Boolean = before > 0 && (after == 0 || after > before)
}
