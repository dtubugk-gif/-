package io.github.dtubugk.island.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import io.github.dtubugk.island.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Paints, fonts, icons and small drawing routines shared by every island scene. */
class IslandPainter(private val context: Context) {
    val bold: Typeface = font(R.font.rubik_bold, Typeface.DEFAULT_BOLD)
    val semibold: Typeface = font(R.font.rubik_semibold, Typeface.DEFAULT_BOLD)
    val medium: Typeface = font(R.font.rubik_medium, Typeface.DEFAULT)
    val regular: Typeface = font(R.font.rubik_regular, Typeface.DEFAULT)

    val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val matrix = Matrix()
    private val icons = HashMap<Int, Drawable?>()

    /** Draws [value] vertically centered on [centerY]. Returns its width. */
    fun label(
        canvas: Canvas,
        value: String,
        x: Float,
        centerY: Float,
        size: Float,
        color: Int,
        alpha: Float,
        align: Paint.Align,
        face: Typeface = medium,
        maxWidth: Float = Float.MAX_VALUE,
    ): Float {
        if (value.isEmpty()) return 0f
        text.shader = null
        text.typeface = face
        text.textSize = size
        text.textAlign = align
        text.color = color
        text.alpha = (Color.alpha(color) * alpha).roundToInt()
        val shown = if (maxWidth < Float.MAX_VALUE) ellipsize(value, maxWidth) else value
        val fm = text.fontMetrics
        canvas.drawText(shown, x, centerY - (fm.ascent + fm.descent) / 2f, text)
        return text.measureText(shown)
    }

    fun measure(value: String, size: Float, face: Typeface = medium): Float {
        text.typeface = face
        text.textSize = size
        return text.measureText(value)
    }

    fun ellipsize(value: String, room: Float): String =
        TextUtils.ellipsize(value, text, max(room, 0f), TextUtils.TruncateAt.END).toString()

    fun icon(canvas: Canvas, res: Int, cx: Float, cy: Float, size: Float, color: Int, alpha: Float) {
        val d = icons.getOrPut(res) { ContextCompat.getDrawable(context, res)?.mutate() } ?: return
        val half = size / 2f
        d.setBounds((cx - half).roundToInt(), (cy - half).roundToInt(), (cx + half).roundToInt(), (cy + half).roundToInt())
        d.setTint(color)
        d.alpha = (Color.alpha(color) * alpha).roundToInt()
        d.draw(canvas)
    }

    /** An arbitrary drawable (app icon, contact photo) clipped to a circle. */
    fun roundDrawable(canvas: Canvas, d: Drawable, cx: Float, cy: Float, size: Float, alpha: Float) {
        val half = size / 2f
        canvas.save()
        path.rewind()
        path.addCircle(cx, cy, half, Path.Direction.CW)
        canvas.clipPath(path)
        d.setBounds((cx - half).roundToInt(), (cy - half).roundToInt(), (cx + half).roundToInt(), (cy + half).roundToInt())
        d.alpha = (alpha * 255).roundToInt()
        d.draw(canvas)
        canvas.restore()
    }

    fun circle(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
        fill.shader = null
        fill.color = color
        fill.alpha = (Color.alpha(color) * alpha).roundToInt()
        canvas.drawCircle(cx, cy, radius, fill)
    }

