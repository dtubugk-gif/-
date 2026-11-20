package com.fontmaker.app

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Every glyph is drawn inside a 1 x 1 box (x to the right, y downward).
 * These are the guide lines inside that box.
 */
object Metrics {
    const val CAP = 0.22f
    const val X_HEIGHT = 0.44f
    const val BASELINE = 0.72f
    const val DESCENDER = 0.90f
    const val SPACING = 0.07f
    const val SPACE_WIDTH = 0.30f
    const val MIN_STROKE = 0.02f
    const val MAX_STROKE = 0.14f
    const val DEFAULT_STROKE = 0.055f
}

data class Pt(val x: Float, val y: Float)

class Stroke(val points: MutableList<Pt> = mutableListOf()) {
    fun copy() = Stroke(points.toMutableList())
}

data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

data class GlyphMetrics(val offsetX: Float, val advance: Float)

class Glyph(val strokes: MutableList<Stroke> = mutableListOf()) {
    val isEmpty: Boolean get() = strokes.all { it.points.isEmpty() }

    fun copy() = Glyph(strokes.mapTo(mutableListOf()) { it.copy() })

    fun bounds(): Bounds? {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var any = false
        for (s in strokes) for (p in s.points) {
            any = true
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return if (any) Bounds(minX, minY, maxX, maxY) else null
    }
}

class HandFont(
    val id: String,
    var name: String,
    var strokeWidth: Float = Metrics.DEFAULT_STROKE,
    val glyphs: MutableMap<String, Glyph> = LinkedHashMap(),
) {
    fun glyph(ch: String): Glyph? = glyphs[ch]?.takeIf { !it.isEmpty }

    fun has(ch: String) = glyph(ch) != null

    val drawnCount: Int get() = CharacterSet.ALL.count { has(it) }

    /** Horizontal placement of a glyph: proportional width based on the ink. */
    fun metrics(g: Glyph): GlyphMetrics {
        val b = g.bounds() ?: return GlyphMetrics(0f, Metrics.SPACE_WIDTH)
        val half = strokeWidth / 2f
        return GlyphMetrics(
            offsetX = -b.minX + half + Metrics.SPACING / 2f,
            advance = (b.maxX - b.minX) + strokeWidth + Metrics.SPACING,
        )
    }
}

object CharacterSet {
    val LOWER = ('a'..'z').map { it.toString() }
    val UPPER = ('A'..'Z').map { it.toString() }
    val DIGITS = ('0'..'9').map { it.toString() }
    val SYMBOLS = listOf(
        ".", ",", "!", "?", "'", "\"", "-", ":", ";", "(", ")", "@", "#", "$", "%", "&",
        "*", "+", "=", "/", "_", "~", "<", ">", "[", "]", "{", "}", "^", "\\", "|",
    )
    val ALL = LOWER + UPPER + DIGITS + SYMBOLS

    fun describe(ch: String): String = when (ch) {
        in LOWER -> "Lowercase $ch"
        in UPPER -> "Capital $ch"
        in DIGITS -> "Number $ch"
        else -> "Symbol  $ch"
    }
}

object Geometry {
    /** Ramer–Douglas–Peucker polyline simplification. */
    fun simplify(pts: List<Pt>, epsilon: Float): List<Pt> {
        if (pts.size < 3) return pts.toList()
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.size - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, pts.size - 1))
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast().let { it[0] to it[1] }
            var maxD = 0f
            var idx = -1
            for (i in s + 1 until e) {
                val d = distToSegment(pts[i], pts[s], pts[e])
                if (d > maxD) {
                    maxD = d
                    idx = i
                }
            }
            if (idx >= 0 && maxD > epsilon) {
                keep[idx] = true
                stack.addLast(intArrayOf(s, idx))
                stack.addLast(intArrayOf(idx, e))
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    private fun distToSegment(p: Pt, a: Pt, b: Pt): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < 1e-6f) return hypot(p.x - a.x, p.y - a.y)
        return abs(dx * (a.y - p.y) - (a.x - p.x) * dy) / len
    }
}
