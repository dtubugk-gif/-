package com.duplicatecleaner.app.scanner

import com.duplicatecleaner.app.model.DuplicateGroup
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.ScanPhase
import com.duplicatecleaner.app.model.ScanProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finds byte-for-byte identical files in three narrowing passes:
 *
 * 1. Group by size. A file whose size is unique cannot have a duplicate, so it is dropped
 *    without ever being opened. This eliminates the vast majority of a typical library.
 * 2. Hash only the first [QUICK_HASH_BYTES] of every remaining candidate. Same-size files
 *    that differ early (almost all of them) are dropped after reading a few kilobytes.
 * 3. Hash the entire content of what is left. Only files with an identical full SHA-256
 *    and identical size are reported as duplicates. Full hashes are memoised in an
 *    optional [HashCache] keyed by path, size and modification time, so re-scans skip
 *    the expensive pass for unchanged files.
 *
 * The class is pure Kotlin: callers supply [open], which turns a [FileEntry] into an
 * [InputStream] (a ContentResolver on Android, a FileInputStream in tests).
 */
class DuplicateFinder(
    private val open: (FileEntry) -> InputStream,
    private val cache: HashCache? = null,
    private val parallelism: Int = defaultParallelism(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun find(
        entries: List<FileEntry>,
        onProgress: (ScanProgress) -> Unit = {},
    ): List<DuplicateGroup> {
        onProgress(ScanProgress(ScanPhase.SIZES, 0, entries.size))
        val candidates = entries.asSequence()
            .filter { it.size > 0 }
            .groupBy { it.size }
            .values
            .filter { it.size >= 2 }
            .flatten()
        if (candidates.isEmpty()) return emptyList()

        val quickHashes = hashAll(candidates, ScanPhase.QUICK_HASH, onProgress, ::quickHash)
        val quickBuckets = candidates
            .mapNotNull { entry -> quickHashes[entry.key]?.let { entry to it } }
            .groupBy { (entry, hash) -> SizeAndHash(entry.size, hash) }
            .values
            .filter { it.size >= 2 }
        if (quickBuckets.isEmpty()) return emptyList()

        // A file no larger than the quick-hash window has already been hashed completely.
        val fullHashes = HashMap<String, String>()
        val needFull = ArrayList<FileEntry>()
        for (bucket in quickBuckets) {
            for ((entry, hash) in bucket) {
                if (entry.size <= QUICK_HASH_BYTES) fullHashes[entry.key] = hash else needFull += entry
            }
        }
        fullHashes += hashAll(needFull, ScanPhase.FULL_HASH, onProgress, ::cachedFullHash)

        return quickBuckets.asSequence()
            .flatten()
            .mapNotNull { (entry, _) -> fullHashes[entry.key]?.let { entry to it } }
            .groupBy { (entry, hash) -> SizeAndHash(entry.size, hash) }
            .values
            .filter { it.size >= 2 }
            .map { bucket ->
                val files = bucket.map { it.first }.sortedWith(KEEPER_ORDER)
                DuplicateGroup(id = "${files.first().size}:${bucket.first().second}", files = files)
            }
            .sortedWith(compareByDescending<DuplicateGroup> { it.wastedBytes }.thenBy { it.id })
    }

    private suspend fun hashAll(
        items: List<FileEntry>,
        phase: ScanPhase,
        onProgress: (ScanProgress) -> Unit,
        hasher: (FileEntry, () -> Boolean) -> String,
    ): Map<String, String> = coroutineScope {
        onProgress(ScanProgress(phase, 0, items.size))
        if (items.isEmpty()) return@coroutineScope emptyMap()
        val done = AtomicInteger(0)
        val gate = Semaphore(parallelism)
        val results = items.map { entry ->
            async(dispatcher) {
                gate.withPermit {
                    val context = coroutineContext
                    val hash = try {
                        hasher(entry) { context.isActive }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (ignored: Exception) {
                        null // unreadable or vanished file: silently skip it
                    }
                    val n = done.incrementAndGet()
                    if (n % PROGRESS_EVERY == 0 || n == items.size) {
                        onProgress(ScanProgress(phase, n, items.size, entry.name))
                    }
                    entry.key to hash
                }
            }
        }.awaitAll()
        val map = HashMap<String, String>(results.size)
        for ((key, hash) in results) if (hash != null) map[key] = hash
        map
    }

    private fun quickHash(entry: FileEntry, isActive: () -> Boolean): String =
        open(entry).use { input -> digest(input, QUICK_HASH_BYTES.toLong(), isActive) }

    private fun fullHash(entry: FileEntry, isActive: () -> Boolean): String =
        open(entry).use { input -> digest(input, Long.MAX_VALUE, isActive) }

    private fun cachedFullHash(entry: FileEntry, isActive: () -> Boolean): String {
        val cacheKey = "${entry.key}|${entry.size}|${entry.modifiedMillis}"
        cache?.get(cacheKey)?.let { return it }
        val hash = fullHash(entry, isActive)
        cache?.put(cacheKey, hash)
        return hash
    }

    private fun digest(input: InputStream, limit: Long, isActive: () -> Boolean): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = limit
        while (remaining > 0) {
            if (!isActive()) throw CancellationException("scan cancelled")
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n < 0) break
            md.update(buffer, 0, n)
            remaining -= n
        }
        return md.digest().toHex()
    }

    private data class SizeAndHash(val size: Long, val hash: String)

    companion object {
        const val QUICK_HASH_BYTES = 64 * 1024
        private const val BUFFER_SIZE = 256 * 1024
        private const val PROGRESS_EVERY = 8

        private val MESSENGER_OR_DOWNLOAD =
            Regex("/(WhatsApp|Telegram|Download|Downloads|Bluetooth|Snapchat|Messenger)/", RegexOption.IGNORE_CASE)

        /**
         * Which copy should be kept by default: the camera original first, then anything
         * that is not a messenger/download copy, then the oldest, then the shortest path.
         */
        val KEEPER_ORDER: Comparator<FileEntry> = compareBy<FileEntry>(
            { if (it.path.contains("/DCIM/Camera", ignoreCase = true)) 0 else 1 },
            { if (MESSENGER_OR_DOWNLOAD.containsMatchIn(it.path)) 1 else 0 },
            { it.modifiedMillis },
            { it.path.length },
            { it.path },
        )

        fun defaultParallelism(): Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

        private val HEX = "0123456789abcdef".toCharArray()

        private fun ByteArray.toHex(): String {
            val out = CharArray(size * 2)
            for (i in indices) {
                val v = this[i].toInt() and 0xFF
                out[i * 2] = HEX[v ushr 4]
                out[i * 2 + 1] = HEX[v and 0x0F]
            }
            return String(out)
        }
    }
}
