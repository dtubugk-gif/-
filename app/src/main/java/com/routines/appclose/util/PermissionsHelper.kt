package com.routines.appclose.util

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.routines.appclose.data.ActionType
import com.routines.appclose.data.RuleAction
import com.routines.appclose.service.AppExitAccessibilityService

/** מצב ההרשאות המיוחדות שהאפליקציה צריכה, וכפתורי קפיצה למסכי המערכת. */
object PermissionsHelper {

    fun isAccessibilityEnabled(context: Context): Boolean {
        // ComponentName.unflattenFromString מטפל גם בצורה המקוצרת
        // (pkg/.service.Foo) שחלק מהיצרנים שומרים בהגדרה.
        val expected = ComponentName(context, AppExitAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
    }

    /** אילו הרשאות חסרות עבור הפעולות של שגרה נתונה — לאזהרה על הכרטיס. */
    fun missingForActions(context: Context, actions: List<RuleAction>): List<String> {
        val missing = linkedSetOf<String>()
        for (action in actions) {
            when (action.type) {
                ActionType.NOTIFY ->
                    if (!hasNotificationPermission(context)) missing.add("התראות")
                ActionType.DND ->
                    if (!hasDndAccess(context)) missing.add("גישת נא לא להפריע")
                ActionType.SOUND_MODE ->
                    if (action.intValue == 0 && !hasDndAccess(context)) missing.add("גישת נא לא להפריע (למצב שקט)")
                ActionType.OPEN_APP ->
                    if (!canDrawOverlays(context)) missing.add("תצוגה מעל אפליקציות")
                ActionType.BRIGHTNESS ->
                    if (!canWriteSettings(context)) missing.add("שינוי הגדרות מערכת")
                else -> {}
            }
        }
        return missing.toList()
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasDndAccess(context: Context): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @Suppress("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun dndAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun writeSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
}
