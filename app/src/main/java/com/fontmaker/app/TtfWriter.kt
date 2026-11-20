package com.fontmaker.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.TreeMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds a real TrueType (.ttf) font from hand-drawn strokes.
 * Each pen stroke is converted to filled outlines: a round "dot" at every point plus a
 * rectangle along every segment, all wound clockwise so overlaps fill correctly.
 */
object TtfWriter {
    private const val UPM = 1000

    private class P(val x: Int, val y: Int, val on: Boolean)

    private class OutGlyph(val contours: List<List<P>>, val advance: Int) {
        private val all = contours.flatten()
        val empty = all.isEmpty()
        val xMin = all.minOfOrNull { it.x } ?: 0
        val yMin = all.minOfOrNull { it.y } ?: 0
        val xMax = all.maxOfOrNull { it.x } ?: 0
        val yMax = all.maxOfOrNull { it.y } ?: 0
        val pointCount = all.size
    }

    private inline fun bytes(block: DataOutputStream.() -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { it.block() }
        return bos.toByteArray()
    }

    fun write(font: HandFont): ByteArray {
        val chars = CharacterSet.ALL.filter { font.has(it) && it != " " }.distinct().sortedBy { it.codePointAt(0) }
        val glyphs = mutableListOf(notdef(), OutGlyph(emptyList(), (Metrics.SPACE_WIDTH * UPM).roundToInt()))
        val cmap = TreeMap<Int, Int>()
        cmap[0x20] = 1
        cmap[0xA0] = 1
        for (ch in chars) {
            glyphs += outline(font, font.glyph(ch)!!)
            cmap[ch.codePointAt(0)] = glyphs.size - 1
        }

        val tables = TreeMap<String, ByteArray>()

        // glyf + loca
        val glyf = ByteArrayOutputStream()
        val offsets = IntArray(glyphs.size + 1)
        glyphs.forEachIndexed { i, g ->
            offsets[i] = glyf.size()
            glyf.write(encodeGlyph(g))
            while (glyf.size() % 4 != 0) glyf.write(0)
        }
        offsets[glyphs.size] = glyf.size()
        tables["glyf"] = glyf.toByteArray()
        tables["loca"] = bytes { offsets.forEach { writeInt(it) } }

        val inked = glyphs.filter { !it.empty }
        val xMin = inked.minOfOrNull { it.xMin } ?: 0
        val yMin = inked.minOfOrNull { it.yMin } ?: 0
        val xMax = inked.maxOfOrNull { it.xMax } ?: 0
        val yMax = inked.maxOfOrNull { it.yMax } ?: 0
        val ascender = maxOf(780, yMax)
        val descender = minOf(-280, yMin)

        val date = System.currentTimeMillis() / 1000 + 2082844800L
        tables["head"] = bytes {
            writeInt(0x00010000); writeInt(0x00010000); writeInt(0); writeInt(0x5F0F3CF5)
            writeShort(0x000B); writeShort(UPM); writeLong(date); writeLong(date)
            writeShort(xMin); writeShort(yMin); writeShort(xMax); writeShort(yMax)
            writeShort(0); writeShort(8); writeShort(2); writeShort(1); writeShort(0)
        }

        tables["hhea"] = bytes {
            writeInt(0x00010000)
            writeShort(ascender); writeShort(descender); writeShort(0)
            writeShort(glyphs.maxOf { it.advance })
            writeShort(inked.minOfOrNull { it.xMin } ?: 0)
            writeShort(inked.minOfOrNull { it.advance - it.xMax } ?: 0)
            writeShort(xMax)
            writeShort(1); writeShort(0); writeShort(0)
            repeat(4) { writeShort(0) }
            writeShort(0)
            writeShort(glyphs.size)
        }

        tables["hmtx"] = bytes {
            for (g in glyphs) { writeShort(g.advance); writeShort(if (g.empty) 0 else g.xMin) }
        }

        tables["maxp"] = bytes {
            writeInt(0x00010000)
            writeShort(glyphs.size)
            writeShort(glyphs.maxOf { it.pointCount })
            writeShort(glyphs.maxOf { it.contours.size })
            writeShort(0); writeShort(0); writeShort(2)
            repeat(8) { writeShort(0) }
        }

        val advances = glyphs.map { it.advance }.filter { it > 0 }
        tables["OS/2"] = bytes {
            writeShort(4)
            writeShort(if (advances.isEmpty()) 500 else advances.average().roundToInt())
            writeShort(400); writeShort(5); writeShort(0)
            writeShort(650); writeShort(600); writeShort(0); writeShort(75)
            writeShort(650); writeShort(600); writeShort(0); writeShort(350)
            writeShort(50); writeShort(300)
            writeShort(0)
            repeat(10) { writeByte(0) }
            writeInt(1); writeInt(0); writeInt(0); writeInt(0)
            writeBytes("FMKR")
            writeShort(0x40)
            writeShort(cmap.firstKey()); writeShort(minOf(cmap.lastKey(), 0xFFFF))
            writeShort(ascender); writeShort(descender); writeShort(0)
            writeShort(maxOf(ascender, yMax)); writeShort(maxOf(-descender, -yMin))
            writeInt(1); writeInt(0)
            writeShort(((Metrics.BASELINE - Metrics.X_HEIGHT) * UPM).roundToInt())
            writeShort(((Metrics.BASELINE - Metrics.CAP) * UPM).roundToInt())
            writeShort(0); writeShort(32); writeShort(1)
        }

        tables["post"] = bytes {
            writeInt(0x00030000); writeInt(0); writeShort(-100); writeShort(50)
            repeat(5) { writeInt(0) }
        }

        tables["cmap"] = buildCmap(cmap)
        tables["name"] = buildName(font.name)

        return assemble(tables)
    }

