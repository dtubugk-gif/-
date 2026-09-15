package il.rikavon.feature.blocker.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible activity Android starts during profile provisioning on Android 12 and newer: first to ask which
 * provisioning mode this app supports (a managed profile), then, inside the new profile, to let the app
 * finish its setup before the profile is shown to the user.
 */
class FocusProfileComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            ACTION_GET_PROVISIONING_MODE ->
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PROVISIONING_MODE, PROVISIONING_MODE_MANAGED_PROFILE))
            ACTION_ADMIN_POLICY_COMPLIANCE -> {
                FocusProfileManager.finishProvisioning(this)
                setResult(RESULT_OK)
            }
            else -> setResult(RESULT_CANCELED)
        }
        finish()
    }

    private companion object {
        // DevicePolicyManager constants (API 29); spelled out so the class stays usable on minSdk.
        const val ACTION_GET_PROVISIONING_MODE = "android.app.action.GET_PROVISIONING_MODE"
        const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"
        const val EXTRA_PROVISIONING_MODE = "android.app.extra.PROVISIONING_MODE"
        const val PROVISIONING_MODE_MANAGED_PROFILE = 2
    }
}
