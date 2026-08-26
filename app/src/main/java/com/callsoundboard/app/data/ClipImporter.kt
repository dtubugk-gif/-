package com.callsoundboard.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.callsoundboard.app.model.SoundClip
import java.io.File
import java.util.UUID

/**
 * Copies a shared/opened audio URI into app-private storage and returns a
 * persistent [SoundClip] pointing at the local copy.
 *
 * Why copy? A URI received via ACTION_SEND carries only a temporary read grant
 * for that task — it can't be persisted like a SAF OpenDocument URI. Copying the
 * bytes into filesDir guarantees the clip stays playable later.
 */
object ClipImporter {

    private const val DIR = "clips"

    fun import(context: Context, uri: Uri): SoundClip? {
        return try {
            val name = displayName(context, uri)
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val dest = uniqueFile(dir, sanitize(name))
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            SoundClip(UUID.randomUUID().toString(), name, Uri.fromFile(dest).toString())
        } catch (_: Exception) {
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        } catch (_: Exception) {
        }
        if (name.isNullOrBlank()) name = uri.lastPathSegment?.substringAfterLast('/')
        return name?.takeIf { it.isNotBlank() } ?: "clip_${System.currentTimeMillis()}"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._\\-\\u0590-\\u05FF ]"), "_").ifBlank { "clip" }

    private fun uniqueFile(dir: File, base: String): File {
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var f = File(dir, base)
        var i = 1
        while (f.exists()) {
            f = File(dir, "$stem-$i$ext")
            i++
        }
        return f
    }
}