    private fun notdef(): OutGlyph {
        val outer = listOf(P(50, 0, true), P(50, 700, true), P(450, 700, true), P(450, 0, true))
        val inner = listOf(P(100, 50, true), P(400, 50, true), P(400, 650, true), P(100, 650, true))
        return OutGlyph(listOf(outer, inner), 500)
    }

    private fun outline(font: HandFont, g: Glyph): OutGlyph {
        val m = font.metrics(g)
        val r = font.strokeWidth / 2f * UPM
        val contours = mutableListOf<List<P>>()
        for (s in g.strokes) {
            if (s.points.isEmpty()) continue
            val pts = Geometry.simplify(s.points, 0.003f).map {
                floatArrayOf((it.x + m.offsetX) * UPM, (Metrics.BASELINE - it.y) * UPM)
            }
            for (p in pts) contours += circle(p[0], p[1], r)
            for (i in 0 until pts.size - 1) segment(pts[i], pts[i + 1], r)?.let { contours += it }
        }
        return OutGlyph(contours, (m.advance * UPM).roundToInt())
    }

    /** Circle as 8 quadratic arcs, clockwise (decreasing angle with y pointing up). */
    private fun circle(cx: Float, cy: Float, r: Float): List<P> {
        val out = ArrayList<P>(16)
        val rr = (r / cos(PI / 8)).toFloat()
        for (k in 0 until 8) {
            val a = -k * PI / 4
            out += P((cx + r * cos(a)).roundToInt(), (cy + r * sin(a)).roundToInt(), true)
            val b = -(k + 0.5) * PI / 4
            out += P((cx + rr * cos(b)).roundToInt(), (cy + rr * sin(b)).roundToInt(), false)
        }
        return out
    }

    private fun segment(a: FloatArray, b: FloatArray, r: Float): List<P>? {
        val dx = b[0] - a[0]
        val dy = b[1] - a[1]
        val len = hypot(dx, dy)
        if (len < 1f) return null
        val nx = -dy / len * r
        val ny = dx / len * r
        val pts = mutableListOf(
            P((a[0] + nx).roundToInt(), (a[1] + ny).roundToInt(), true),
            P((b[0] + nx).roundToInt(), (b[1] + ny).roundToInt(), true),
            P((b[0] - nx).roundToInt(), (b[1] - ny).roundToInt(), true),
            P((a[0] - nx).roundToInt(), (a[1] - ny).roundToInt(), true),
        )
        var area = 0L
        for (i in pts.indices) {
            val p = pts[i]
            val q = pts[(i + 1) % pts.size]
            area += p.x.toLong() * q.y - q.x.toLong() * p.y
        }
        if (area == 0L) return null
        if (area > 0) pts.reverse() // make clockwise
        return pts
    }

