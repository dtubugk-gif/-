package com.dtubugk.shakelight.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Keys shared by the settings screen (res/xml/preferences.xml) and the code. */
object Prefs {
    const val SHAKE_ENABLED = "shake_enabled"
    const val SENSITIVITY = "sensitivity"
    const val SHAKE_COUNT = "shake_count"
    const val VIBRATE = "vibrate"
    const val POCKET_GUARD = "pocket_guard"
    const val SCREEN_OFF = "work_screen_off"
    const val AUTO_OFF = "auto_off_minutes"
    const val START_ON_BOOT = "start_on_boot"
    const val TORCH_LEVEL = "torch_level"

    const val PROJ_ROTATE = "proj_rotate"
    const val PROJ_MIRROR = "proj_mirror"
    const val PROJ_BRIGHT = "proj_bright"
    const val PROJ_LOOP = "proj_loop"

    const val CAST_SOUND = "cast_sound"
    const val CAST_LAST_IP = "cast_last_ip"
    const val CAST_CONTROL = "cast_control"

    /** Keys that change how shake detection behaves while the service runs. */
    val SHAKE_TUNING = setOf(SENSITIVITY, SHAKE_COUNT, POCKET_GUARD, SCREEN_OFF)

    fun get(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun torchLevel(context: Context): Int = get(context).getInt(TORCH_LEVEL, 100)

    fun autoOffMinutes(context: Context): Int =
        get(context).getString(AUTO_OFF, "0")?.toIntOrNull() ?: 0
}
