package il.rikavon.feature.blocker.contact

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.permissions.PermissionState
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Why the pet is not calling, or that it can, in the order a person should fix things. */
sealed interface CallDiagnosis {
    data object TrackingOff : CallDiagnosis

    data object NoUsageAccess : CallDiagnosis

    data object CallsOff : CallDiagnosis

    /** The tracking service has not run its loop lately; [lastPollAt] is zero when it never did. */
    data class ServiceAsleep(val lastPollAt: Long) : CallDiagnosis

    /** No app has "calls on every open", so the pet only calls at 95 % and on a block. */
    data object NothingToCallAbout : CallDiagnosis

    /** Without the overlay permission the call can only be a notification, and [notifications] says if even that. */
    data class NoOverlay(val notifications: Boolean) : CallDiagnosis

    /** Nothing is missing; [ringLabel] and [openLabel] name the apps in [health], when there are any. */
    data class Ready(
        val health: CallHealthState,
        val ringLabel: String? = null,
        val openLabel: String? = null,
    ) : CallDiagnosis
}

/**
 * Turns "the calls do not work" into one sentence: which precondition is missing, in the order to fix them,
 * or, when nothing is, what the call machinery last did. Also rings a test call through the exact path a
 * real one takes, so the user can hear whether the phone lets the pet through.
 */
@Singleton
class CallDoctor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val limits: LimitsRepository,
    private val installed: InstalledAppsSource,
    private val health: CallHealth,
    private val notifier: PetContactNotifier,
    private val selectedMascot: SelectedMascot,
    private val score: FocusScoreProvider,
    private val time: TimeSource,
) {
    /** The diagnosis, live; [permissionState] is re-read by the screen when it resumes. */
    fun diagnosis(permissionState: Flow<PermissionState>): Flow<CallDiagnosis> =
        combine(settings.settings, limits.limits, health.state, permissionState) { prefs, limitList, state, perms ->
            diagnose(prefs, limitList, state, perms, time.nowMillis()).labelled()
        }

    private fun CallDiagnosis.labelled(): CallDiagnosis =
        if (this is CallDiagnosis.Ready) {
            copy(
                ringLabel = health.lastRing?.let { installed.label(it.packageName) },
                openLabel = health.lastOpen?.let { installed.label(it.packageName) },
            )
        } else {
            this
        }

    /** Rings now, about this app, with the selected pet: the same call a real open would make. */
    suspend fun testCall() {
        val skin = selectedMascot.current() ?: return
        val prefs = settings.current()
        val language = UiLanguage.fromLocale(context.resources.configuration.locales)
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        val call = PetContact.Call(context.packageName, CallReason.PLEAD, minutesLeft = 0)
        notifier.call(skin, score.currentStage(), call, label, language, prefs.reduceMotion)
    }

    companion object {
        /** The loop polls at most every 15 s while the screen is on; longer than this and it is not running. */
        const val ASLEEP_AFTER_MILLIS = 90_000L

        fun diagnose(
            prefs: Settings,
            limitList: List<AppLimit>,
            state: CallHealthState,
            perms: PermissionState,
            nowMillis: Long,
        ): CallDiagnosis =
            when {
                !prefs.trackingEnabled -> CallDiagnosis.TrackingOff
                !perms.usageAccess -> CallDiagnosis.NoUsageAccess
                !prefs.petCallsEnabled -> CallDiagnosis.CallsOff
                nowMillis - state.lastPollAt > ASLEEP_AFTER_MILLIS -> CallDiagnosis.ServiceAsleep(state.lastPollAt)
                limitList.none { it.enabled && it.callOnOpen } -> CallDiagnosis.NothingToCallAbout
                !perms.overlay -> CallDiagnosis.NoOverlay(perms.notifications)
                else -> CallDiagnosis.Ready(state)
            }
    }
}
