package com.dtubugk.shakelight.cast

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlin.concurrent.thread

/**
 * Gives ShakeLight Receiver the phone's app list (names + icons) for the Computer-mode Start menu:
 * `adb shell content read --uri content://com.dtubugk.shakelight.desktop/apps`.
 * Only the debugging shell can read it (android.permission.DUMP in the manifest).
 */
class DesktopAppsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (uri.lastPathSegment != "apps") return null
        val ctx = context ?: return null
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread(name = "desktop-apps") {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                runCatching { out.write(DesktopMode.appsJson(ctx, withComponents = true)) }
            }
        }
        return read
    }

    override fun getType(uri: Uri): String = "application/json"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
