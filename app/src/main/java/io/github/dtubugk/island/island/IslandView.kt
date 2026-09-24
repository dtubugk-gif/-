package io.github.dtubugk.island.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.Choreographer
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import io.github.dtubugk.island.R
import io.github.dtubugk.island.data.IslandConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class BatteryState(val level: Int, val charging: Boolean)

/**
 * Draws the island and runs its motion. The same view backs the real overlay and the in-app
 * preview, so the preview is exactly what appears on screen.
 *
 * Shape (width, height, corner radius) moves on springs that may overshoot; content only fades,
 * so colors never "bounce". Which [Scene] to show is decided by [IslandDirector].
 */
@SuppressLint("ViewConstructor")
class IslandView(
    context: Context,
    /** True for the real overlay: the window is centered on the island. False for the preview. */
    private val isOverlay: Boolean,
) : View(context) {

    interface Host {
        /** The window must be at least this big for the current motion. */
        fun onWindowSizeNeeded(width: Int, height: Int) {}
        /** A hidden island must not swallow touches meant for whatever is underneath. */
        fun onShownChanged(shown: Boolean) {}
        fun onTap(tap: Tap) {}
        fun onLongPress() {}
        fun onPullDown() {}
        /** Sideways swipe: -1 = towards the left, +1 = towards the right. */
        fun onSwipe(direction: Int) {}
        fun onSwipeUp() {}
        fun onOutsideTouch() {}
        /** Auto-close timers pause while a finger is on the island. */
        fun onTouching(active: Boolean) {}
    }

    var host: Host? = null
    /** Preview only: paint the camera lens, since there is no real hole behind the view. */
    var drawLens = false

    /** Island center in screen pixels; the overlay window is centered on it. */
    val anchorX: Float get() = layout.centerX

    private var screen = ScreenSpec(1080f, 96f, 2.75f, null)
    private var config = IslandConfig()
    private var layout: IslandLayout = IslandGeometry.compute(screen, config, 0f)
    private val dp get() = layout.density
    private val painter = IslandPainter(context)

    private class Layer(var scene: Scene, val alpha: Spring)

    /** Fading-out scenes first, the current one last. */
    private val layers = ArrayList<Layer>()
    var scene: Scene = IdleScene(config)
        private set

    // --- motion -------------------------------------------------------------------------------
    private val width = Spring(0f, 0.5f)
    private val height = Spring(0f, 0.5f)
    private val radius = Spring(0f, 0.5f)
    /** Horizontal shift of the island's center, for shapes that stretch over Samsung's chip. */
    private val offset = Spring(0f, 0.5f)
    private val shadow = Spring(0f, 0.002f)
    private val press = Spring(0f, 0.002f)
    private val shown = Spring(1f, 0.002f)
    private val shapeSprings = listOf(width, height, radius, offset, shadow, press, shown)

    /** Host-driven visibility (screen off, landscape); combined with the user's own switch. */
    private var hostVisible = true
    /** Swiped up by the user: hidden for a while. */
    private var snoozed = false

    fun setSnoozed(on: Boolean) {
        if (snoozed == on) return
        snoozed = on
        updateShown(animate = true)
    }

    private var frameLoopRunning = false
    private var lastFrameNanos = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = if (lastFrameNanos == 0L) 1f / 60f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 1f / 24f)
            lastFrameNanos = frameTimeNanos
            var moving = false
            for (s in shapeSprings) if (s.step(dt)) moving = true
            for (l in layers) if (l.alpha.step(dt)) moving = true
            layers.removeAll { it.scene !== scene && it.alpha.isAtRest && it.alpha.value == 0f }
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

    private val tick = Runnable { invalidate() }
    private val rect = RectF()
    private val clipPath = Path()
    private val islandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pulledDown = false
    private var doubleTapped = false
    private var lastTapWasButton = false
    /** App behind what was on screen at the first tap of a possible double tap. */
    private var firstTapApp: android.app.PendingIntent? = null
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        // The first tap acts at once (it expands the island); a second tap right after opens the
        // app behind what is shown. That second tap's own "single tap" is swallowed, so it can
        // never hit a button on the card the first tap just opened.
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (doubleTapped) return true
            val tap = scene.tap(e.x, e.y, frame())
            // The first tap may replace the scene at once (a notice dismissed, a card opened), so
            // the app a double tap opens is the one the user actually saw. Only a plain
            // expand/collapse starts a double tap; a button, a dismissal or a launch never does.
            firstTapApp = if (tap is Tap.Expand || tap is Tap.Collapse) scene.app else null
            lastTapWasButton = tap !is Tap.Expand && tap !is Tap.Collapse && tap !is Tap.Dismiss
            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            host?.onTap(tap)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleTapped = true
            val app = firstTapApp
            firstTapApp = null
            if (app != null) {
                haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                host?.onTap(Tap.Launch(app))
            }
            return true
        }

        // Two quick presses on a control (next, next) both count; the detector would otherwise
        // swallow the second one as half of a double tap.
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked != MotionEvent.ACTION_UP || !lastTapWasButton) return false
            val tap = scene.tap(e.x, e.y, frame())
            if (tap is Tap.Expand || tap is Tap.Collapse || tap is Tap.Dismiss) return false
            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            host?.onTap(tap)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // A held second tap of a double tap is not a long press: the app just opened.
            if (doubleTapped) return
            haptic(HapticFeedbackConstants.LONG_PRESS)
            host?.onLongPress()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (velocityY > 600f * dp && abs(velocityY) > abs(velocityX)) {
                pullDown()
                return true
            }
            if (velocityY < -600f * dp && abs(velocityY) > abs(velocityX)) {
                swipeUp()
                return true
            }
            // Sideways: next song, the next concurrent activity, or a notice dismissed.
            if (abs(velocityX) > 500f * dp && abs(velocityX) > abs(velocityY) * 1.5f) {
                swipeSideways(if (velocityX < 0f) -1 else 1)
                return true
            }
            return false
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.x - start.x
            val dy = e2.y - start.y
            // The dominant axis decides, once the drag is long enough on it.
            val threshold = 36f * dp
            when {
                abs(dy) >= abs(dx) && dy > threshold -> pullDown()
                abs(dy) >= abs(dx) && dy < -threshold -> swipeUp()
                abs(dx) > abs(dy) && abs(dx) > threshold -> swipeSideways(if (dx < 0f) -1 else 1)
                else -> return false
            }
            return true
        }
    })

    init {
        contentDescription = context.getString(R.string.app_name)
        layers.add(Layer(scene, Spring(1f, 0.002f)))
        snapShape()
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

    /** Morphs to [next]. The same key updates in place; a new key crossfades. */
    fun show(next: Scene, animate: Boolean = true) {
        val current = layers.lastOrNull()
        val oldShape = scene.shape(layout)
        val newShape = next.shape(layout)
        if (current != null && current.scene.key == next.key) {
            current.scene = next
            scene = next
            if (oldShape != newShape) morphTo(newShape, growing = area(newShape) >= area(oldShape), animate = animate)
            invalidate()
            return
        }
        scene = next
        if (!animate || !animationsEnabled() || !isAttachedToWindow) {
            layers.clear()
            layers.add(Layer(next, Spring(1f, 0.002f)))
            snapShape()
            return
        }
        val growing = area(newShape) >= area(oldShape)
        for (l in layers) l.alpha.animateTo(0f, 900f, 1f)
        // Going back to something still fading out (A -> B -> A): revive that layer instead of
        // stacking a second copy of it, which would ghost old content for a moment.
        val revived = layers.firstOrNull { it.scene.key == next.key }
        if (revived != null) {
            layers.remove(revived)
            revived.scene = next
            revived.alpha.animateTo(1f, 320f, 1f)
            layers.add(revived)
        } else {
            layers.add(Layer(next, Spring(0f, 0.002f).also { it.animateTo(1f, 320f, 1f, delaySeconds = if (growing) 0.07f else 0.05f) }))
        }
        morphTo(newShape, growing, animate = true)
    }

    /** Fades the whole island in or out, e.g. when the screen rotates to landscape. */
    fun setShown(visible: Boolean, animate: Boolean) {
        hostVisible = visible
        updateShown(animate)
    }

    /** Whether the island is (or is becoming) visible. */
    val isShownTarget: Boolean get() = hostVisible && config.visible && !snoozed

    /** Current resting window size for the overlay host. */
    fun restingWindowSize(): Pair<Int, Int> = windowSizeFor(animating = false)

    // --- motion internals ---------------------------------------------------------------------

    private fun updateShown(animate: Boolean) {
        val target = if (isShownTarget) 1f else 0f
        if (target != shown.target) host?.onShownChanged(target > 0f)
        if (target == 0f) {
            // The window turns untouchable now and may never see this finger lift: end the gesture
            // here, including a pending long press, which would otherwise still open the settings.
            press.snapTo(0f)
            val now = SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0).also {
                gestures.onTouchEvent(it)
                it.recycle()
            }
            doubleTapped = false
            firstTapApp = null
            host?.onTouching(false)
        }
        if (!animate || !animationsEnabled()) {
            shown.snapTo(target)
            invalidate()
        } else if (shown.target != target) {
            shown.animateTo(target, stiffness = if (target > 0f) 260f else 700f, dampingRatio = 0.9f)
            startFrameLoop()
        }
    }

    private fun morphTo(shape: IslandShape, growing: Boolean, animate: Boolean) {
        if (!animate || !animationsEnabled() || !isAttachedToWindow) {
            snapShape()
            return
        }
        // Opening is soft and bouncy; closing is quicker and settles without wobble.
        val stiffness = if (growing) 260f else 420f
        val damping = if (growing) 0.72f else 0.86f
        width.animateTo(shape.width, stiffness, damping)
        height.animateTo(shape.height, stiffness, damping)
        radius.animateTo(shape.radius, stiffness, 1f)
        offset.animateTo(shape.offsetX, stiffness, damping)
        shadow.animateTo(if (scene.isCard) 1f else 0f, 300f, 1f)
        requestWindowSize(settled = false)
        startFrameLoop()
    }

    private fun snapShape() {
        val shape = scene.shape(layout)
        width.snapTo(shape.width)
        height.snapTo(shape.height)
        radius.snapTo(shape.radius)
        offset.snapTo(shape.offsetX)
        shadow.snapTo(if (scene.isCard) 1f else 0f)
        press.snapTo(0f)
        for (l in layers) l.alpha.snapTo(if (l.scene === scene) 1f else 0f)
        layers.removeAll { it.scene !== scene }
        invalidate()
        requestWindowSize(settled = true)
    }

    private fun relayout(animate: Boolean) {
        val textWidth = if (config.text.isEmpty()) 0f else painter.measure(config.text, idleTextSize(), painter.bold)
        layout = IslandGeometry.compute(screen, config, textWidth)
        if (animate && animationsEnabled()) {
            val shape = scene.shape(layout)
            width.animateTo(shape.width, 520f, 0.8f)
            height.animateTo(shape.height, 520f, 0.8f)
            radius.animateTo(shape.radius, 520f, 1f)
            offset.animateTo(shape.offsetX, 520f, 0.8f)
            requestWindowSize(settled = false)
            startFrameLoop()
        } else {
            snapShape()
        }
    }

    /** Must match [IdleScene]: the glyph is 56% of the island height. */
    private fun idleTextSize(): Float {
        val d = screen.density
        val h = max(config.heightDp.coerceIn(IslandConfig.MIN_HEIGHT_DP, IslandConfig.MAX_HEIGHT_DP) * d, (screen.hole?.diameter ?: 0f) + 10f * d)
        return h * 0.56f
    }

    private fun area(s: IslandShape) = s.width * s.height

    private fun startFrameLoop() {
        if (frameLoopRunning) return
        frameLoopRunning = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    // getDurationScale() exists only from Android 13; areAnimatorsEnabled() covers the rest.
    private fun animationsEnabled(): Boolean =
        ValueAnimator.areAnimatorsEnabled() &&
            (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU || ValueAnimator.getDurationScale() > 0f)

    // --- window sizing ------------------------------------------------------------------------

    private fun windowSizeFor(animating: Boolean): Pair<Int, Int> {
        val target = scene.shape(layout)
        val margin = SHADOW_MARGIN_DP * dp
        val w: Float
        val h: Float
        // The window is centered on the camera, so an off-center shape needs room on both sides.
        if (animating) {
            // Room for the current shape, the target, the spring overshoot and the press squish.
            val shift = max(abs(offset.value), abs(target.offsetX))
            w = (max(width.value, target.width) * 1.1f + 2f * shift) + 2f * margin
            h = layout.top + max(height.value, target.height) * 1.1f + margin
        } else {
            val m = if (scene.isCard) margin else 1f * dp
            w = target.width + 2f * abs(target.offsetX) + 2f * m
            h = layout.top + target.height + m
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
                host?.onOutsideTouch()
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                if (!hitIsland(event.x, event.y, slop = 8f * dp)) {
                    doubleTapped = false
                    lastTapWasButton = false
                    firstTapApp = null
                    host?.onOutsideTouch()
                    return false
                }
                pulledDown = false
                host?.onTouching(true)
                press.animateTo(1f, 900f, 0.6f)
                requestWindowSize(settled = false)
                startFrameLoop()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                press.animateTo(0f, 600f, 0.55f)
                startFrameLoop()
                host?.onTouching(false)
            }
        }
        gestures.onTouchEvent(event)
        // The second tap may end as a drag, a long press or a cancel instead of a tap-up: the
        // flag must not outlive its gesture, or the next real tap would be swallowed.
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) doubleTapped = false
        return true
    }

    private fun pullDown() {
        if (pulledDown) return
        pulledDown = true
        host?.onPullDown()
    }

    private fun swipeUp() {
        if (pulledDown) return
        pulledDown = true
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        host?.onSwipeUp()
    }

    private fun swipeSideways(direction: Int) {
        if (pulledDown) return
        pulledDown = true
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
        host?.onSwipe(direction)
    }

    private fun hitIsland(x: Float, y: Float, slop: Float): Boolean =
        abs(x - centerX() - offset.value) <= width.value / 2f + slop && y >= layout.top - slop && y <= layout.top + height.value + slop

    private fun haptic(type: Int) {
        if (config.haptics) performHapticFeedback(type)
    }

    // --- drawing ------------------------------------------------------------------------------

    private fun centerX(): Float = if (isOverlay) getWidth() / 2f else layout.centerX

    private fun frame(): Frame = Frame(
        holeX = centerX() + layout.holeOffsetX,
        cx = centerX() + offset.value,
        top = layout.top,
        width = width.value,
        height = height.value,
        layout = layout,
        p = painter,
        nowMs = System.currentTimeMillis(),
        animMs = SystemClock.uptimeMillis(),
    )

    override fun onDraw(canvas: Canvas) {
        val visibility = shown.value.coerceIn(0f, 1f)
        if (visibility <= 0.001f) return
        val cx = centerX() + offset.value
        val top = layout.top
        val appear = 0.55f + 0.45f * visibility
        val w = max(width.value * (1f + 0.05f * press.value) * appear, 1f)
        val h = max(height.value * (1f + 0.07f * press.value) * appear, 1f)
        val r = min(radius.value * appear, h / 2f)
        val left = cx - w / 2f

        canvas.save()
        if (visibility < 1f) canvas.saveLayerAlpha(null, (visibility * 255).roundToInt())

        // Soft shadow only under cards; the resting island is pure black like hardware.
        val s = shadow.value.coerceIn(0f, 1f)
        if (s > 0.01f) {
            islandPaint.setShadowLayer(18f * dp, 0f, 6f * dp, Color.argb((s * 90).roundToInt(), 0, 0, 0))
        } else {
            islandPaint.clearShadowLayer()
        }
        rect.set(left, top, left + w, top + h)
        islandPaint.alpha = (255 * config.opacity.coerceIn(IslandConfig.MIN_OPACITY, 1f)).roundToInt()
        canvas.drawRoundRect(rect, r, r, islandPaint)
        islandPaint.alpha = 255
        if (drawLens) drawLens(canvas, centerX() + layout.holeOffsetX, top + layout.holeCenterY)

        clipPath.rewind()
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(clipPath)

        val f = Frame(cx, top, w, h, layout, painter, System.currentTimeMillis(), SystemClock.uptimeMillis(), holeX = centerX() + layout.holeOffsetX)
        var refresh = Long.MAX_VALUE
        for (l in layers) {
            val a = l.alpha.value.coerceIn(0f, 1f)
            if (a <= 0.01f) continue
            val sc = l.scene
            val full = sc.shape(layout)
            // Content is laid out at the scene's resting size and scaled while the shape morphs.
            val scale = if (sc is IdleScene) 1f else (w / full.width).coerceIn(0.4f, 1.1f) * (0.94f + 0.06f * a)
            canvas.save()
            if (scale != 1f) canvas.scale(scale, scale, cx, if (sc.isCard) top else top + full.height / 2f)
            sc.draw(canvas, f, a)
            canvas.restore()
            if (sc.refreshMs > 0L) refresh = min(refresh, sc.nextRefreshDelay(f.nowMs).coerceAtLeast(16L))
        }

        if (visibility < 1f) canvas.restore()
        canvas.restore()

        // Waveforms and running clocks redraw on their own cadence while nothing else animates.
        // One pending tick at most, so extra invalidations never stack into parallel loops.
        removeCallbacks(tick)
        if (!frameLoopRunning && refresh != Long.MAX_VALUE) postDelayed(tick, refresh)
    }

    /** A camera lens as it really looks inside a black island: barely there. */
    private fun drawLens(canvas: Canvas, x: Float, y: Float) {
        val r = max(layout.holeRadius, 5f * dp) * 0.8f
        lensPaint.color = 0xFF07070A.toInt()
        canvas.drawCircle(x, y, r, lensPaint)
        lensPaint.color = 0xFF0E0E14.toInt()
        canvas.drawCircle(x, y, r * 0.55f, lensPaint)
        lensPaint.color = 0x2A5A6A9A
        canvas.drawCircle(x - r * 0.24f, y - r * 0.24f, r * 0.14f, lensPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (isOverlay || w == 0) return
        // The preview is a miniature phone top: camera centered, typical Galaxy proportions.
        val d = resources.displayMetrics.density
        setScreen(ScreenSpec(w.toFloat(), 36f * d, d, Hole(w / 2f, PREVIEW_HOLE_Y_DP * d, PREVIEW_HOLE_DP * d)))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(tick)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        frameLoopRunning = false
        lastFrameNanos = 0L
    }

    companion object {
        private const val SHADOW_MARGIN_DP = 22f
        const val PREVIEW_HOLE_Y_DP = 18f
        const val PREVIEW_HOLE_DP = 16f
    }
}
