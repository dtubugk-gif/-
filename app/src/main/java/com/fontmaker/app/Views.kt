package com.fontmaker.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

object Ui {
    const val PRIMARY = 0xFF5B3CC4.toInt()
    const val PRIMARY_SOFT = 0xFFECE7FB.toInt()
    const val TEXT = 0xFF1F1B2E.toInt()
    const val TEXT_SOFT = 0xFF6B6680.toInt()
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val BG = 0xFFF6F4FB.toInt()
    const val SUCCESS = 0xFF188038.toInt()

    val INKS = intArrayOf(
        0xFF111111.toInt(), 0xFF1A56DB.toInt(), 0xFFD93025.toInt(),
        0xFF8E24AA.toInt(), 0xFF188038.toInt(), 0xFFE91E63.toInt(),
    )

    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density
    fun dpi(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(color: Int, radius: Float, strokeColor: Int = 0, strokeW: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeW > 0) setStroke(strokeW, strokeColor)
    }

    fun button(ctx: Context, text: String, primary: Boolean = true, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) Color.WHITE else PRIMARY)
            val bg = rounded(if (primary) PRIMARY else PRIMARY_SOFT, dp(ctx, 14f))
            background = RippleDrawable(ColorStateList.valueOf(0x33000000), bg, null)
            setPadding(dpi(ctx, 16), dpi(ctx, 14), dpi(ctx, 16), dpi(ctx, 14))
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun label(ctx: Context, text: String, sizeSp: Float = 15f, color: Int = TEXT, bold: Boolean = false) =
        TextView(ctx).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
}

/** Shows text written in a hand-made font. Optionally shows a blinking cursor and auto-shrinks. */
class GlyphTextView(context: Context) : View(context) {
    var font: HandFont? = null
        set(v) { field = v; requestLayout(); invalidate() }
    var text: String = ""
        set(v) { field = v; requestLayout(); invalidate() }
    var textSizePx: Float = Ui.dp(context, 36f)
        set(v) { field = v; requestLayout(); invalidate() }
    var minTextSizePx: Float = 0f
    var inkColor: Int = Color.BLACK
        set(v) { field = v; invalidate() }
    var hint: String = ""
    var showCursor = false
    var centerVertically = false

