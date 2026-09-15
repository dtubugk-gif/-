package il.rikavon.feature.blocker.profile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.Build
import android.os.UserHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.feature.blocker.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The focus profile: a work profile this app owns. Android reserves package suspension (the greyed-out icon
 * and the "blocked" dialog that never launches the app, what Family Link does) for profile and device owners,
 * so the way for a normal app to offer it is to own a profile and grey out the apps installed inside it.
 *
 * One object serves both sides: on the personal side it creates the profile and opens the copy of this app
 * inside it; inside the profile it applies the suspensions the enforcement loop asks for.
 */
@Singleton
class FocusProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dpm: DevicePolicyManager
        get() = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin get() = FocusProfileAdminReceiver.component(context)

    /** Packages this process suspended, so they can be restored even after their limit is deleted. */
    private val applied = mutableSetOf<String>()

    /** True inside the focus profile: this copy of the app owns it and may suspend packages. */
    val insideProfile: Boolean
        get() = runCatching { dpm.isProfileOwnerApp(context.packageName) }.getOrDefault(false)

    fun state(): FocusProfileState {
        val inside = insideProfile
        val has = !inside && targetProfile() != null
        val canCreate =
            !inside &&
                !has &&
                runCatching { dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE) }
                    .getOrDefault(false)
        return FocusProfileState(insideProfile = inside, hasProfile = has, canCreate = canCreate)
    }

    /** The system provisioning flow; start it for a result and refresh [state] when it returns. */
    fun provisioningIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)

    /** Opens this app's copy inside the focus profile (the launcher lists it under the briefcase tab). */
    fun openInsideProfile(): Boolean {
        val target = targetProfile() ?: return false
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return false
        return runCatching { crossProfileApps()?.startMainActivity(component, target) }.isSuccess
    }

    /**
     * Greys out the tracked packages in [blocked] and restores the tracked ones that are not; packages this
     * process suspended earlier count as tracked so deleting a limit restores its app. No-op outside the
     * profile.
     */
    fun applySuspension(tracked: Set<String>, blocked: Set<String>) {
        if (!insideProfile) return
        val scope = tracked + applied
        val suspendedNow =
            scope.filterTo(mutableSetOf()) { runCatching { dpm.isPackageSuspended(admin, it) }.getOrDefault(false) }
        val plan = SuspensionPlan.plan(scope, blocked, suspendedNow)
        if (plan.toSuspend.isNotEmpty()) {
            runCatching { dpm.setPackagesSuspended(admin, plan.toSuspend.toTypedArray(), true) }
            applied += plan.toSuspend
        }
        if (plan.toUnsuspend.isNotEmpty()) {
            runCatching { dpm.setPackagesSuspended(admin, plan.toUnsuspend.toTypedArray(), false) }
            applied -= plan.toUnsuspend
        }
    }

    /** Restores every package this process suspended (tracking switched off, service stopping). */
    fun releaseAll() {
        if (!insideProfile || applied.isEmpty()) return
        runCatching { dpm.setPackagesSuspended(admin, applied.toTypedArray(), false) }
        applied.clear()
    }

    private fun crossProfileApps(): CrossProfileApps? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE) as? CrossProfileApps
        } else {
            null
        }

    private fun targetProfile(): UserHandle? =
        runCatching { crossProfileApps()?.targetUserProfiles?.firstOrNull() }.getOrNull()

    companion object {
        /** Runs inside the new profile when Android hands it over; safe to call more than once. */
        fun finishProvisioning(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = FocusProfileAdminReceiver.component(context)
            runCatching { dpm.setProfileName(admin, context.getString(R.string.profile_name)) }
            runCatching { dpm.setShortSupportMessage(admin, context.getString(R.string.profile_support_message)) }
            runCatching { dpm.setProfileEnabled(admin) }
        }
    }
}
