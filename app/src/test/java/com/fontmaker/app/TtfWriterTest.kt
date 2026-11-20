package com.fontmaker.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class TtfWriterTest {
    private fun line(vararg xy: Float) = Stroke(xy.toList().chunked(2).map { Pt(it[0], it[1]) }.toMutableList())

    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Double, to: Double): Stroke {
        val s = Stroke()
        for (i in 0..24) {
            val a = from + (to - from) * i / 24
            s.points.add(Pt(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat()))
        }
        return s
    }

    private fun sampleFont(): HandFont {
        val f = HandFont("test", "Test Hand")
        f.glyphs["H"] = Glyph(mutableListOf(line(0.3f, 0.22f, 0.3f, 0.72f), line(0.6f, 0.22f, 0.6f, 0.72f), line(0.3f, 0.47f, 0.6f, 0.47f)))
        f.glyphs["i"] = Glyph(mutableListOf(line(0.5f, 0.44f, 0.5f, 0.72f), line(0.5f, 0.32f)))
        f.glyphs["o"] = Glyph(mutableListOf(arc(0.5f, 0.58f, 0.13f, 0.14f, 0.0, 2 * PI)))
        f.glyphs["l"] = Glyph(mutableListOf(line(0.45f, 0.2f, 0.47f, 0.72f)))
        f.glyphs["e"] = Glyph(mutableListOf(Stroke((listOf(Pt(0.38f, 0.58f), Pt(0.62f, 0.58f)) + arc(0.5f, 0.58f, 0.12f, 0.14f, 0.0, -1.6 * PI).points).toMutableList())))
        f.glyphs["!"] = Glyph(mutableListOf(line(0.5f, 0.22f, 0.5f, 0.6f), line(0.5f, 0.71f)))
        return f
    }

    @Test
    fun writesValidSfntStructure() {
        val bytes = TtfWriter.write(sampleFont())
        val buf = ByteBuffer.wrap(bytes)
        assertEquals(0x00010000, buf.getInt(0))
        val numTables = buf.getShort(4).toInt()
        assertEquals(10, numTables)
        // whole-file checksum must equal the magic constant
        var sum = 0L
        var i = 0
        while (i < bytes.size) {
            var w = 0L
            for (k in 0 until 4) w = (w shl 8) or (if (i + k < bytes.size) bytes[i + k].toLong() and 0xFF else 0)
            sum = (sum + w) and 0xFFFFFFFFL
            i += 4
        }
        assertEquals(0xB1B0AFBAL, sum)
        assertTrue(bytes.size > 1000)
        System.getProperty("ttfOut")?.let { File(it).writeBytes(bytes) }
    }
}