    private var cursorOn = true
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AA0A6.toInt()
        textSize = Ui.dp(context, 15f)
    }
    private val cursorPaint = Paint().apply { color = Ui.PRIMARY }
    private val blink = object : Runnable {
        override fun run() {
            cursorOn = !cursorOn
            invalidate()
            postDelayed(this, 530)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (showCursor) postDelayed(blink, 530)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val h = if (hMode == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            val avail = max(1, w - paddingLeft - paddingRight).toFloat()
            val lay = GlyphRenderer.layout(text, font, textSizePx, avail)
            val content = max(lay.height, textSizePx * GlyphRenderer.LINE_HEIGHT)
            val want = (content + paddingTop + paddingBottom).toInt()
            if (hMode == MeasureSpec.AT_MOST) min(want, MeasureSpec.getSize(heightMeasureSpec)) else want
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        if (availW <= 0 || availH <= 0) return
        var size = textSizePx
        var lay = GlyphRenderer.layout(text, font, size, availW)
        if (minTextSizePx > 0f) {
            while (lay.height > availH && size > minTextSizePx) {
                size = max(minTextSizePx, size * 0.88f)
                lay = GlyphRenderer.layout(text, font, size, availW)
            }
        }
        val offY = when {
            lay.height > availH -> availH - lay.height // keep the end (where you type) visible
            centerVertically -> (availH - lay.height) / 2f
            else -> 0f
        }
        canvas.save()
        canvas.clipRect(paddingLeft.toFloat(), paddingTop.toFloat(), width - paddingRight.toFloat(), height - paddingBottom.toFloat())
        val top = paddingTop + offY
        if (text.isEmpty() && hint.isNotEmpty()) {
            val y = top + lay.lineHeight / 2f - (hintPaint.ascent() + hintPaint.descent()) / 2f
            canvas.drawText(hint, paddingLeft + Ui.dp(context, 8f), y, hintPaint)
        }
        GlyphRenderer.draw(canvas, lay, font, inkColor, paddingLeft.toFloat(), top)
        if (showCursor && cursorOn) {
            val x = paddingLeft + lay.endX + Ui.dp(context, 1f)
            val lineTop = top + (lay.lineCount - 1) * lay.lineHeight
            canvas.drawRect(x, lineTop + size * 0.08f, x + Ui.dp(context, 2f), lineTop + size * 0.72f, cursorPaint)
        }
        canvas.restore()
    }
}

/** The square you draw a character in. */
class DrawCanvasView(context: Context) : View(context) {
    var font: HandFont? = null
        set(v) { field = v; invalidate() }
    var glyph: Glyph = Glyph()
        set(v) { field = v; undoStack.clear(); invalidate() }
    var refChar: String = "a"
        set(v) { field = v; invalidate() }
    var onChanged: (() -> Unit)? = null

    private val undoStack = ArrayDeque<Glyph>()
    private var current: Stroke? = null
    private val box = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFDAD4EE.toInt(); strokeWidth = Ui.dp(context, 1.5f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFFC9C1E6.toInt(); strokeWidth = Ui.dp(context, 1.2f)
    }
    private val dashPaint = Paint(guidePaint).apply {
        pathEffect = DashPathEffect(floatArrayOf(Ui.dp(context, 6f), Ui.dp(context, 6f)), 0f)
    }
    private val baselinePaint = Paint(guidePaint).apply { color = 0xFF9C8FD6.toInt(); strokeWidth = Ui.dp(context, 2f) }
    private val guideText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB0A6D6.toInt(); textSize = Ui.dp(context, 10f) }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEDEAF5.toInt(); typeface = Typeface.DEFAULT }
    private val inkPaint = GlyphRenderer.strokePaint(Color.BLACK)
    private val path = Path()
    private val m = Matrix()
    private val tmpRect = android.graphics.Rect()

    val canUndo get() = undoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        glyph.strokes.clear()
        glyph.strokes.addAll(prev.strokes)
        invalidate(); onChanged?.invoke()
    }

    fun clear() {
        if (glyph.isEmpty) return
        undoStack.addLast(glyph.copy())
        glyph.strokes.clear()
        invalidate(); onChanged?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        val left = paddingLeft + (width - paddingLeft - paddingRight - side) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - side) / 2f
        box.set(left, top, left + side, top + side)
        val r = Ui.dp(context, 18f)
        canvas.drawRoundRect(box, r, r, bgPaint)
        canvas.drawRoundRect(box, r, r, borderPaint)

        // Ghost reference letter, scaled so its cap height matches the guides.
        ghostPaint.textSize = side * 0.7f
        ghostPaint.getTextBounds("H", 0, 1, tmpRect)
        if (tmpRect.height() > 0) ghostPaint.textSize *= ((Metrics.BASELINE - Metrics.CAP) * side) / tmpRect.height()
        val gw = ghostPaint.measureText(refChar)
        canvas.drawText(refChar, left + (side - gw) / 2f, top + Metrics.BASELINE * side, ghostPaint)

        fun hLine(y: Float, p: Paint, name: String) {
            val yy = top + y * side
            canvas.drawLine(left + r / 2, yy, left + side - r / 2, yy, p)
            canvas.drawText(name, left + Ui.dp(context, 8f), yy - Ui.dp(context, 4f), guideText)
        }
        hLine(Metrics.CAP, dashPaint, "top")
        hLine(Metrics.X_HEIGHT, dashPaint, "middle")
        hLine(Metrics.BASELINE, baselinePaint, "baseline")
        hLine(Metrics.DESCENDER, dashPaint, "tail")

        m.setScale(side, side)
        m.postTranslate(left, top)
        path.reset()
        GlyphRenderer.unitPath(glyph).transform(m, path)
        inkPaint.strokeWidth = (font?.strokeWidth ?: Metrics.DEFAULT_STROKE) * side
        canvas.drawPath(path, inkPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (box.width() <= 0f) return false
        fun pt(x: Float, y: Float) = Pt(
            ((x - box.left) / box.width()).coerceIn(-0.02f, 1.02f),
            ((y - box.top) / box.height()).coerceIn(-0.02f, 1.02f),
        )
        fun add(p: Pt) {
            val s = current ?: return
            val last = s.points.lastOrNull()
            if (last == null || kotlin.math.hypot(p.x - last.x, p.y - last.y) > 0.002f) s.points.add(p)
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                undoStack.addLast(glyph.copy())
                if (undoStack.size > 60) undoStack.removeFirst()
                current = Stroke().also { glyph.strokes.add(it) }
                add(pt(e.x, e.y))
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until e.historySize) add(pt(e.getHistoricalX(h), e.getHistoricalY(h)))
                add(pt(e.x, e.y))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                add(pt(e.x, e.y))
                current?.let { s ->
                    val simple = Geometry.simplify(s.points, 0.0015f)
                    s.points.clear(); s.points.addAll(simple)
                }
                current = null
                onChanged?.invoke()
            }
        }
        invalidate()
        return true
    }
}

/** One tile in the character grid. */
class GlyphCellView(context: Context) : View(context) {
    var font: HandFont? = null
    var ch: String = "a"
        set(v) { field = v; invalidate() }
    private val rect = RectF()
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9C1E6.toInt(); textAlign = Paint.Align.CENTER }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT_SOFT; textSize = Ui.dp(context, 10f) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.SUCCESS }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        val inset = Ui.dp(context, 3f)
        rect.set(inset, inset, width - inset, height - inset)
        val g = font?.glyph(ch)
        bg.color = if (g != null) Color.WHITE else 0xFFF1EEF9.toInt()
        canvas.drawRoundRect(rect, Ui.dp(context, 12f), Ui.dp(context, 12f), bg)
        if (g != null) {
            val f = font!!
            val r = RectF(rect).apply { inset(Ui.dp(context, 4f), Ui.dp(context, 4f)) }
            GlyphRenderer.drawGlyphInRect(canvas, f, g, r, Color.BLACK, centerOnInk = false)
            canvas.drawCircle(rect.right - Ui.dp(context, 8f), rect.top + Ui.dp(context, 8f), Ui.dp(context, 3f), dot)
        } else {
            placeholder.textSize = rect.height() * 0.5f
            canvas.drawText(ch, rect.centerX(), rect.centerY() - (placeholder.ascent() + placeholder.descent()) / 2f, placeholder)
        }
        canvas.drawText(ch, rect.left + Ui.dp(context, 6f), rect.bottom - Ui.dp(context, 6f), labelPaint)
    }
}
