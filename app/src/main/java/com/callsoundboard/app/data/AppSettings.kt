package com.callsoundboard.app.data

import android.content.Context

/** Small key/value settings store (currently just the auto-bubble toggle). */
object AppSettings {

    private const val PREFS = "settings"
    private const val KEY_AUTO_BUBBLE = "auto_bubble"
    private const val KEY_DOWNLOADS_TREE = "downloads_tree_uri"

    fun isAutoBubbleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_BUBBLE, true)

    fun setAutoBubbleEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_BUBBLE, enabled).apply()
    }

    /** Persisted SAF tree URI of the folder the user granted (usually Downloads). */
    fun getDownloadsTreeUri(context: Context): String? =
        prefs(context).getString(KEY_DOWNLOADS_TREE, null)

    fun setDownloadsTreeUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_DOWNLOADS_TREE, uri).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
