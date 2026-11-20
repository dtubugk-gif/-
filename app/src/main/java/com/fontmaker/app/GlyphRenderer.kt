package com.fontmaker.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class LaidOutChar(val ch: String, val x: Float, val line: Int)

class TextLayout(
    val items: List<LaidOutChar>,
    val lineWidths: List<Float>,
    val size: Float,
    val endX: Float,
) {
    val lineCount get() = lineWidths.size
    val lineHeight get() = size * GlyphRenderer.LINE_HEIGHT
    val width get() = lineWidths.maxOrNull() ?: 0f
    val height get() = lineCount * lineHeight
}

/** Lays out and draws text using hand-drawn glyphs (falls back to a system font for undrawn ones). */
object GlyphRenderer {
    const val LINE_HEIGHT = 0.86f
    /** How far the glyph box starts above a line's top edge (trims the empty space above capitals). */
    const val TOP_TRIM = 0.12f
    private const val FALLBACK_SCALE = 0.66f

    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("casual", Typeface.NORMAL)
    }
    private val tmpPath = Path()
    private val matrix = Matrix()

    fun strokePaint(color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    /** Smooth path for a glyph in unit (0..1) coordinates. */
    fun unitPath(g: Glyph): Path {
        val p = Path()
        for (s in g.strokes) {
            val pts = s.points
            if (pts.isEmpty()) continue
            p.moveTo(pts[0].x, pts[0].y)
            when (pts.size) {
                1 -> p.lineTo(pts[0].x + 0.0005f, pts[0].y)
                2 -> p.lineTo(pts[1].x, pts[1].y)
                else -> {
                    for (i in 1 until pts.size - 1) {
                        val mx = (pts[i].x + pts[i + 1].x) / 2f
                        val my = (pts[i].y + pts[i + 1].y) / 2f
                        p.quadTo(pts[i].x, pts[i].y, mx, my)
                    }
                    p.lineTo(pts.last().x, pts.last().y)
                }
            }
        }
        return p
    }

    fun advance(font: HandFont?, ch: String, size: Float): Float {
        if (ch == " ") return Metrics.SPACE_WIDTH * size
        if (ch == "\n") return 0f
        val g = font?.glyph(ch)
        if (g != null) return font.metrics(g).advance * size
        fallbackPaint.textSize = size * FALLBACK_SCALE
        return fallbackPaint.measureText(ch) + Metrics.SPACING * size
    }

    private fun splitChars(text: String): List<String> {
        val out = ArrayList<String>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            out.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return out
    }

    fun layout(text: String, font: HandFont?, size: Float, maxWidth: Float): TextLayout {
        val items = ArrayList<LaidOutChar>()
        val widths = ArrayList<Float>()
        var line = 0
        var x = 0f
        fun newLine() {
            widths.add(x)
            line++
            x = 0f
        }

        val chars = splitChars(text)
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            when (c) {
                "\n" -> { newLine(); i++ }
                " " -> {
                    if (x > 0f) x += advance(font, c, size)
                    i++
                }
                else -> {
                    var j = i
                    var wordW = 0f
                    while (j < chars.size && chars[j] != " " && chars[j] != "\n") {
                        wordW += advance(font, chars[j], size); j++
                    }
                    if (x > 0f && x + wordW > maxWidth) newLine()
                    for (k in i until j) {
                        val a = advance(font, chars[k], size)
                        if (x > 0f && x + a > maxWidth) newLine()
                        items.add(LaidOutChar(chars[k], x, line))
                        x += a
                    }
                    i = j
                }
            }
        }
        widths.add(x)
        return TextLayout(items, widths, size, x)
    }

    fun drawChar(canvas: Canvas, font: HandFont?, ch: String, x: Float, lineTop: Float, size: Float, paint: Paint) {
        val top = lineTop - TOP_TRIM * size
        val g = font?.glyph(ch)
        if (g != null) {
            val m = font.metrics(g)
            matrix.setScale(size, size)
            matrix.postTranslate(x + m.offsetX * size, top)
            tmpPath.reset()
            unitPath(g).transform(matrix, tmpPath)
            paint.strokeWidth = font.strokeWidth * size
            canvas.drawPath(tmpPath, paint)
        } else if (ch != " " && ch != "\n") {
            fallbackPaint.textSize = size * FALLBACK_SCALE
            fallbackPaint.color = paint.color
            canvas.drawText(ch, x + Metrics.SPACING * size / 2f, top + Metrics.BASELINE * size, fallbackPaint)
        }
    }

    fun draw(canvas: Canvas, layout: TextLayout, font: HandFont?, color: Int, left: Float, top: Float) {
        val paint = strokePaint(color)
        for (it in layout.items) {
            drawChar(canvas, font, it.ch, left + it.x, top + it.line * layout.lineHeight, layout.size, paint)
        }
    }

    /**
     * Draws a single glyph into [rect]. With [centerOnInk] the ink is centred (used on keys);
     * otherwise the whole 1x1 drawing box is mapped into the rect (keeps baseline position).
     */
    fun drawGlyphInRect(canvas: Canvas, font: HandFont, glyph: Glyph, rect: RectF, color: Int, centerOnInk: Boolean) {
        val paint = strokePaint(color)
        val size: Float
        val dx: Float
        val dy: Float
        if (centerOnInk) {
            val b = glyph.bounds() ?: return
            size = rect.height() / 0.62f
            val cx = (b.minX + b.maxX) / 2f
            val cy = (b.minY + b.maxY) / 2f
            // Keep tall/short letters relative to each other: centre on the x-height band for small ink.
            val anchorY = if (b.maxY - b.minY < 0.3f && cy > Metrics.X_HEIGHT) (Metrics.CAP + Metrics.BASELINE) / 2f else cy
            dx = rect.centerX() - cx * size
            dy = rect.centerY() - anchorY * size
        } else {
            size = min(rect.width(), rect.height())
            dx = rect.centerX() - size / 2f
            dy = rect.centerY() - size / 2f
        }
        matrix.setScale(size, size)
        matrix.postTranslate(dx, dy)
        tmpPath.reset()
        unitPath(glyph).transform(matrix, tmpPath)
        paint.strokeWidth = max(1.5f, font.strokeWidth * size)
        canvas.drawPath(tmpPath, paint)
    }

    /** Renders text to a picture suitable for sending in a chat. */
    fun renderBitmap(text: String, font: HandFont?, preferredSize: Float, ink: Int, background: Int, maxWidth: Float): Bitmap {
        var size = preferredSize
        var layout: TextLayout
        while (true) {
            val pad = size * 0.28f
            layout = layout(text, font, size, maxWidth - 2 * pad)
            if (layout.height + 2 * pad <= 6000f || size < 30f) break
            size *= 0.8f
        }
        val pad = size * 0.28f
        val w = ceil(max(layout.width, size * 0.5f) + 2 * pad).toInt()
        val h = ceil(layout.height + 2 * pad).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), pad * 0.7f, pad * 0.7f, bgPaint)
        draw(c, layout, font, ink, pad, pad)
        return bmp
    }
}
