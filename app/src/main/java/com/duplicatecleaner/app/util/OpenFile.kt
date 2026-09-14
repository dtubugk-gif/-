package com.duplicatecleaner.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.duplicatecleaner.app.model.FileEntry
import java.io.File

/** Opens the file in whatever app the system picks (gallery, player, viewer). */
fun openExternally(context: Context, entry: FileEntry): Boolean {
    val uri: Uri = try {
        entry.uri?.let(Uri::parse)
            ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entry.path))
    } catch (ignored: IllegalArgumentException) {
        return false
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, entry.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (ignored: ActivityNotFoundException) {
        false
    }
}
