package com.duplicatecleaner.app.model

enum class MediaKind { IMAGE, VIDEO, AUDIO, OTHER }

/**
 * A single file discovered by a scan. Pure Kotlin so the duplicate-finding logic
 * can be unit tested on the JVM.
 */
data class FileEntry(
    /** Stable unique key: the absolute path when known, otherwise the content URI. */
    val key: String,
    /** MediaStore content URI (as a string), or null for files found by walking the file system. */
    val uri: String?,
    val path: String,
    val name: String,
    val size: Long,
    val modifiedMillis: Long,
    val mimeType: String?,
    val kind: MediaKind,
)

/** A set of files whose contents are byte-for-byte identical. */
data class DuplicateGroup(
    val id: String,
    /** Sorted so that the suggested file to keep comes first. */
    val files: List<FileEntry>,
) {
    val fileSize: Long get() = files.firstOrNull()?.size ?: 0L
    val count: Int get() = files.size
    val wastedBytes: Long get() = fileSize * (count - 1).coerceAtLeast(0)
    val kind: MediaKind get() = files.firstOrNull()?.kind ?: MediaKind.OTHER
}

enum class ScanPhase { LISTING, SIZES, QUICK_HASH, FULL_HASH }

data class ScanProgress(
    val phase: ScanPhase,
    val done: Int = 0,
    val total: Int = 0,
    val currentName: String? = null,
)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "avif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "3gp", "webm", "avi", "m4v", "ts")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "ogg", "opus", "flac", "aac", "amr", "wma", "mid")

/** Classifies a file by MIME type, falling back to the extension. */
fun mediaKindOf(mimeType: String?, name: String): MediaKind {
    when {
        mimeType == null -> Unit
        mimeType.startsWith("image/") -> return MediaKind.IMAGE
        mimeType.startsWith("video/") -> return MediaKind.VIDEO
        mimeType.startsWith("audio/") -> return MediaKind.AUDIO
    }
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        in IMAGE_EXTENSIONS -> MediaKind.IMAGE
        in VIDEO_EXTENSIONS -> MediaKind.VIDEO
        in AUDIO_EXTENSIONS -> MediaKind.AUDIO
        else -> MediaKind.OTHER
    }
}
