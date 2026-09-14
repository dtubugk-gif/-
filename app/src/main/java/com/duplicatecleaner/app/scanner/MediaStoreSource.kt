package com.duplicatecleaner.app.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.model.mediaKindOf

/**
 * Lists photos, videos and audio through MediaStore. This is the only way to reach
 * other apps' media under scoped storage (Android 10+) without "All files access".
 */
object MediaStoreSource {

    private val FILES_URI: Uri = MediaStore.Files.getContentUri("external")

    @Suppress("DEPRECATION")
    private val PROJECTION = arrayOf(
        FileColumns._ID,
        FileColumns.DATA,
        FileColumns.DISPLAY_NAME,
        FileColumns.SIZE,
        FileColumns.DATE_MODIFIED,
        FileColumns.MIME_TYPE,
        FileColumns.MEDIA_TYPE,
    )

    fun query(
        context: Context,
        kinds: Set<MediaKind>,
        onCount: (Int) -> Unit = {},
        isActive: () -> Boolean = { true },
    ): List<FileEntry> {
        val mediaTypes = kinds.mapNotNull {
            when (it) {
                MediaKind.IMAGE -> FileColumns.MEDIA_TYPE_IMAGE
                MediaKind.VIDEO -> FileColumns.MEDIA_TYPE_VIDEO
                MediaKind.AUDIO -> FileColumns.MEDIA_TYPE_AUDIO
                MediaKind.OTHER -> null
            }
        }
        if (mediaTypes.isEmpty()) return emptyList()

        val selection = "${FileColumns.MEDIA_TYPE} IN (${mediaTypes.joinToString(",") { "?" }})"
        val args = mediaTypes.map { it.toString() }.toTypedArray()
        val result = ArrayList<FileEntry>()

        context.contentResolver.query(FILES_URI, PROJECTION, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(FileColumns._ID)
            @Suppress("DEPRECATION")
            val dataCol = c.getColumnIndexOrThrow(FileColumns.DATA)
            val nameCol = c.getColumnIndexOrThrow(FileColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(FileColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
            val mimeCol = c.getColumnIndexOrThrow(FileColumns.MIME_TYPE)
            val typeCol = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)

            while (c.moveToNext()) {
                if (!isActive()) break
                val id = c.getLong(idCol)
                val path = c.getString(dataCol).orEmpty()
                val mime = c.getString(mimeCol)
                val name = c.getString(nameCol) ?: path.substringAfterLast('/')
                val kind = when (c.getInt(typeCol)) {
                    FileColumns.MEDIA_TYPE_IMAGE -> MediaKind.IMAGE
                    FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                    FileColumns.MEDIA_TYPE_AUDIO -> MediaKind.AUDIO
                    else -> mediaKindOf(mime, name)
                }
                val uri = contentUriFor(kind, id)
                result += FileEntry(
                    key = path.ifEmpty { uri.toString() },
                    uri = uri.toString(),
                    path = path,
                    name = name,
                    size = c.getLong(sizeCol),
                    modifiedMillis = c.getLong(dateCol) * 1000L,
                    mimeType = mime,
                    kind = kind,
                )
                if (result.size % 500 == 0) onCount(result.size)
            }
        }
        onCount(result.size)
        return result
    }

    fun contentUriFor(kind: MediaKind, id: Long): Uri {
        val collection = when (kind) {
            MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaKind.OTHER -> FILES_URI
        }
        return ContentUris.withAppendedId(collection, id)
    }

    /** Looks up the MediaStore row for an absolute path, used when deleting file-system entries. */
    @Suppress("DEPRECATION")
    fun findUriByPath(resolver: ContentResolver, path: String): Uri? {
        if (path.isEmpty()) return null
        val projection = arrayOf(FileColumns._ID, FileColumns.MEDIA_TYPE)
        return try {
            resolver.query(FILES_URI, projection, "${FileColumns.DATA}=?", arrayOf(path), null)?.use { c ->
                if (!c.moveToFirst()) return null
                val id = c.getLong(0)
                val kind = when (c.getInt(1)) {
                    FileColumns.MEDIA_TYPE_IMAGE -> MediaKind.IMAGE
                    FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                    FileColumns.MEDIA_TYPE_AUDIO -> MediaKind.AUDIO
                    else -> MediaKind.OTHER
                }
                contentUriFor(kind, id)
            }
        } catch (ignored: Exception) {
            null
        }
    }

    /** Removes a stale index row after a file was deleted directly on disk. Best effort. */
    @Suppress("DEPRECATION")
    fun removeIndexEntry(resolver: ContentResolver, path: String) {
        try {
            resolver.delete(FILES_URI, "${FileColumns.DATA}=?", arrayOf(path))
        } catch (ignored: Exception) {
            // The media scanner will drop the row on its own eventually.
        }
    }
}
