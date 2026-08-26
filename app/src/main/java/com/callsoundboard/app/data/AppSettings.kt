package com.callsoundboard.app.data

import android.content.Context

/** Small key/value settings store (currently just the auto-bubble toggle). */
object AppSettings {

    private const val PREFS = "settings"
    private const val KEY_AUTO_BUBBLE = "auto_bubble"

    fun isAutoBubbleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_BUBBLE, true)

    fun setAutoBubbleEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_BUBBLE, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
