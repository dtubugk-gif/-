package il.rikavon.core.data.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AppPermission { USAGE_ACCESS, OVERLAY, ACCESSIBILITY, NOTIFICATIONS, EXACT_ALARM, BATTERY_OPTIMIZATION }

data class PermissionState(
    val usageAccess: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val ignoresBatteryOptimization: Boolean,
    /** The optional accessibility service that closes a blocked app the instant it opens. */
    val accessibility: Boolean = false,
    /** Android 14+: whether the pet's calls may take over the screen like a real incoming call. */
    val fullScreenIntent: Boolean = true,
) {
    /** Tracking and blocking need these two; everything else degrades gracefully. */
    val coreGranted: Boolean get() = usageAccess && overlay

    fun granted(permission: AppPermission): Boolean =
        when (permission) {
            AppPermission.USAGE_ACCESS -> usageAccess
            AppPermission.OVERLAY -> overlay
            AppPermission.ACCESSIBILITY -> accessibility
            AppPermission.NOTIFICATIONS -> notifications
            AppPermission.EXACT_ALARM -> exactAlarm
            AppPermission.BATTERY_OPTIMIZATION -> ignoresBatteryOptimization
        }
}

@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun state(): PermissionState =
        PermissionState(
            usageAccess = hasUsageAccess(),
            overlay = Settings.canDrawOverlays(context),
            notifications = hasNotifications(),
            exactAlarm = canScheduleExactAlarms(),
            ignoresBatteryOptimization = ignoresBatteryOptimizations(),
            accessibility = hasAccessibility(),
            fullScreenIntent = canUseFullScreenIntent(),
        )

    /** Full-screen intents need a user grant on Android 14+; older versions allow them with the permission. */
    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return manager.canUseFullScreenIntent()
    }

    /** True when the user enabled this app's accessibility service in system settings. */
    fun hasAccessibility(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    fun hasNotifications(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun ignoresBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
