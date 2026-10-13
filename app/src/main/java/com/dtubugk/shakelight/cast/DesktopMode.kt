package com.dtubugk.shakelight.cast

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.Settings
import android.util.Base64
import android.view.Surface
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import com.dtubugk.shakelight.core.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** The desktop side of "Project to computer": landscape lock, app list, opening apps. */
object DesktopMode {

    private const val SAVED_AUTO_ROTATE = "desktop_saved_auto_rotate"
    private const val SAVED_ROTATION = "desktop_saved_rotation"
    private const val WINDOWING_MODE_FREEFORM = 5
    private var windowCount = 0

    fun canRotate(context: Context): Boolean = Settings.System.canWrite(context)

    fun isLandscape(context: Context): Boolean = Prefs.get(context).contains(SAVED_ROTATION)

    /** Locks the phone sideways so apps fill the wide computer screen; restores the user's setting after. */
    fun setLandscape(context: Context, on: Boolean): Boolean {
        if (!canRotate(context)) return false
        val resolver = context.contentResolver
        val prefs = Prefs.get(context)
        return runCatching {
            if (on) {
                if (!prefs.contains(SAVED_ROTATION)) {
                    prefs.edit {
                        putInt(SAVED_AUTO_ROTATE, Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1))
                        putInt(SAVED_ROTATION, Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0))
                    }
                }
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_90)
            } else if (prefs.contains(SAVED_ROTATION)) {
                Settings.System.putInt(resolver, Settings.System.USER_ROTATION, prefs.getInt(SAVED_ROTATION, Surface.ROTATION_0))
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, prefs.getInt(SAVED_AUTO_ROTATE, 1))
                prefs.edit {
                    remove(SAVED_ROTATION)
                    remove(SAVED_AUTO_ROTATE)
                }
            }
            true
        }.getOrDefault(false)
    }

    /** All launchable apps as JSON: [{"p": package, "n": name, "i": base64 PNG icon}]. */
    fun appsJson(context: Context, withComponents: Boolean = false): ByteArray {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(launcher, 0)
        // The Start menu shows icons at 48 px; 96 px stays sharp on high-DPI screens and keeps the
        // list small (full-resolution icons made it 2.5 MB for 130 apps).
        val iconSize = 96
        val apps = JSONArray()
        activities
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
            .forEach { (info, label) ->
                val icon = runCatching {
                    val bitmap = info.loadIcon(pm).toBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                    Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                }.getOrDefault("")
                val app = JSONObject().put("p", info.activityInfo.packageName).put("n", label).put("i", icon)
                if (withComponents) app.put("c", info.activityInfo.packageName + "/" + info.activityInfo.name)
                apps.put(app)
            }
        return apps.toString().toByteArray()
    }

    /**
     * Opens an app. With [window] it asks for a resizable floating window (Samsung pop-up view /
     * Android freeform), which only some phones allow; otherwise it opens full screen.
     */
    fun launch(context: Context, packageName: String, window: Boolean): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = if (window && supportsWindows(context)) windowOptions(context) else null
        // The accessibility service is allowed to open apps while ShakeLight is in the background.
        val starter = ControlService.instance ?: context
        return runCatching { starter.startActivity(intent, options) }.isSuccess ||
            runCatching { starter.startActivity(intent) }.isSuccess
    }

    fun supportsWindows(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    private fun windowOptions(context: Context): android.os.Bundle? = runCatching {
        val metrics = context.resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        // Each new window opens a step down-right of the previous one, like a desktop.
        val step = windowCount++ % 5
        val landscape = w > h
        val winW = (w * if (landscape) 0.55f else 0.86f).toInt()
        val winH = (h * if (landscape) 0.72f else 0.55f).toInt()
        val left = (w * 0.05f + step * w * 0.07f).toInt().coerceAtMost(w - winW)
        val top = (h * 0.06f + step * h * 0.05f).toInt().coerceAtMost(h - winH)
        val bounds = Rect(left, top, left + winW, top + winH)
        val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        // Not public API, but this is how launchers ask for a freeform window.
        runCatching {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        }
        options.toBundle()
    }.getOrNull()
}
