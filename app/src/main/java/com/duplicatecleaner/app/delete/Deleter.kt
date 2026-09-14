package com.duplicatecleaner.app.delete

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.duplicatecleaner.app.Permissions
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.scanner.MediaStoreSource
import java.io.File

/**
 * Deletes files using whichever mechanism the current Android version and permission
 * state allow:
 *
 * - "All files access" (or pre-Android 11 with WRITE_EXTERNAL_STORAGE): direct deletion,
 *   no prompts.
 * - Android 11+ without it: [MediaStore.createDeleteRequest], which shows one system
 *   confirmation dialog for the whole batch. The caller launches the returned
 *   [IntentSender] and reports the result back.
 * - Android 10 in scoped mode: per-item [RecoverableSecurityException] flow.
 */
class Deleter(private val context: Context) {

    class Confirmation(
        val intentSender: IntentSender,
        /** Entries the system dialog covers. */
        val entries: List<FileEntry>,
        /** True when the dialog only grants access and the caller must call [delete] again. */
        val retryAfterConfirm: Boolean,
    )

    class Result(
        val deleted: List<FileEntry>,
        val failed: List<FileEntry>,
        val confirmation: Confirmation? = null,
        /** Entries not attempted yet because a confirmation interrupted the batch. */
        val remaining: List<FileEntry> = emptyList(),
    )

    fun delete(entries: List<FileEntry>, onProgress: (Int) -> Unit = {}): Result {
        val resolver = context.contentResolver
        val fullAccess = Permissions.hasAllFilesAccess(context)
        val deleted = ArrayList<FileEntry>()
        val failed = ArrayList<FileEntry>()
        val viaSystemDialog = ArrayList<Pair<FileEntry, Uri>>()

        entries.forEachIndexed { index, entry ->
            val uri = entry.uri?.let(Uri::parse)
                ?: if (!fullAccess) MediaStoreSource.findUriByPath(resolver, entry.path) else null

            if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !fullAccess) {
                viaSystemDialog += entry to uri
                return@forEachIndexed
            }

            var ok = false
            if (uri != null) {
                try {
                    ok = resolver.delete(uri, null, null) > 0
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        return Result(
                            deleted = deleted,
                            failed = failed,
                            confirmation = Confirmation(
                                intentSender = e.userAction.actionIntent.intentSender,
                                entries = listOf(entry),
                                retryAfterConfirm = true,
                            ),
                            remaining = entries.drop(index + 1),
                        )
                    }
                }
            }
            if (!ok && entry.path.isNotEmpty()) ok = deleteFromDisk(entry.path)
            if (ok) deleted += entry else failed += entry
            onProgress(index + 1)
        }

        if (viaSystemDialog.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createDeleteRequest(resolver, viaSystemDialog.map { it.second })
            return Result(
                deleted = deleted,
                failed = failed,
                confirmation = Confirmation(
                    intentSender = pending.intentSender,
                    entries = viaSystemDialog.map { it.first },
                    retryAfterConfirm = false,
                ),
            )
        }
        return Result(deleted, failed)
    }

    private fun deleteFromDisk(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return true
        if (!file.delete()) return false
        MediaStoreSource.removeIndexEntry(context.contentResolver, path)
        return true
    }
}
