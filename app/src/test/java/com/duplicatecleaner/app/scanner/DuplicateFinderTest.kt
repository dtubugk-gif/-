package com.duplicatecleaner.app.scanner

import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.model.ScanPhase
import com.duplicatecleaner.app.model.ScanProgress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.util.Random

class DuplicateFinderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val random = Random(42)

    private fun bytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun file(relative: String, content: ByteArray, modified: Long? = null): File {
        val f = File(tmp.root, relative)
        f.parentFile!!.mkdirs()
        f.writeBytes(content)
        if (modified != null) f.setLastModified(modified)
        return f
    }

    private fun entry(f: File) = FileEntry(
        key = f.path,
        uri = null,
        path = f.path,
        name = f.name,
        size = f.length(),
        modifiedMillis = f.lastModified(),
        mimeType = null,
        kind = MediaKind.OTHER,
    )

    private fun finder(cache: HashCache? = null) =
        DuplicateFinder(open = { FileInputStream(it.path) }, cache = cache, parallelism = 3)

    @Test
    fun `groups exact duplicates and ignores everything else`() = runBlocking {
        val big = bytes(300 * 1024)
        val a = file("a/photo.jpg", big)
        val b = file("b/copy of photo.jpg", big)
        val c = file("c/third.jpg", big)

        // Same size and same first 64 KB as `big`, but differs at the very end.
        val tail = big.copyOf().also { it[it.lastIndex] = (it[it.lastIndex] + 1).toByte() }
        val nearMiss = file("d/near-miss.jpg", tail)

        val small = bytes(100)
        val s1 = file("e/small1.txt", small)
        val s2 = file("f/small2.txt", small)

        val unique = file("g/unique.bin", bytes(300 * 1024 + 1))
        val empty1 = file("h/empty1", ByteArray(0))
        val empty2 = file("h/empty2", ByteArray(0))

        val entries = listOf(a, b, c, nearMiss, s1, s2, unique, empty1, empty2).map(::entry)
        val groups = finder().find(entries)

        assertEquals(2, groups.size)
        val bigGroup = groups.first { it.fileSize == big.size.toLong() }
        val smallGroup = groups.first { it.fileSize == small.size.toLong() }
        assertEquals(setOf(a.path, b.path, c.path), bigGroup.files.map { it.path }.toSet())
        assertEquals(setOf(s1.path, s2.path), smallGroup.files.map { it.path }.toSet())
        // Biggest saving first.
        assertEquals(bigGroup, groups.first())
        assertEquals(2L * big.size, bigGroup.wastedBytes)
        assertFalse(groups.flatMap { it.files }.any { it.path == nearMiss.path })
    }

    @Test
    fun `keeper order prefers camera folder, then non-messenger, then oldest`() = runBlocking {
        val content = bytes(200 * 1024)
        val now = System.currentTimeMillis()
        val whatsapp = file("Android/media/com.whatsapp/WhatsApp/Media/IMG.jpg", content, now - 300_000)
        val download = file("Download/IMG.jpg", content, now - 200_000)
        val camera = file("DCIM/Camera/IMG.jpg", content, now)
        val newer = file("Pictures/Screenshots/IMG.jpg", content, now - 100_000)
        val older = file("Pictures/Other/IMG.jpg", content, now - 250_000)

        val groups = finder().find(listOf(whatsapp, download, camera, newer, older).map(::entry))

        assertEquals(1, groups.size)
        val order = groups.single().files.map { it.path }
        assertEquals(camera.path, order[0])
        assertEquals(older.path, order[1])
        assertEquals(newer.path, order[2])
        assertTrue(order.indexOf(whatsapp.path) > order.indexOf(newer.path))
        assertTrue(order.indexOf(download.path) > order.indexOf(newer.path))
    }

    @Test
    fun `hash cache is populated and reused`() = runBlocking {
        val content = bytes(150 * 1024)
        val a = file("x/a.bin", content)
        val b = file("y/b.bin", content)
        val cache = HashCache(File(tmp.root, "cache.bin"))

        val first = finder(cache).find(listOf(a, b).map(::entry))
        assertEquals(1, first.size)
        assertEquals(2, cache.size())
        cache.save()

        val reloaded = HashCache(File(tmp.root, "cache.bin")).apply { load() }
        assertEquals(2, reloaded.size())
        val key = "${a.path}|${a.length()}|${a.lastModified()}"
        assertNotNull(reloaded.get(key))

        // With a cache hit, the finder must not need to read the file at all.
        var opened = 0
        val cachedFinder = DuplicateFinder(
            open = { opened++; FileInputStream(it.path) },
            cache = reloaded,
            parallelism = 2,
        )
        val second = cachedFinder.find(listOf(a, b).map(::entry))
        assertEquals(1, second.size)
        assertEquals("only the quick-hash pass should open files", 2, opened)
    }

    @Test
    fun `unreadable files are skipped, not fatal`() = runBlocking {
        val content = bytes(80 * 1024)
        val a = file("p/a.bin", content)
        val b = file("q/b.bin", content)
        val ghost = entry(a).copy(key = "/nonexistent/ghost.bin", path = "/nonexistent/ghost.bin")

        val groups = finder().find(listOf(entry(a), entry(b), ghost))

        assertEquals(1, groups.size)
        assertEquals(setOf(a.path, b.path), groups.single().files.map { it.path }.toSet())
    }

    @Test
    fun `progress reports every phase`() = runBlocking {
        val content = bytes(120 * 1024)
        val a = file("m/a.bin", content)
        val b = file("n/b.bin", content)
        val phases = LinkedHashSet<ScanPhase>()
        var last: ScanProgress? = null

        finder().find(listOf(a, b).map(::entry)) { p -> phases += p.phase; last = p }

        assertEquals(listOf(ScanPhase.SIZES, ScanPhase.QUICK_HASH, ScanPhase.FULL_HASH), phases.toList())
        assertEquals(ScanPhase.FULL_HASH, last!!.phase)
        assertEquals(2, last!!.done)
        assertEquals(2, last!!.total)
    }
}
