package io.github.dtubugk.island.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Choreographer
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import io.github.dtubugk.island.R
import io.github.dtubugk.island.data.IslandColors
import io.github.dtubugk.island.data.IslandConfig
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class BatteryState(val level: Int, val charging: Boolean)

/**
 * Draws the island and runs its motion. The same view is used for the real overlay and for the
 * in-app preview, so what the user sees in the app is exactly what appears on the screen.
 *
 * Every shape is anchored at the island's top edge and horizontal center; geometry (width,
 * height, corner radius) moves on springs that may overshoot, while content only fades, so it
 * never "bounces" in color.
 */
@SuppressLint("ViewConstructor")
class IslandView(
    context: Context,
    /** True for the real overlay: the window is centered on the island. False for the preview. */
    private val isOverlay: Boolean,
) : View(context) {

    enum class Mode { IDLE, CHARGING, EXPANDED }

    interface Host {
        /** The window must be at least this big for the current motion. */
        fun onWindowSizeNeeded(width: Int, height: Int) {}
        fun onOpenSettings() {}
        fun onOpenNotifications() {}
        /** A hidden island must not swallow touches meant for whatever is underneath. */
        fun onShownChanged(shown: Boolean) {}
    }

    var host: Host? = null
    var battery = BatteryState(level = 83, charging = false)
    var clock: () -> LocalDateTime = { LocalDateTime.now() }
    /** Preview only: paint the camera lens, since there is no real hole behind the view. */
    var drawLens = false

    var mode = Mode.IDLE
        private set

    /** Island center in screen pixels; the overlay window is centered on it. */
    val anchorX: Float get() = layout.centerX

    private var screen = ScreenSpec(1080f, 96f, 2.75f, null)
    private var config = IslandConfig()
    private var layout: IslandLayout = IslandGeometry.compute(screen, config, 0f)
    private val dp get() = layout.density

    // --- motion -------------------------------------------------------------------------------
    private val width = Spring(0f, 0.5f)
    private val height = Spring(0f, 0.5f)
    private val radius = Spring(0f, 0.5f)
    private val idleAlpha = Spring(1f, 0.002f)
    private val chargingAlpha = Spring(0f, 0.002f)
    private val expandedAlpha = Spring(0f, 0.002f)
    private val press = Spring(0f, 0.002f)
    private val shown = Spring(1f, 0.002f)
    private val springs = listOf(width, height, radius, idleAlpha, chargingAlpha, expandedAlpha, press, shown)

    /** Host-driven visibility (screen off, landscape); combined with the user's own switch. */
    private var hostVisible = true

    private var frameLoopRunning = false
    private var lastFrameNanos = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = if (lastFrameNanos == 0L) 1f / 60f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 1f / 24f)
            lastFrameNanos = frameTimeNanos
            var moving = false
            for (s in springs) if (s.step(dt)) moving = true
            invalidate()
            if (moving) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                frameLoopRunning = false
                lastFrameNanos = 0L
                requestWindowSize(settled = true)
            }
        }
    }

    private val autoCollapse = Runnable { setMode(Mode.IDLE) }

    // --- paints -------------------------------------------------------------------------------
    private val bold: Typeface = font(R.font.rubik_bold, Typeface.DEFAULT_BOLD)
    private val semibold: Typeface = font(R.font.rubik_semibold, Typeface.DEFAULT_BOLD)
    private val medium: Typeface = font(R.font.rubik_medium, Typeface.DEFAULT)
    private val regular: Typeface = font(R.font.rubik_regular, Typeface.DEFAULT)

    private val islandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clipPath = Path()
    private val boltPath = Path()
    private val rect = RectF()
    private val bounds = Rect()
    private val settingsIcon = ContextCompat.getDrawable(context, R.drawable.ic_island_settings)?.mutate()

    // Snapshot refreshed each time the card opens; the card is only up for a few seconds.
    private var timeText = ""
    private var greetingText = ""
    private var dateText = ""

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onTap(e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            haptic(HapticFeedbackConstants.LONG_PRESS)
            host?.onOpenSettings()
            setMode(Mode.IDLE)
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityY > 600f * dp && abs(velocityY) > abs(velocityX)) {
                pullDown()
                return true
            }
            return false
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val start = e1 ?: return false
            if (e2.y - start.y > 36f * dp) {
                pullDown()
                return true
            }
            return false
        }
    })
    private var pulledDown = false

    init {
        contentDescription = context.getString(R.string.app_name)
        snapTo(Mode.IDLE)
    }

    // --- public API ---------------------------------------------------------------------------

    fun setScreen(spec: ScreenSpec) {
        if (spec == screen) return
        screen = spec
        relayout(animate = false)
    }

    fun setConfig(newConfig: IslandConfig) {
        if (newConfig == config) return
        val old = config
        config = newConfig
        relayout(animate = old.visible == newConfig.visible && isAttachedToWindow)
        updateShown(animate = true)
    }

    fun expand() {
        setMode(Mode.EXPANDED)
    }

    fun showCharging() {
        // Charging is a brief notice: never interrupt a card the user opened on purpose.
        if (mode == Mode.EXPANDED) return
        setMode(Mode.CHARGING)
    }

    fun collapse() {
        setMode(Mode.IDLE)
    }

    /** Fades the whole island in or out, e.g. when the screen rotates to landscape. */
    fun setShown(visible: Boolean, animate: Boolean) {
        hostVisible = visible
        updateShown(animate)
    }

    /** Whether the island is (or is becoming) visible. */
    val isShownTarget: Boolean get() = hostVisible && config.visible

    private fun updateShown(animate: Boolean) {
        val target = if (isShownTarget) 1f else 0f
        if (target != shown.target) host?.onShownChanged(target > 0f)
        if (target == 0f && mode != Mode.IDLE) setMode(Mode.IDLE, animate = false)
        if (!animate || !animationsEnabled()) {
            shown.snapTo(target)
            invalidate()
        } else if (shown.target != target) {
            shown.animateTo(target, stiffness = if (target > 0f) 260f else 700f, dampingRatio = 0.9f)
            startFrameLoop()
        }
    }

    /** Places everything at its resting state for [target] without animating. */
    fun snapTo(target: Mode) {
        mode = target
        if (target == Mode.EXPANDED) refreshSnapshot()
        val shape = shapeFor(target)
        width.snapTo(shape.width)
        height.snapTo(shape.height)
        radius.snapTo(shape.radius)
        idleAlpha.snapTo(if (target == Mode.IDLE) 1f else 0f)
        chargingAlpha.snapTo(if (target == Mode.CHARGING) 1f else 0f)
        expandedAlpha.snapTo(if (target == Mode.EXPANDED) 1f else 0f)
        press.snapTo(0f)
        invalidate()
        requestWindowSize(settled = true)
    }

    /** Current resting window size for the overlay host. */
    fun restingWindowSize(): Pair<Int, Int> = windowSizeFor(animating = false)

    // --- state machine ------------------------------------------------------------------------

    private fun scheduleAutoCollapse(m: Mode) {
        removeCallbacks(autoCollapse)
        when (m) {
            Mode.EXPANDED -> postDelayed(autoCollapse, EXPANDED_DURATION_MS)
            Mode.CHARGING -> postDelayed(autoCollapse, CHARGING_DURATION_MS)
            Mode.IDLE -> Unit
        }
    }

    private fun setMode(target: Mode, animate: Boolean = true) {
        scheduleAutoCollapse(target)
        if (target == mode) return
        if (!animate || !animationsEnabled() || !isAttachedToWindow) {
            snapTo(target)
            return
        }
        val growing = area(shapeFor(target)) >= area(shapeFor(mode))
        mode = target
        if (target == Mode.EXPANDED) refreshSnapshot()

        val shape = shapeFor(target)
        // Opening is soft and bouncy; closing is quicker and settles without wobble.
        val stiffness = if (growing) 260f else 420f
        val damping = if (growing) 0.72f else 0.86f
        width.animateTo(shape.width, stiffness, damping)
        height.animateTo(shape.height, stiffness, damping)
        radius.animateTo(shape.radius, stiffness, 1f)

        // Outgoing content leaves fast; incoming content waits for the shape to open up.
        fun fade(spring: Spring, visible: Boolean) {
            if (visible) spring.animateTo(1f, 320f, 1f, delaySeconds = if (growing) 0.07f else 0.05f)
            else spring.animateTo(0f, 900f, 1f)
        }
        fade(idleAlpha, target == Mode.IDLE)
        fade(chargingAlpha, target == Mode.CHARGING)
        fade(expandedAlpha, target == Mode.EXPANDED)
        requestWindowSize(settled = false)
        startFrameLoop()
    }

    private fun relayout(animate: Boolean) {
        val (text, _) = idleTextSize()
        layout = IslandGeometry.compute(screen, config, text)
        applyGlyphColor()
        if (animate && animationsEnabled()) {
            val shape = shapeFor(mode)
            width.animateTo(shape.width, 520f, 0.8f)
            height.animateTo(shape.height, 520f, 0.8f)
            radius.animateTo(shape.radius, 520f, 1f)
            requestWindowSize(settled = false)
            startFrameLoop()
        } else {
            snapTo(mode)
        }
    }

    private fun shapeFor(m: Mode): IslandShape = when (m) {
        Mode.IDLE -> layout.idle
        Mode.CHARGING -> layout.charging
        Mode.EXPANDED -> layout.expanded
    }

    private fun area(s: IslandShape) = s.width * s.height

    private fun startFrameLoop() {
        if (frameLoopRunning) return
        frameLoopRunning = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun animationsEnabled(): Boolean =
        ValueAnimator.areAnimatorsEnabled() && ValueAnimator.getDurationScale() > 0f

    // --- window sizing ------------------------------------------------------------------------

    private fun windowSizeFor(animating: Boolean): Pair<Int, Int> {
        val target = shapeFor(mode)
        val shadow = SHADOW_MARGIN_DP * dp
        val w: Float
        val h: Float
        if (animating) {
            // Room for the current shape, the target, the spring overshoot and the press squish.
            w = max(width.value, target.width) * 1.1f + 2f * shadow
            h = layout.top + max(height.value, target.height) * 1.1f + shadow
        } else {
            val margin = if (mode == Mode.EXPANDED) shadow else 1f * dp
            w = target.width + 2f * margin
            h = layout.top + target.height + margin
        }
        return min(w, screen.width * 1.2f).roundToInt() to h.roundToInt()
    }

    private fun requestWindowSize(settled: Boolean) {
        if (!isOverlay) return
        val (w, h) = windowSizeFor(animating = !settled)
        host?.onWindowSizeNeeded(w, h)
    }

    // --- touch --------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (shown.target == 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (mode != Mode.IDLE) setMode(Mode.IDLE)
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                if (!hitIsland(event.x, event.y, slop = 8f * dp)) {
                    if (mode != Mode.IDLE) setMode(Mode.IDLE)
                    return false
                }
                pulledDown = false
                removeCallbacks(autoCollapse)
                press.animateTo(1f, 900f, 0.6f)
                requestWindowSize(settled = false)
                startFrameLoop()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                press.animateTo(0f, 600f, 0.55f)
                startFrameLoop()
                // A touch pauses the timer; a tap below may still change the mode and re-arm it.
                scheduleAutoCollapse(mode)
            }
        }
        gestures.onTouchEvent(event)
        return true
    }

    private fun onTap(x: Float, y: Float) {
        when (mode) {
            Mode.IDLE, Mode.CHARGING -> {
                if (!config.expandOnTap) return
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                setMode(Mode.EXPANDED)
            }
            Mode.EXPANDED -> {
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                val (gx, gy) = gearCenter()
                val r = GEAR_SIZE_DP * dp
                if (abs(x - gx) <= r && abs(y - gy) <= r) host?.onOpenSettings()
                setMode(Mode.IDLE)
            }
        }
    }

    private fun pullDown() {
        if (pulledDown) return
        pulledDown = true
        host?.onOpenNotifications()
        setMode(Mode.IDLE)
    }

    private fun hitIsland(x: Float, y: Float, slop: Float): Boolean {
        val cx = centerX()
        return abs(x - cx) <= width.value / 2f + slop && y >= layout.top - slop && y <= layout.top + height.value + slop
    }

    private fun haptic(type: Int) {
        if (config.haptics) performHapticFeedback(type)
    }

    // --- drawing ------------------------------------------------------------------------------

    private fun centerX(): Float = if (isOverlay) getWidth() / 2f else layout.centerX

    override fun onDraw(canvas: Canvas) {
        val visibility = shown.value.coerceIn(0f, 1f)
        if (visibility <= 0.001f) return
        val cx = centerX()
        val top = layout.top
        val squish = 1f + 0.05f * press.value
        val appear = 0.55f + 0.45f * visibility
        val w = max(width.value * squish * appear, 1f)
        val h = max(height.value * (1f + 0.07f * press.value) * appear, 1f)
        val r = min(radius.value * appear, h / 2f)
        val left = cx - w / 2f

        canvas.save()
        if (visibility < 1f) canvas.saveLayerAlpha(null, (visibility * 255).roundToInt())

        // Soft shadow only for the card; the resting island is pure black like hardware.
        val shadow = expandedAlpha.value.coerceIn(0f, 1f)
        if (shadow > 0.01f) {
            islandPaint.setShadowLayer(18f * dp, 0f, 6f * dp, Color.argb((shadow * 90).roundToInt(), 0, 0, 0))
        } else {
            islandPaint.clearShadowLayer()
        }
        rect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, r, r, islandPaint)

        if (drawLens) drawLens(canvas, cx + layout.holeOffsetX, top + layout.holeCenterY)

        clipPath.rewind()
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val idle = idleAlpha.value.coerceIn(0f, 1f)
        if (idle > 0.01f) drawIdle(canvas, cx, top, w, h, idle)
        val charging = chargingAlpha.value.coerceIn(0f, 1f)
        if (charging > 0.01f) drawCharging(canvas, cx, top, w, charging)
        val expanded = expandedAlpha.value.coerceIn(0f, 1f)
        if (expanded > 0.01f) drawExpanded(canvas, cx, top, w, expanded)

        if (visibility < 1f) canvas.restore()
        canvas.restore()
    }

    private fun drawLens(canvas: Canvas, x: Float, y: Float) {
        val r = max(layout.holeRadius, 5f * dp)
        lensPaint.shader = null
        lensPaint.color = 0xFF16161C.toInt()
        canvas.drawCircle(x, y, r, lensPaint)
        lensPaint.color = 0xFF232334.toInt()
        canvas.drawCircle(x, y, r * 0.62f, lensPaint)
        lensPaint.color = 0x553A4A7A
        canvas.drawCircle(x - r * 0.22f, y - r * 0.22f, r * 0.2f, lensPaint)
    }

    // Idle: the text rides on its side of the island, so it slides out with the edge as it grows.
    private fun drawIdle(canvas: Canvas, cx: Float, top: Float, w: Float, h: Float, alpha: Float) {
        val text = config.text
        if (text.isEmpty()) return
        val (textWidth, size) = idleTextSize()
        textPaint.textSize = size * (0.9f + 0.1f * alpha)
        textPaint.textAlign = Paint.Align.CENTER
        val restHalf = layout.idle.width / 2f
        val fromEdge = restHalf - abs(layout.textCenterX)
        val side = if (layout.textCenterX >= 0f) 1f else -1f
        val x = cx + side * (w / 2f - fromEdge)
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val y = top + h / 2f - (bounds.top + bounds.bottom) / 2f
        applyGlyphShader(x, textWidth)
        textPaint.alpha = (alpha * 255).roundToInt()
        canvas.drawText(text, x, y, textPaint)
        textPaint.shader = null
    }

    private fun drawCharging(canvas: Canvas, cx: Float, top: Float, w: Float, alpha: Float) {
        val s = layout.charging
        val scale = (w / s.width).coerceIn(0.5f, 1.1f) * (0.94f + 0.06f * alpha)
        val a = (alpha * 255).roundToInt()
        canvas.save()
        canvas.scale(scale, scale, cx, top + s.height / 2f)
        val left = cx - s.width / 2f
        val right = cx + s.width / 2f
        val mid = top + s.height / 2f
        val inset = s.height * 0.5f

        // RTL: the label reads first on the right, the battery sits at the far left.
        labelPaint.typeface = medium
        labelPaint.textSize = s.height * 0.44f
        labelPaint.color = Color.WHITE
        labelPaint.alpha = a
        labelPaint.textAlign = Paint.Align.RIGHT
        drawCentered(canvas, CHARGING_LABEL, right - inset, mid, labelPaint)

        val bw = s.height * 0.86f
        val bh = s.height * 0.42f
        val batteryLeft = left + inset
        drawBattery(canvas, batteryLeft, mid - bh / 2f, bw, bh, battery.level, CHARGING_GREEN, bolt = true, alpha = a)
        labelPaint.typeface = semibold
        labelPaint.color = CHARGING_GREEN
        labelPaint.alpha = a
        labelPaint.textAlign = Paint.Align.LEFT
        drawCentered(canvas, "${battery.level}%", batteryLeft + bw + s.height * 0.3f, mid, labelPaint)
        canvas.restore()
    }

    private fun drawExpanded(canvas: Canvas, cx: Float, top: Float, w: Float, alpha: Float) {
        val s = layout.expanded
        val scale = (w / s.width).coerceIn(0.4f, 1.1f) * (0.94f + 0.06f * alpha)
        val a = (alpha * 255).roundToInt()
        canvas.save()
        canvas.scale(scale, scale, cx, top)
        val left = cx - s.width / 2f
        val right = cx + s.width / 2f
        val band = top + layout.holeCenterY.coerceIn(layout.bandHeight * 0.3f, layout.bandHeight * 0.7f)
        val sideInset = 24f * dp

        // Top band mirrors the status bar the card is covering: clock on the right, battery on the left.
        labelPaint.typeface = semibold
        labelPaint.textSize = 15f * dp
        labelPaint.color = Color.WHITE
        labelPaint.alpha = a
        labelPaint.textAlign = Paint.Align.RIGHT
        drawCentered(canvas, timeText, right - sideInset, band, labelPaint)

        val bw = 22f * dp
        val bh = 11f * dp
        val level = battery.level
        val batteryColor = when {
            battery.charging -> CHARGING_GREEN
            level <= 15 -> LOW_RED
            else -> Color.WHITE
        }
        drawBattery(canvas, left + sideInset, band - bh / 2f, bw, bh, level, batteryColor, bolt = battery.charging, alpha = a)
        labelPaint.typeface = medium
        labelPaint.textSize = 13f * dp
        labelPaint.color = if (batteryColor == Color.WHITE) Color.WHITE else batteryColor
        labelPaint.alpha = a
        labelPaint.textAlign = Paint.Align.LEFT
        drawCentered(canvas, "$level%", left + sideInset + bw + 6f * dp, band, labelPaint)

        // Main row: avatar with the island text, greeting and date, settings button.
        val rowMid = top + layout.bandHeight + ROW_DP * dp / 2f
        val tile = TILE_DP * dp
        val tileRight = right - 16f * dp
        val tileLeft = tileRight - tile
        rect.set(tileLeft, rowMid - tile / 2f, tileRight, rowMid + tile / 2f)
        fillPaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, IslandColors.GRADIENT_START, IslandColors.GRADIENT_END, Shader.TileMode.CLAMP)
        fillPaint.alpha = a
        canvas.drawRoundRect(rect, 16f * dp, 16f * dp, fillPaint)
        fillPaint.shader = null

        val avatar = config.text.ifBlank { IslandConfig.DEFAULT_TEXT }
        textPaint.shader = null
        textPaint.color = Color.WHITE
        textPaint.alpha = a
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 28f * dp
        val maxAvatar = tile - 14f * dp
        val measured = textPaint.measureText(avatar)
        if (measured > maxAvatar) textPaint.textSize *= maxAvatar / measured
        drawCentered(canvas, avatar, rect.centerX(), rect.centerY(), textPaint)
        applyGlyphColor()

        val (gx, gy) = gearCenter()
        val gear = GEAR_SIZE_DP * dp
        fillPaint.color = 0x24FFFFFF
        fillPaint.alpha = (0x24 * alpha).roundToInt()
        canvas.drawCircle(gx, gy, gear / 2f, fillPaint)
        settingsIcon?.let {
            val icon = (20f * dp).roundToInt()
            it.setBounds((gx - icon / 2f).roundToInt(), (gy - icon / 2f).roundToInt(), (gx + icon / 2f).roundToInt(), (gy + icon / 2f).roundToInt())
            it.alpha = (alpha * 230).roundToInt()
            it.draw(canvas)
        }

        val textRight = tileLeft - 14f * dp
        val textLeft = gx + gear / 2f + 12f * dp
        val room = max(textRight - textLeft, 0f)
        labelPaint.typeface = semibold
        labelPaint.textSize = 17f * dp
        labelPaint.color = Color.WHITE
        labelPaint.alpha = a
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(ellipsize(greetingText, room), textRight, rowMid - 4f * dp, labelPaint)
        labelPaint.typeface = regular
        labelPaint.textSize = 13.5f * dp
        labelPaint.alpha = (alpha * 160).roundToInt()
        canvas.drawText(ellipsize(dateText, room), textRight, rowMid + 16f * dp, labelPaint)
        canvas.restore()
    }

    private fun gearCenter(): Pair<Float, Float> {
        val s = layout.expanded
        val left = centerX() - s.width / 2f
        return left + 16f * dp + GEAR_SIZE_DP * dp / 2f to layout.top + layout.bandHeight + ROW_DP * dp / 2f
    }

    private fun drawBattery(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, level: Int, color: Int, bolt: Boolean, alpha: Int) {
        val capW = h * 0.14f
        val bodyW = w - capW - h * 0.08f
        val r = h * 0.3f
        strokePaint.strokeWidth = max(1f, h * 0.09f)
        strokePaint.color = Color.WHITE
        strokePaint.alpha = (alpha * 0.4f).roundToInt()
        val half = strokePaint.strokeWidth / 2f
        rect.set(x + half, y + half, x + bodyW - half, y + h - half)
        canvas.drawRoundRect(rect, r, r, strokePaint)
        fillPaint.shader = null
        fillPaint.color = Color.WHITE
        fillPaint.alpha = (alpha * 0.4f).roundToInt()
        rect.set(x + bodyW + h * 0.08f, y + h * 0.32f, x + w, y + h * 0.68f)
        canvas.drawRoundRect(rect, capW / 2f, capW / 2f, fillPaint)

        val pad = strokePaint.strokeWidth + h * 0.06f
        val fillMax = bodyW - 2f * pad
        val fillW = fillMax * (level.coerceIn(0, 100) / 100f)
        fillPaint.color = color
        fillPaint.alpha = alpha
        rect.set(x + pad, y + pad, x + pad + max(fillW, h * 0.12f), y + h - pad)
        canvas.drawRoundRect(rect, r * 0.6f, r * 0.6f, fillPaint)

        if (bolt) {
            val cx = x + bodyW / 2f
            val cy = y + h / 2f
            val bh = h * 0.78f
            val bw = bh * 0.55f
            boltPath.rewind()
            boltPath.moveTo(cx + bw * 0.12f, cy - bh / 2f)
            boltPath.lineTo(cx - bw / 2f, cy + bh * 0.08f)
            boltPath.lineTo(cx - bw * 0.02f, cy + bh * 0.08f)
            boltPath.lineTo(cx - bw * 0.12f, cy + bh / 2f)
            boltPath.lineTo(cx + bw / 2f, cy - bh * 0.08f)
            boltPath.lineTo(cx + bw * 0.02f, cy - bh * 0.08f)
            boltPath.close()
            fillPaint.color = Color.BLACK
            fillPaint.alpha = alpha
            canvas.drawPath(boltPath, fillPaint)
        }
    }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, centerY: Float, paint: Paint) {
        val fm = paint.fontMetrics
        canvas.drawText(text, x, centerY - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun ellipsize(text: String, room: Float): String =
        TextUtils.ellipsize(text, labelPaint, room, TextUtils.TruncateAt.END).toString()

    // --- text helpers -------------------------------------------------------------------------

    /** Width and size of the idle text, sized from the island height so it scales with it. */
    private fun idleTextSize(): Pair<Float, Float> {
        val h = max(config.heightDp.coerceIn(IslandConfig.MIN_HEIGHT_DP, IslandConfig.MAX_HEIGHT_DP) * screen.density, (screen.hole?.diameter ?: 0f) + 6f * screen.density)
        val size = h * 0.56f
        textPaint.typeface = bold
        textPaint.textSize = size
        val w = if (config.text.isEmpty()) 0f else textPaint.measureText(config.text)
        return w to size
    }

    private fun applyGlyphColor() {
        val c = IslandColors.of(config.colorIndex)
        textPaint.color = if (c == IslandColors.GRADIENT) Color.WHITE else c
    }

    private fun applyGlyphShader(centerX: Float, textWidth: Float) {
        applyGlyphColor()
        if (IslandColors.of(config.colorIndex) != IslandColors.GRADIENT) return
        val half = max(textWidth / 2f, 4f * dp)
        textPaint.shader = LinearGradient(centerX + half, 0f, centerX - half, 0f, IslandColors.GRADIENT_START, IslandColors.GRADIENT_END, Shader.TileMode.CLAMP)
    }

    private fun refreshSnapshot() {
        val now = clock()
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        timeText = now.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
        greetingText = when (now.hour) {
            in 5..11 -> "בוקר טוב"
            in 12..16 -> "צהריים טובים"
            in 17..20 -> "ערב טוב"
            else -> "לילה טוב"
        }
        dateText = now.format(DateTimeFormatter.ofPattern("EEEE, d 'ב'MMMM", HEBREW))
    }

    private fun font(id: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isOverlay || w == 0) return
        // The preview is a miniature phone top: camera centered, typical Galaxy proportions.
        val d = resources.displayMetrics.density
        setScreen(ScreenSpec(w.toFloat(), 36f * d, d, Hole(w / 2f, PREVIEW_HOLE_Y_DP * d, PREVIEW_HOLE_DP * d)))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(autoCollapse)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameLoopRunning = false
        lastFrameNanos = 0L
    }

    companion object {
        const val EXPANDED_DURATION_MS = 6000L
        const val CHARGING_DURATION_MS = 3200L
        private const val SHADOW_MARGIN_DP = 22f
        private const val ROW_DP = 64f
        private const val TILE_DP = 50f
        private const val GEAR_SIZE_DP = 40f
        private const val CHARGING_LABEL = "טוען"
        const val PREVIEW_HOLE_Y_DP = 18f
        const val PREVIEW_HOLE_DP = 16f
        private val CHARGING_GREEN = 0xFF34C759.toInt()
        private val LOW_RED = 0xFFFF453A.toInt()
        private val HEBREW: Locale = Locale.forLanguageTag("he-IL")
    }
}