    fun roundRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, alpha: Float) {
        fill.shader = null
        fill.color = color
        fill.alpha = (Color.alpha(color) * alpha).roundToInt()
        rect.set(l, t, r, b)
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    /** Album art as a rounded square, center-cropped; a music note on a tile when there is none. */
    fun artwork(canvas: Canvas, bitmap: Bitmap?, cx: Float, cy: Float, size: Float, radius: Float, accent: Int, alpha: Float) {
        val half = size / 2f
        rect.set(cx - half, cy - half, cx + half, cy + half)
        if (bitmap == null || bitmap.isRecycled) {
            roundRect(canvas, rect.left, rect.top, rect.right, rect.bottom, radius, blend(accent, Color.BLACK, 0.45f), alpha)
            icon(canvas, R.drawable.ic_music, cx, cy, size * 0.55f, Color.WHITE, alpha)
            return
        }
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = size / min(bitmap.width, bitmap.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate(cx - bitmap.width * scale / 2f, cy - bitmap.height * scale / 2f)
        shader.setLocalMatrix(matrix)
        art.shader = shader
        art.alpha = (alpha * 255).roundToInt()
        canvas.drawRoundRect(rect, radius, radius, art)
        art.shader = null
    }

    /**
     * The iPhone-style "now playing" bars. Each bar breathes on its own pair of sine waves so the
     * motion never visibly loops; paused bars rest as dots.
     */
    fun waveform(canvas: Canvas, cx: Float, cy: Float, width: Float, height: Float, color: Int, playing: Boolean, timeMs: Long, alpha: Float) {
        val bars = 5
        val gap = width / (bars * 2f - 1f)
        val barW = gap
        val t = timeMs / 1000f
        fill.shader = null
        fill.color = color
        fill.alpha = (Color.alpha(color) * alpha).roundToInt()
        for (i in 0 until bars) {
            val level = if (playing) {
                val a = abs(sin(t * (5.1f + i * 1.7f) + i * 1.3f))
                val b = abs(sin(t * (2.3f + i * 0.9f) + i * 2.1f))
                0.25f + 0.75f * (0.6f * a + 0.4f * b)
            } else {
                0f
            }
            val h = max(barW, height * level)
            val x = cx - width / 2f + i * 2f * gap
            rect.set(x, cy - h / 2f, x + barW, cy + h / 2f)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, fill)
        }
    }

    private val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }

    /**
     * One line of text between [left] and [right]. If it fits it sits against the right edge
     * (Hebrew reads from there); otherwise it scrolls in its reading direction with soft edges.
     */
    fun marquee(canvas: Canvas, value: String, left: Float, right: Float, centerY: Float, size: Float, color: Int, alpha: Float, timeMs: Long) {
        if (value.isBlank() || right <= left) return
        val width = measure(value, size, medium)
        val room = right - left
        if (width <= room) {
            label(canvas, value, right, centerY, size, color, alpha, Paint.Align.RIGHT, medium)
            return
        }
        val edge = size * 0.9f
        val layer = canvas.saveLayer(left, centerY - size, right, centerY + size, null)
        val cycle = width + size * 2.5f
        val phase = (timeMs * size * 1.6f / 1000f) % cycle
        val rtl = value.firstOrNull { Character.isLetter(it) }?.let {
            Character.getDirectionality(it) == Character.DIRECTIONALITY_RIGHT_TO_LEFT
        } ?: false
        if (rtl) {
            label(canvas, value, right + phase, centerY, size, color, alpha, Paint.Align.RIGHT, medium)
            label(canvas, value, right + phase - cycle, centerY, size, color, alpha, Paint.Align.RIGHT, medium)
        } else {
            label(canvas, value, left - phase, centerY, size, color, alpha, Paint.Align.LEFT, medium)
            label(canvas, value, left - phase + cycle, centerY, size, color, alpha, Paint.Align.LEFT, medium)
        }
        fade.shader = LinearGradient(left, 0f, left + edge, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(left, centerY - size, left + edge, centerY + size, fade)
        fade.shader = LinearGradient(right - edge, 0f, right, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(right - edge, centerY - size, right, centerY + size, fade)
        canvas.restoreToCount(layer)
    }

    fun progress(canvas: Canvas, left: Float, right: Float, cy: Float, height: Float, fraction: Float, alpha: Float) {
        roundRect(canvas, left, cy - height / 2f, right, cy + height / 2f, height / 2f, 0x40FFFFFF, alpha)
        val end = left + (right - left) * fraction.coerceIn(0f, 1f)
        if (end > left + height) roundRect(canvas, left, cy - height / 2f, end, cy + height / 2f, height / 2f, Color.WHITE, alpha)
    }

    fun pill(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, background: Int, label: String, labelColor: Int, size: Float, alpha: Float) {
        roundRect(canvas, l, t, r, b, (b - t) / 2f, background, alpha)
        label(canvas, label, (l + r) / 2f, (t + b) / 2f, size, labelColor, alpha, Paint.Align.CENTER, semibold, maxWidth = r - l - size)
    }

    fun battery(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, level: Int, color: Int, bolt: Boolean, alpha: Float) {
        val a = (alpha * 255).roundToInt()
        val capW = h * 0.14f
        val bodyW = w - capW - h * 0.08f
        val r = h * 0.3f
        stroke.strokeWidth = max(1f, h * 0.09f)
        stroke.color = Color.WHITE
        stroke.alpha = (a * 0.4f).roundToInt()
        val half = stroke.strokeWidth / 2f
        rect.set(x + half, y + half, x + bodyW - half, y + h - half)
        canvas.drawRoundRect(rect, r, r, stroke)
        roundRect(canvas, x + bodyW + h * 0.08f, y + h * 0.32f, x + w, y + h * 0.68f, capW / 2f, 0x66FFFFFF, alpha)

        val pad = stroke.strokeWidth + h * 0.06f
        val fillW = (bodyW - 2f * pad) * (level.coerceIn(0, 100) / 100f)
        roundRect(canvas, x + pad, y + pad, x + pad + max(fillW, h * 0.12f), y + h - pad, r * 0.6f, color, alpha)

        if (bolt) {
            val cx = x + bodyW / 2f
            val cy = y + h / 2f
            val bh = h * 0.78f
            val bw = bh * 0.55f
            path.rewind()
            path.moveTo(cx + bw * 0.12f, cy - bh / 2f)
            path.lineTo(cx - bw / 2f, cy + bh * 0.08f)
            path.lineTo(cx - bw * 0.02f, cy + bh * 0.08f)
            path.lineTo(cx - bw * 0.12f, cy + bh / 2f)
            path.lineTo(cx + bw / 2f, cy - bh * 0.08f)
            path.lineTo(cx + bw * 0.02f, cy - bh * 0.08f)
            path.close()
            fill.shader = null
            fill.color = Color.BLACK
            fill.alpha = a
            canvas.drawPath(path, fill)
        }
    }

    private fun font(id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback

    companion object {
        const val GREEN = 0xFF34C759.toInt()
        const val RED = 0xFFFF453A.toInt()
        const val ORANGE = 0xFFFF9F0A.toInt()
        const val PURPLE = 0xFFA78BFA.toInt()
        const val BLUE = 0xFF64A8FF.toInt()
        const val SECONDARY = 0x99FFFFFF.toInt()

        fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
        )

        /** "3:07" / "1:02:45", never negative. */
        fun duration(ms: Long): String {
            val total = (max(ms, 0L) / 1000L)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
