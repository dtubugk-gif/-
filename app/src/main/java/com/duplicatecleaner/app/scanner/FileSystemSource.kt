package com.duplicatecleaner.app.scanner

import android.webkit.MimeTypeMap
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.model.mediaKindOf
import java.io.File

/**
 * Walks storage volumes directly. Requires "All files access" (Android 11+) or the legacy
 * WRITE_EXTERNAL_STORAGE permission (Android 8-10). Finds everything MediaStore does plus
 * documents, downloads and files in folders the media scanner skips.
 */
object FileSystemSource {

    fun walk(
        roots: List<File>,
        kinds: Set<MediaKind>,
        onCount: (Int) -> Unit = {},
        isActive: () -> Boolean = { true },
    ): List<FileEntry> {
        val result = ArrayList<FileEntry>()
        val seen = HashSet<String>()
        for (root in roots) {
            root.walkTopDown()
                .onEnter { dir -> isActive() && shouldEnter(dir) }
                .forEach { file ->
                    if (!file.isFile) return@forEach
                    val path = file.absolutePath
                    if (!seen.add(path)) return@forEach
                    val mime = mimeTypeFor(file.name)
                    val kind = mediaKindOf(mime, file.name)
                    if (kind !in kinds) return@forEach
                    result += FileEntry(
                        key = path,
                        uri = null,
                        path = path,
                        name = file.name,
                        size = file.length(),
                        modifiedMillis = file.lastModified(),
                        mimeType = mime,
                        kind = kind,
                    )
                    if (result.size % 500 == 0) onCount(result.size)
                }
        }
        onCount(result.size)
        return result
    }

    private fun shouldEnter(dir: File): Boolean {
        val name = dir.name
        // Hidden folders hold caches and thumbnails, not user files.
        if (name.startsWith(".")) return false
        // Android/data and Android/obb are private app sandboxes and unreadable anyway.
        if ((name == "data" || name == "obb") && dir.parentFile?.name == "Android") return false
        return true
    }

    fun mimeTypeFor(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
