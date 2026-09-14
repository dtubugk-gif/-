package com.duplicatecleaner.app.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

/** Resolves the top-level directories of every mounted storage volume (internal + SD cards). */
object StorageRoots {

    fun get(context: Context): List<File> {
        val roots = LinkedHashSet<File>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(StorageManager::class.java)
            manager?.storageVolumes?.forEach { volume ->
                if (volume.state == Environment.MEDIA_MOUNTED) volume.directory?.let { roots += it }
            }
        }

        if (roots.isEmpty()) {
            roots += Environment.getExternalStorageDirectory()
            // Secondary volumes: derive the volume root from our app-specific directory on it.
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                val path = dir.absolutePath
                val marker = "/Android/data/"
                val index = path.indexOf(marker)
                if (index > 0) roots += File(path.substring(0, index))
            }
        }

        return roots.filter { it.isDirectory && it.canRead() }
    }
}