    private fun encodeGlyph(g: OutGlyph): ByteArray {
        if (g.empty) return ByteArray(0)
        return bytes {
            writeShort(g.contours.size)
            writeShort(g.xMin); writeShort(g.yMin); writeShort(g.xMax); writeShort(g.yMax)
            var idx = -1
            for (c in g.contours) { idx += c.size; writeShort(idx) }
            writeShort(0) // no instructions
            for (c in g.contours) for (p in c) writeByte(if (p.on) 1 else 0)
            var last = 0
            for (c in g.contours) for (p in c) { writeShort(p.x - last); last = p.x }
            last = 0
            for (c in g.contours) for (p in c) { writeShort(p.y - last); last = p.y }
        }
    }

    private fun buildCmap(map: TreeMap<Int, Int>): ByteArray {
        val entries = map.entries.filter { it.key < 0xFFFF }
        val segCount = entries.size + 1
        var pow = 1
        var log = 0
        while (pow * 2 <= segCount) { pow *= 2; log++ }
        val searchRange = pow * 2
        val sub = bytes {
            writeShort(4); writeShort(16 + 8 * segCount); writeShort(0)
            writeShort(segCount * 2); writeShort(searchRange); writeShort(log); writeShort(segCount * 2 - searchRange)
            for (e in entries) writeShort(e.key)
            writeShort(0xFFFF)
            writeShort(0)
            for (e in entries) writeShort(e.key)
            writeShort(0xFFFF)
            for (e in entries) writeShort((e.value - e.key) and 0xFFFF)
            writeShort(1)
            repeat(segCount) { writeShort(0) }
        }
        return bytes {
            writeShort(0); writeShort(2)
            writeShort(0); writeShort(3); writeInt(20)
            writeShort(3); writeShort(1); writeInt(20)
            write(sub)
        }
    }

    private fun buildName(family: String): ByteArray {
        val clean = family.trim().ifEmpty { "My Handwriting" }
        val ps = clean.filter { it.isLetterOrDigit() && it.code < 128 }.take(50).ifEmpty { "HandFont" } + "-Regular"
        val records = listOf(
            1 to clean,
            2 to "Regular",
            3 to "FontMaker: $clean",
            4 to clean,
            5 to "Version 1.000",
            6 to ps,
        ).map { it.first to it.second.toByteArray(Charsets.UTF_16BE) }
        return bytes {
            writeShort(0); writeShort(records.size); writeShort(6 + 12 * records.size)
            var off = 0
            for ((id, data) in records) {
                writeShort(3); writeShort(1); writeShort(0x409); writeShort(id)
                writeShort(data.size); writeShort(off)
                off += data.size
            }
            for ((_, data) in records) write(data)
        }
    }

    private fun pad4(n: Int) = (n + 3) and 3.inv()

    private fun checksum(data: ByteArray): Long {
        var sum = 0L
        var i = 0
        while (i < data.size) {
            var word = 0L
            for (k in 0 until 4) {
                word = (word shl 8) or (if (i + k < data.size) (data[i + k].toLong() and 0xFF) else 0L)
            }
            sum = (sum + word) and 0xFFFFFFFFL
            i += 4
        }
        return sum
    }

    private fun assemble(tables: TreeMap<String, ByteArray>): ByteArray {
        val n = tables.size
        var pow = 1
        var log = 0
        while (pow * 2 <= n) { pow *= 2; log++ }
        var offset = 12 + 16 * n
        var headOffset = 0
        val out = bytes {
            writeInt(0x00010000); writeShort(n); writeShort(pow * 16); writeShort(log); writeShort(n * 16 - pow * 16)
            for ((tag, data) in tables) {
                if (tag == "head") headOffset = offset
                writeBytes(tag)
                writeInt(checksum(data).toInt())
                writeInt(offset)
                writeInt(data.size)
                offset += pad4(data.size)
            }
            for ((_, data) in tables) {
                write(data)
                repeat(pad4(data.size) - data.size) { writeByte(0) }
            }
        }
        val adj = (0xB1B0AFBAL - checksum(out)) and 0xFFFFFFFFL
        out[headOffset + 8] = (adj shr 24).toByte()
        out[headOffset + 9] = (adj shr 16).toByte()
        out[headOffset + 10] = (adj shr 8).toByte()
        out[headOffset + 11] = adj.toByte()
        return out
    }
}
