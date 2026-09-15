package il.rikavon.feature.blocker.profile

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The profile owner of the focus profile. It asks for no device policies; owning the profile is what lets
 * this app grey out (suspend) the apps installed inside it when their limit is reached.
 */
class FocusProfileAdminReceiver : DeviceAdminReceiver() {
    /** Runs inside the freshly created profile: name it, enable it, and continue onboarding there. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        FocusProfileManager.finishProvisioning(context)
        context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { launch -> runCatching { context.startActivity(launch) } }
    }

    companion object {
        fun component(context: Context): ComponentName = ComponentName(context, FocusProfileAdminReceiver::class.java)
    }
}
