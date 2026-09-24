package io.github.dtubugk.island.island

import android.app.PendingIntent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import io.github.dtubugk.island.R
import io.github.dtubugk.island.data.IslandColors
import io.github.dtubugk.island.data.IslandConfig
import io.github.dtubugk.island.island.IslandPainter.Companion.GREEN
import io.github.dtubugk.island.island.IslandPainter.Companion.ORANGE
import io.github.dtubugk.island.island.IslandPainter.Companion.RED
import io.github.dtubugk.island.island.IslandPainter.Companion.SECONDARY
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a touch on the island asks for. */
sealed class Tap {
    object Expand : Tap()
    object Collapse : Tap()
    object Dismiss : Tap()
    object Settings : Tap()
    object PlayPause : Tap()
    object Next : Tap()
    object Previous : Tap()
    data class Launch(val intent: PendingIntent?) : Tap()
    /** One of a notification's own buttons: the app decides what becomes of its notification. */
    data class Action(val intent: PendingIntent?) : Tap()
    data class Act(val action: IslandAction) : Tap()
    /** Switch to another concurrent activity (a tab in the card). */
    data class Select(val key: String) : Tap()
    /** Open the inline reply box for the message shown. */
    object Reply : Tap()
}

/**
 * One frame's drawing context. Scenes lay themselves out at their full resting size, centered
 * on [cx] and hanging from [top]; the view scales them while the shape is still morphing.
 * [width]/[height] are the live (animated) island size.
 */
class Frame(
    val cx: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val layout: IslandLayout,
    val p: IslandPainter,
    /** Wall-clock time, for timers. */
    val nowMs: Long,
    /** Monotonic time, for animation. */
    val animMs: Long,
    /** The camera's x in view pixels; content keeps clear of it. */
    val holeX: Float = cx,
) {
    val dp get() = layout.density
}

/**
 * One thing the island can show. Scenes with the same [key] are the same content updated in
 * place (a new song position, the next second of a call); a different key crossfades.
 */
abstract class Scene(val key: String) {
    abstract fun shape(l: IslandLayout): IslandShape
    /** Cards grow down from the top edge; pills scale around their middle. */
    open val isCard: Boolean = false
    /** The app behind what is shown, opened by a double tap; null when the island shows its own content. */
    open val app: PendingIntent? get() = null
    /** Redraw cadence while visible, for waveforms and running clocks. 0 = static. */
    open val refreshMs: Long = 0L

    /** Milliseconds until this scene next looks different; defaults to [refreshMs]. */
    open fun nextRefreshDelay(nowMs: Long): Long = refreshMs
    abstract fun draw(c: Canvas, f: Frame, alpha: Float)
    abstract fun tap(x: Float, y: Float, f: Frame): Tap
}

// --- shared layout helpers ----------------------------------------------------------------------

private fun Frame.left(shape: IslandShape) = cx - shape.width / 2f
private fun Frame.right(shape: IslandShape) = cx + shape.width / 2f

/** Center line of the band that holds the camera at the top of a card. */
private fun Frame.bandY() = top + layout.holeCenterY.coerceIn(layout.bandHeight * 0.3f, layout.bandHeight * 0.7f)

/** Room for a label on one side of the camera in a pill. */
private fun Frame.sideRoom(shape: IslandShape, edgeInset: Float) =
    shape.width / 2f - layout.holeRadius - 8f * dp - edgeInset

private fun near(x: Float, y: Float, cx: Float, cy: Float, r: Float) = abs(x - cx) <= r && abs(y - cy) <= r

// --- quick actions -----------------------------------------------------------------------------

/** One round button in a quick-actions row. */
data class QuickAction(val action: IslandAction, val icon: Int, val label: String, val on: Boolean, val onColor: Int = 0xFFFFFFFF.toInt())

/** Round icon buttons with tiny labels, shared out evenly over the card's width. First one on the right. */
private class ActionRow(private val actions: List<QuickAction>) {
    fun centers(f: Frame, shape: IslandShape): List<Float> {
        if (actions.isEmpty()) return emptyList()
        val inset = 16f * f.dp
        val start = f.left(shape) + inset
        val end = f.right(shape) - inset
        val step = (end - start) / actions.size
        return actions.indices.map { i -> end - step * (i + 0.5f) }
    }

    fun draw(c: Canvas, f: Frame, shape: IslandShape, cy: Float, alpha: Float) {
        val p = f.p
        val dp = f.dp
        val r = 22f * dp
        centers(f, shape).forEachIndexed { i, cx ->
            val a = actions[i]
            val bg = if (a.on) a.onColor else 0x24FFFFFF
            val fg = if (a.on && a.onColor != 0xFFFFFFFF.toInt()) Color.WHITE else if (a.on) Color.BLACK else Color.WHITE
            p.circle(c, cx, cy, r, bg, alpha)
            p.icon(c, a.icon, cx, cy, 22f * dp, fg, alpha * 0.95f)
            p.label(c, a.label, cx, cy + r + 9f * dp, 10.5f * dp, SECONDARY, alpha, Paint.Align.CENTER, p.medium, maxWidth = 2f * r + 14f * dp)
        }
    }

    fun hit(x: Float, y: Float, f: Frame, shape: IslandShape, cy: Float): IslandAction? {
        if (abs(y - cy) > 30f * f.dp) return null
        centers(f, shape).forEachIndexed { i, cx -> if (abs(x - cx) <= 30f * f.dp) return actions[i].action }
        return null
    }

    companion object {
        /** Content height a row needs: the buttons plus their labels. */
        const val HEIGHT_DP = 72f
    }
}

/** The island's own tools, shown on the info card, in the user's chosen order. */
fun quickActions(state: SystemState, chosen: List<IslandAction>): List<QuickAction> = chosen.mapNotNull { a ->
    when (a) {
        IslandAction.FLASHLIGHT -> QuickAction(a, R.drawable.ic_flashlight, "פנס", state.flashlight, 0xFFFFD60A.toInt())
        IslandAction.DND -> QuickAction(a, R.drawable.ic_dnd, "לא להפריע", state.doNotDisturb, IslandPainter.PURPLE)
        IslandAction.AIRPLANE -> QuickAction(a, R.drawable.ic_airplane, "מצב טיסה", false)
        IslandAction.SCREENSHOT -> QuickAction(a, R.drawable.ic_screenshot, "צילום מסך", false)
        IslandAction.LOCK -> QuickAction(a, R.drawable.ic_lock, "נעילה", false)
        IslandAction.SETTINGS -> QuickAction(a, R.drawable.ic_island_settings, "הגדרות", false)
        IslandAction.SPEAKER, IslandAction.MUTE -> null
    }
}

/**
 * Tabs for concurrent activities, on the band's left side of a card: small discs, the current one
 * filled with its color. Tapping one switches the island to it.
 */
private object Tabs {
    fun centers(f: Frame, shape: IslandShape, count: Int): List<Float> {
        val step = 26f * f.dp
        val start = f.left(shape) + 22f * f.dp + 10f * f.dp
        return List(count) { i -> start + i * step }
    }

    fun draw(c: Canvas, f: Frame, shape: IslandShape, all: List<IslandTab>, alpha: Float) {
        val cy = f.bandY()
        val tabs = fit(f, shape, all)
        centers(f, shape, tabs.size).forEachIndexed { i, cx ->
            val t = tabs[i]
            f.p.circle(c, cx, cy, 10f * f.dp, if (t.selected) t.accent else 0x33FFFFFF, alpha)
            f.p.icon(c, t.icon, cx, cy, 12f * f.dp, if (t.selected) Color.BLACK else Color.WHITE, alpha * 0.95f)
        }
    }

    /** As many tabs as fit before the camera, the current one always among them; none if not even one fits. */
    private fun fit(f: Frame, shape: IslandShape, all: List<IslandTab>): List<IslandTab> {
        if (all.isEmpty()) return all
        val first = f.left(shape) + 32f * f.dp
        val limit = f.holeX - f.layout.holeRadius - 4f * f.dp
        val room = limit - (first + 10f * f.dp)
        if (room < 0f) return emptyList()
        val max = (1 + room / (26f * f.dp)).toInt().coerceIn(1, all.size)
        if (all.size <= max) return all
        val kept = all.take(max).toMutableList()
        all.firstOrNull { it.selected }?.let { if (it !in kept) kept[kept.lastIndex] = it }
        return kept
    }

    fun hit(x: Float, y: Float, f: Frame, shape: IslandShape, all: List<IslandTab>): IslandTab? {
        val tabs = fit(f, shape, all)
        if (tabs.isEmpty() || abs(y - f.bandY()) > 18f * f.dp) return null
        centers(f, shape, tabs.size).forEachIndexed { i, cx -> if (abs(x - cx) <= 14f * f.dp) return tabs[i] }
        return null
    }
}

/** What a call needs at hand: speaker, mute, and airplane mode to drop everything at once. */
fun callActions(state: SystemState): List<QuickAction> = listOf(
    QuickAction(IslandAction.SPEAKER, R.drawable.ic_speaker, "רמקול", state.speaker, IslandPainter.BLUE),
    QuickAction(IslandAction.MUTE, R.drawable.ic_mic_off, "השתקה", state.muted, RED),
    QuickAction(IslandAction.AIRPLANE, R.drawable.ic_airplane, "מצב טיסה", false),
)

// --- idle ---------------------------------------------------------------------------------------

class IdleScene(private val config: IslandConfig) : Scene("idle") {
    private val bounds = Rect()

    override fun shape(l: IslandLayout) = l.idle

    // The text rides on its side of the island, so it slides out with the edge as the island grows.
    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val text = config.text
        if (text.isEmpty()) return
        val p = f.p
        val rest = f.layout.idle
        val size = rest.height * 0.56f
        p.text.shader = null
        p.text.typeface = p.bold
        p.text.textSize = size * (0.9f + 0.1f * alpha)
        p.text.textAlign = Paint.Align.CENTER
        val fromEdge = rest.width / 2f - abs(f.layout.textCenterX)
        val side = if (f.layout.textCenterX >= 0f) 1f else -1f
        val x = f.cx + side * (f.width / 2f - fromEdge)
        p.text.getTextBounds(text, 0, text.length, bounds)
        val y = f.top + f.height / 2f - (bounds.top + bounds.bottom) / 2f
        val color = IslandColors.of(config.colorIndex).let { if (it == IslandColors.SYSTEM) p.systemAccent() else it }
        if (color == IslandColors.GRADIENT) {
            p.text.color = Color.WHITE
            val half = max(p.text.measureText(text) / 2f, 4f * f.dp)
            p.text.shader = LinearGradient(x + half, 0f, x - half, 0f, IslandColors.GRADIENT_START, IslandColors.GRADIENT_END, Shader.TileMode.CLAMP)
        } else {
            p.text.color = color
        }
        p.text.alpha = (alpha * 255).roundToInt()
        c.drawText(text, x, y, p.text)
        p.text.shader = null
    }

    override fun tap(x: Float, y: Float, f: Frame) = Tap.Expand
}

// --- info card: time, battery, greeting, date -------------------------------------------------

class InfoScene(
    private val config: IslandConfig,
    private val battery: BatteryState,
    private val time: String,
    private val greeting: String,
    private val date: String,
    private val system: SystemState = SystemState(),
    actions: List<IslandAction> = IslandConfig().quickActions,
) : Scene("info") {
    override val isCard = true
    private var tileGradient: LinearGradient? = null
    private val row = ActionRow(quickActions(system, actions))
    override fun shape(l: IslandLayout) = l.card((76f + ActionRow.HEIGHT_DP) * l.density)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val p = f.p
        val dp = f.dp
        val left = f.left(s)
        val right = f.right(s)
        val band = f.bandY()
        val inset = 24f * dp

        // The card covers the status bar, so its band repeats it: clock right, battery left.
        p.label(c, time, right - inset, band, 15f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, p.semibold)
        val batteryColor = when {
            battery.charging -> GREEN
            battery.level <= 15 -> RED
            else -> Color.WHITE
        }
        p.battery(c, left + inset, band - 5.5f * dp, 22f * dp, 11f * dp, battery.level, batteryColor, battery.charging, alpha)
        p.label(c, "${battery.level}%", left + inset + 28f * dp, band, 13f * dp, batteryColor, alpha, Paint.Align.LEFT)

        val row = f.top + f.layout.bandHeight + 32f * dp
        val tile = 50f * dp
        val tileRight = right - 16f * dp
        val tileLeft = tileRight - tile
        // Built once in tile-local coordinates and moved with the canvas, so it follows the tile.
        val gradient = tileGradient ?: LinearGradient(0f, 0f, tile, tile, IslandColors.GRADIENT_START, IslandColors.GRADIENT_END, Shader.TileMode.CLAMP).also { tileGradient = it }
        p.fill.shader = gradient
        p.fill.alpha = (alpha * 255).roundToInt()
        c.save()
        c.translate(tileLeft, row - tile / 2f)
        c.drawRoundRect(0f, 0f, tile, tile, 16f * dp, 16f * dp, p.fill)
        c.restore()
        p.fill.shader = null
        val avatar = config.text.ifBlank { IslandConfig.DEFAULT_TEXT }
        val fit = min(28f * dp, 28f * dp * (tile - 14f * dp) / max(p.measure(avatar, 28f * dp, p.bold), 1f))
        p.label(c, avatar, tileLeft + tile / 2f, row, fit, Color.WHITE, alpha, Paint.Align.CENTER, p.bold)

        val textRight = tileLeft - 14f * dp
        val room = textRight - (left + inset)
        p.label(c, greeting, textRight, row - 10f * dp, 17f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, p.semibold, room)
        p.label(c, date, textRight, row + 12f * dp, 13.5f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.regular, room)

        this.row.draw(c, f, s, actionsY(f), alpha)
    }

    private fun actionsY(f: Frame) = f.top + f.layout.bandHeight + (76f + 26f) * f.dp

    override fun tap(x: Float, y: Float, f: Frame): Tap {
        row.hit(x, y, f, shape(f.layout), actionsY(f))?.let { return Tap.Act(it) }
        return Tap.Collapse
    }
}

// --- short notices: charging, silent mode, Do Not Disturb, headphones, low battery ---------------

/** A wide pill: label on the right (read first in Hebrew), a status glyph and value on the left. */
class NoticeScene(
    key: String,
    private val label: String,
    private val glyph: Glyph,
    private val value: String = "",
    private val valueColor: Int = Color.WHITE,
    /** Tapping opens this instead of just dismissing (a saved screenshot). */
    private val open: PendingIntent? = null,
) : Scene(key) {
    override val app get() = open
    sealed class Glyph {
        data class Icon(val res: Int, val background: Int) : Glyph()
        data class Battery(val level: Int, val color: Int, val bolt: Boolean) : Glyph()
    }

    override fun shape(l: IslandLayout) = l.charging

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val p = f.p
        val h = s.height
        val mid = f.top + h / 2f
        val inset = h * 0.5f
        p.label(c, label, f.right(s) - inset, mid, h * 0.42f, Color.WHITE, alpha, Paint.Align.RIGHT, p.medium, f.sideRoom(s, inset))

        var x = f.left(s) + inset
        when (glyph) {
            is Glyph.Battery -> {
                val bw = h * 0.86f
                p.battery(c, x, mid - h * 0.21f, bw, h * 0.42f, glyph.level, glyph.color, glyph.bolt, alpha)
                x += bw + h * 0.28f
            }
            is Glyph.Icon -> {
                // Concentric with the pill's rounded end.
                val d = h * 0.74f
                val gx = f.left(s) + h / 2f
                p.circle(c, gx, mid, d / 2f, glyph.background, alpha)
                p.icon(c, glyph.res, gx, mid, d * 0.62f, Color.WHITE, alpha)
                x = gx + d / 2f + h * 0.22f
            }
        }
        if (value.isNotEmpty()) {
            p.label(c, value, x, mid, h * 0.42f, valueColor, alpha, Paint.Align.LEFT, p.semibold, f.sideRoom(s, x - f.left(s)))
        }
    }

    override fun tap(x: Float, y: Float, f: Frame): Tap = if (open != null) Tap.Launch(open) else Tap.Dismiss
}

// --- music -------------------------------------------------------------------------------------

/** Artwork by the camera on the right, live waveform on the left: the classic island. */
class MediaCompactScene(
    private val media: MediaState,
    /** Samsung's own chip for this song, in screen pixels: the island stretches over it. */
    private val cover: RectF? = null,
) : Scene("media-c:${media.packageName}") {
    override val refreshMs get() = if (media.playing) FRAME_MS else 0L
    override val app get() = media.openApp
    override fun shape(l: IslandLayout) = l.compact.covering(cover, l)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val h = s.height
        val mid = f.top + h / 2f
        val art = h * 0.66f
        val artX = f.right(s) - h / 2f
        val waveX = f.left(s) + h / 2f + h * 0.05f
        f.p.artwork(c, media.art, artX, mid, art, art * 0.26f, media.accent, alpha)
        f.p.waveform(c, waveX, mid, h * 0.62f, h * 0.46f, media.accent, media.playing, f.animMs, alpha)

        // Stretched over Samsung's chip, the island takes over what the chip said: the title.
        val gap = 10f * f.dp
        val clear = f.layout.holeRadius + gap
        val rightStart = f.holeX + clear
        val rightEnd = artX - art / 2f - gap
        val leftStart = waveX + h * 0.31f + gap
        val leftEnd = f.holeX - clear
        val useRight = rightEnd - rightStart >= leftEnd - leftStart
        val start = if (useRight) rightStart else leftStart
        val end = if (useRight) rightEnd else leftEnd
        if (end - start >= MIN_TITLE_ROOM_DP * f.dp) {
            f.p.marquee(c, media.title, start, end, mid, h * 0.4f, 0xE6FFFFFF.toInt(), alpha, if (media.playing) f.animMs else 0L)
        }
    }

    override fun tap(x: Float, y: Float, f: Frame) = Tap.Expand

    companion object {
        const val FRAME_MS = 33L
        private const val MIN_TITLE_ROOM_DP = 56f
    }
}

/** Artwork, title, progress and controls, the island's big music card. */
class MediaCardScene(private val media: MediaState, private val tabs: List<IslandTab> = emptyList()) : Scene("media-card:${media.packageName}") {
    override val isCard = true
    override val app get() = media.openApp
    override val refreshMs get() = if (media.playing) MediaCompactScene.FRAME_MS else 0L
    override fun shape(l: IslandLayout) = l.card(CONTENT_DP * l.density)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val p = f.p
        val dp = f.dp
        val left = f.left(s)
        val right = f.right(s)
        val band = f.bandY()
        val inset = 24f * dp

        p.label(c, media.appLabel, right - inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.medium, f.sideRoom(s, inset))
        if (tabs.isEmpty()) p.waveform(c, left + inset + 11f * dp, band, 22f * dp, 14f * dp, media.accent, media.playing, f.animMs, alpha)
        else Tabs.draw(c, f, s, tabs, alpha)

        val row = artCenterY(f)
        val art = ART_DP * dp
        val artX = right - 18f * dp - art / 2f
        p.artwork(c, media.art, artX, row, art, 12f * dp, media.accent, alpha)
        val textRight = artX - art / 2f - 14f * dp
        val room = textRight - (left + inset)
        p.label(c, media.title, textRight, row - 11f * dp, 17f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, p.semibold, room)
        p.label(c, media.artist, textRight, row + 12f * dp, 14f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.regular, room)

        // Playback runs left to right, like on every phone, even in Hebrew.
        val py = f.top + f.layout.bandHeight + 92f * dp
        val position = media.positionNow()
        if (media.durationMs > 0) {
            val elapsed = IslandPainter.duration(position)
            val remaining = "-" + IslandPainter.duration(media.durationMs - position)
            val size = 12f * dp
            val ew = p.label(c, elapsed, left + inset, py, size, SECONDARY, alpha, Paint.Align.LEFT, p.medium)
            val rw = p.measure(remaining, size)
            p.label(c, remaining, right - inset, py, size, SECONDARY, alpha, Paint.Align.RIGHT, p.medium)
            p.progress(c, left + inset + ew + 10f * dp, right - inset - rw - 10f * dp, py, 4f * dp, position.toFloat() / media.durationMs, alpha)
        }

        val cy = controlsY(f)
        p.icon(c, R.drawable.ic_previous, f.cx - SKIP_OFFSET_DP * dp, cy, 32f * dp, Color.WHITE, alpha)
        p.icon(c, if (media.playing) R.drawable.ic_pause else R.drawable.ic_play, f.cx, cy, 42f * dp, Color.WHITE, alpha)
        p.icon(c, R.drawable.ic_next, f.cx + SKIP_OFFSET_DP * dp, cy, 32f * dp, Color.WHITE, alpha)
    }

    override fun tap(x: Float, y: Float, f: Frame): Tap {
        val dp = f.dp
        Tabs.hit(x, y, f, shape(f.layout), tabs)?.let { return Tap.Select(it.key) }
        val cy = controlsY(f)
        val r = 30f * dp
        return when {
            near(x, y, f.cx, cy, r) -> Tap.PlayPause
            near(x, y, f.cx - SKIP_OFFSET_DP * dp, cy, r) -> Tap.Previous
            near(x, y, f.cx + SKIP_OFFSET_DP * dp, cy, r) -> Tap.Next
            abs(y - artCenterY(f)) <= 34f * dp -> Tap.Launch(media.openApp)
            else -> Tap.Collapse
        }
    }

    private fun artCenterY(f: Frame) = f.top + f.layout.bandHeight + 38f * f.dp
    private fun controlsY(f: Frame) = f.top + f.layout.bandHeight + 130f * f.dp

    private companion object {
        const val CONTENT_DP = 160f
        const val ART_DP = 60f
        const val SKIP_OFFSET_DP = 80f
    }
}

// --- calls and timers ----------------------------------------------------------------------------

private fun LiveActivity.accent() = when (kind) {
    LiveKind.CALL -> GREEN
    LiveKind.ALARM -> RED
    LiveKind.NAVIGATION -> IslandPainter.BLUE
    LiveKind.RECORDING -> RED
    LiveKind.TIMER -> ORANGE
    LiveKind.PROGRESS -> IslandPainter.BLUE
}

private fun LiveActivity.iconRes() = when (kind) {
    LiveKind.CALL -> R.drawable.ic_call
    LiveKind.ALARM -> R.drawable.ic_alarm
    LiveKind.NAVIGATION -> R.drawable.ic_navigation
    LiveKind.RECORDING -> R.drawable.ic_timer
    LiveKind.TIMER -> R.drawable.ic_timer
    LiveKind.PROGRESS -> R.drawable.ic_download
}

/** The badge by the camera: the maneuver arrow / app icon when there is one, else the kind's glyph. */
private fun drawBadge(c: Canvas, f: Frame, a: LiveActivity, cx: Float, cy: Float, size: Float, alpha: Float) {
    val icon = a.icon
    if (a.kind == LiveKind.RECORDING) {
        // The recording dot: a red disc in a soft ring.
        f.p.circle(c, cx, cy, size * 0.5f, 0x40FF453A, alpha)
        f.p.circle(c, cx, cy, size * 0.3f, RED, alpha)
    } else if (icon != null && (a.kind == LiveKind.NAVIGATION || a.kind == LiveKind.PROGRESS)) {
        f.p.roundDrawable(c, icon, cx, cy, size, alpha)
    } else {
        f.p.icon(c, a.iconRes(), cx, cy, size, a.accent(), alpha)
    }
}

/** The short value shown opposite the badge: running time, distance, or percent. */
private fun LiveActivity.compactValue(nowMs: Long): String? = when (kind) {
    LiveKind.NAVIGATION -> title.takeIf { it.isNotBlank() }
    LiveKind.PROGRESS -> if (progress >= 0f) "${(progress * 100).roundToInt()}%" else subText.ifBlank { title }.takeIf { it.isNotBlank() }
    else -> clock(nowMs)
}
/** Time until the shown seconds change, on the chronometer's own phase (up or down alike). */
private fun LiveActivity.nextTick(nowMs: Long): Long {
    if (chronometerBase <= 0L) return 0L
    val ms = Math.floorMod(chronometerBase - nowMs, 1000L)
    return (if (ms == 0L) 1000L else ms) + 15L
}

private fun LiveActivity.clock(nowMs: Long): String? {
    if (chronometerBase <= 0L) return null
    return IslandPainter.duration(if (countDown) chronometerBase - nowMs else nowMs - chronometerBase)
}

/** Green phone and running call time, or orange timer and countdown, around the camera. */
class LiveCompactScene(
    private val activity: LiveActivity,
    private val cover: RectF? = null,
) : Scene("live-c:${activity.key}") {
    override val refreshMs get() = if (activity.chronometerBase > 0L) 1000L else 0L
    override val app get() = activity.openApp
    override fun nextRefreshDelay(nowMs: Long) = activity.nextTick(nowMs)
    override fun shape(l: IslandLayout) = l.compact.covering(cover, l)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val h = s.height
        val mid = f.top + h / 2f
        val p = f.p
        drawBadge(c, f, activity, f.right(s) - h / 2f, mid, h * 0.62f, alpha)
        val inset = h * 0.42f
        val value = activity.compactValue(f.nowMs)
        if (activity.kind == LiveKind.PROGRESS && activity.progress >= 0f) {
            // A thin ring fills clockwise as the download or delivery advances.
            val r = h * 0.24f
            val cx = f.left(s) + inset + r
            p.stroke.strokeWidth = 3f * f.dp
            p.stroke.color = 0x40FFFFFF
            p.stroke.alpha = (0x40 * alpha).roundToInt()
            c.drawCircle(cx, mid, r, p.stroke)
            p.stroke.color = activity.accent()
            p.stroke.alpha = (255 * alpha).roundToInt()
            p.stroke.strokeCap = Paint.Cap.ROUND
            c.drawArc(cx - r, mid - r, cx + r, mid + r, -90f, 360f * activity.progress.coerceIn(0f, 1f), false, p.stroke)
            p.stroke.strokeCap = Paint.Cap.BUTT
            p.label(c, value.orEmpty(), cx + r + 6f * f.dp, mid, h * 0.4f, Color.WHITE, alpha, Paint.Align.LEFT, p.semibold, f.sideRoom(s, inset + 2f * r + 6f * f.dp))
        } else if (value != null) {
            p.label(c, value, f.left(s) + inset, mid, h * 0.46f, activity.accent(), alpha, Paint.Align.LEFT, p.semibold, f.sideRoom(s, inset))
        } else {
            p.label(c, activity.title, f.left(s) + inset, mid, h * 0.4f, Color.WHITE, alpha, Paint.Align.LEFT, p.medium, f.sideRoom(s, inset))
        }
    }

    override fun tap(x: Float, y: Float, f: Frame) = Tap.Expand
}

class LiveCardScene(
    private val activity: LiveActivity,
    private val system: SystemState = SystemState(),
    private val tabs: List<IslandTab> = emptyList(),
) : Scene("live-card:${activity.key}") {
    override val isCard = true
    override val app get() = activity.openApp
    override val refreshMs get() = if (activity.chronometerBase > 0L) 1000L else 0L
    override fun nextRefreshDelay(nowMs: Long) = activity.nextTick(nowMs)
    private val actions = activity.actions.take(3)
    private val hasProgress = activity.kind == LiveKind.PROGRESS && activity.progress >= 0f
    /** Calls get the island's own tools underneath the call's buttons. */
    private val tools = if (activity.kind == LiveKind.CALL) ActionRow(callActions(system)) else null
    private val infoDp = if (hasProgress) 96f else 76f
    override fun shape(l: IslandLayout) = l.card(
        (infoDp + (if (actions.isEmpty()) 0f else 52f) + (if (tools != null) ActionRow.HEIGHT_DP else 0f)) * l.density,
    )

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val p = f.p
        val dp = f.dp
        val left = f.left(s)
        val right = f.right(s)
        val band = f.bandY()
        val inset = 24f * dp
        p.label(c, activity.appLabel, right - inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.medium, f.sideRoom(s, inset))
        val eta = activity.subText.takeIf { activity.kind == LiveKind.NAVIGATION && it.isNotBlank() }
        val clock = activity.clock(f.nowMs)
        if (tabs.isNotEmpty()) {
            // Tabs take the left side; the running value moves next to the app label.
            Tabs.draw(c, f, s, tabs, alpha)
            val labelW = p.measure(activity.appLabel, 13f * dp)
            val x = right - inset - labelW - 10f * dp
            when {
                clock != null -> p.label(c, clock, x, band, 14f * dp, activity.accent(), alpha, Paint.Align.RIGHT, p.semibold)
                eta != null -> p.label(c, eta, x, band, 12f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.medium, x - f.holeX - f.layout.holeRadius - 8f * dp)
            }
        } else if (eta != null) {
            // ETA and remaining time, as the app reports them.
            p.label(c, eta, left + inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.LEFT, p.medium, f.sideRoom(s, inset))
        } else if (clock != null) {
            p.label(c, clock, left + inset, band, 15f * dp, activity.accent(), alpha, Paint.Align.LEFT, p.semibold)
        }

        val row = f.top + f.layout.bandHeight + 32f * dp
        val icon = 52f * dp
        val ix = right - 18f * dp - icon / 2f
        val badge = activity.icon
        if (activity.kind == LiveKind.RECORDING) {
            p.circle(c, ix, row, icon / 2f, 0x40FF453A, alpha)
            p.circle(c, ix, row, icon * 0.28f, RED, alpha)
        } else if (badge != null && (activity.kind == LiveKind.NAVIGATION || activity.kind == LiveKind.PROGRESS)) {
            p.circle(c, ix, row, icon / 2f, 0x24FFFFFF, alpha)
            p.roundDrawable(c, badge, ix, row, icon * 0.78f, alpha)
        } else {
            p.circle(c, ix, row, icon / 2f, activity.accent(), alpha)
            p.icon(c, activity.iconRes(), ix, row, icon * 0.5f, Color.WHITE, alpha)
        }
        val textRight = ix - icon / 2f - 14f * dp
        val room = textRight - (left + inset)
        // Navigation reads instruction first, distance second; everything else title then text.
        val first = if (activity.kind == LiveKind.NAVIGATION) activity.text.ifBlank { activity.title } else activity.title
        val second = if (activity.kind == LiveKind.NAVIGATION) activity.title else activity.text
        p.label(c, first, textRight, row - 11f * dp, 17f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, p.semibold, room)
        p.label(c, second, textRight, row + 12f * dp, 14f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.regular, room)

        if (hasProgress) {
            val py = f.top + f.layout.bandHeight + 80f * dp
            p.progress(c, left + inset, right - inset, py, 5f * dp, activity.progress, alpha)
        }

        actionRects(f).forEachIndexed { i, (l, r) ->
            val a = actions[i]
            val (bg, fg) = actionColors(a.title)
            val y = actionsY(f)
            p.pill(c, l, y - 21f * dp, r, y + 21f * dp, bg, a.title, fg, 15f * dp, alpha)
        }
        tools?.draw(c, f, s, toolsY(f), alpha)
    }

    override fun tap(x: Float, y: Float, f: Frame): Tap {
        Tabs.hit(x, y, f, shape(f.layout), tabs)?.let { return Tap.Select(it.key) }
        if (actions.isNotEmpty() && abs(y - actionsY(f)) <= 26f * f.dp) {
            actionRects(f).forEachIndexed { i, (l, r) -> if (x in l..r) return Tap.Action(actions[i].intent) }
        }
        tools?.hit(x, y, f, shape(f.layout), toolsY(f))?.let { return Tap.Act(it) }
        return Tap.Launch(activity.openApp)
    }

    private fun actionsY(f: Frame) = f.top + f.layout.bandHeight + (infoDp + 24f) * f.dp
    private fun toolsY(f: Frame) = f.top + f.layout.bandHeight + (infoDp + (if (actions.isEmpty()) 0f else 52f) + 26f) * f.dp

    /** Buttons share the row equally; in RTL the first action sits on the right. */
    private fun actionRects(f: Frame): List<Pair<Float, Float>> {
        if (actions.isEmpty()) return emptyList()
        val s = shape(f.layout)
        val gap = 10f * f.dp
        val start = f.left(s) + 18f * f.dp
        val end = f.right(s) - 18f * f.dp
        val w = (end - start - gap * (actions.size - 1)) / actions.size
        return actions.indices.map { i ->
            val r = end - i * (w + gap)
            (r - w) to r
        }
    }

    private fun actionColors(title: String): Pair<Int, Int> = when {
        CallActions.isDecline(title) -> RED to Color.WHITE
        CallActions.isAccept(title) -> GREEN to Color.WHITE
        else -> 0x33FFFFFF to Color.WHITE
    }
}

// --- islands from other apps (broadcast API) --------------------------------------------------------

class CustomCardScene(private val p: Peek.Custom, private val tabs: List<IslandTab> = emptyList()) : Scene("custom-card:${p.id}") {
    override val isCard = true
    override fun shape(l: IslandLayout) = l.card(76f * l.density)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val painter = f.p
        val dp = f.dp
        val left = f.left(s)
        val right = f.right(s)
        if (tabs.isNotEmpty()) Tabs.draw(c, f, s, tabs, alpha)
        val row = f.top + f.layout.bandHeight + 32f * dp
        val size = 48f * dp
        val ix = right - 18f * dp - size / 2f
        painter.circle(c, ix, row, size / 2f, p.color, alpha)
        painter.icon(c, R.drawable.ic_bolt, ix, row, size * 0.5f, Color.WHITE, alpha)
        val textRight = ix - size / 2f - 14f * dp
        val room = textRight - (left + 24f * dp)
        painter.label(c, p.title, textRight, row - 11f * dp, 17f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, painter.semibold, room)
        painter.label(c, p.text, textRight, row + 12f * dp, 14f * dp, SECONDARY, alpha, Paint.Align.RIGHT, painter.regular, room)
    }

    override fun tap(x: Float, y: Float, f: Frame): Tap {
        Tabs.hit(x, y, f, shape(f.layout), tabs)?.let { return Tap.Select(it.key) }
        return Tap.Dismiss
    }
}

// --- messages ------------------------------------------------------------------------------------

class MessageScene(
    private val appLabel: String,
    private val icon: Drawable?,
    private val title: String,
    private val text: String,
    private val open: PendingIntent?,
    key: String,
    private val actions: List<LiveAction> = emptyList(),
    private val canReply: Boolean = false,
    /** How many more messages wait behind this one. */
    private val more: Int = 0,
) : Scene("msg:$key") {
    override val isCard = true
    override val app get() = open
    /** The notification's buttons, and "reply" first when the app offers one. */
    private val pills: List<Pair<String, Tap>> =
        (if (canReply) listOf("השב" to Tap.Reply) else emptyList()) + actions.take(3).map { it.title to Tap.Action(it.intent) }
    override fun shape(l: IslandLayout) = l.card((76f + if (pills.isEmpty()) 0f else 52f) * l.density)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val p = f.p
        val dp = f.dp
        val left = f.left(s)
        val right = f.right(s)
        val band = f.bandY()
        val inset = 24f * dp
        p.label(c, appLabel, right - inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.medium, f.sideRoom(s, inset))
        // "+2": more messages waiting; a sideways swipe moves on to them.
        p.label(c, if (more > 0) "עכשיו · +$more" else "עכשיו", left + inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.LEFT, p.medium)

        val row = f.top + f.layout.bandHeight + 32f * dp
        val size = 48f * dp
        val ix = right - 18f * dp - size / 2f
        if (icon != null) {
            p.circle(c, ix, row, size / 2f, 0x33FFFFFF, alpha)
            p.roundDrawable(c, icon, ix, row, size, alpha)
        } else {
            p.circle(c, ix, row, size / 2f, IslandPainter.BLUE, alpha)
            p.icon(c, R.drawable.ic_chat, ix, row, size * 0.5f, Color.WHITE, alpha)
        }
        val textRight = ix - size / 2f - 14f * dp
        val room = textRight - (left + inset)
        p.label(c, title, textRight, row - 11f * dp, 17f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, p.semibold, room)
        p.label(c, text, textRight, row + 12f * dp, 14f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.regular, room)

        pillRects(f).forEachIndexed { i, (l, r) ->
            val (label, tap) = pills[i]
            val y = pillsY(f)
            val bg = if (tap is Tap.Reply) IslandPainter.BLUE else 0x33FFFFFF
            p.pill(c, l, y - 21f * dp, r, y + 21f * dp, bg, label, Color.WHITE, 15f * dp, alpha)
        }
    }

    override fun tap(x: Float, y: Float, f: Frame): Tap {
        if (pills.isNotEmpty() && abs(y - pillsY(f)) <= 26f * f.dp) {
            pillRects(f).forEachIndexed { i, (l, r) -> if (x in l..r) return pills[i].second }
        }
        return Tap.Launch(open)
    }

    private fun pillsY(f: Frame) = f.top + f.layout.bandHeight + 100f * f.dp

    /** Buttons share the row equally; the first one sits on the right. */
    private fun pillRects(f: Frame): List<Pair<Float, Float>> {
        if (pills.isEmpty()) return emptyList()
        val s = shape(f.layout)
        val gap = 10f * f.dp
        val start = f.left(s) + 18f * f.dp
        val end = f.right(s) - 18f * f.dp
        val w = (end - start - gap * (pills.size - 1)) / pills.size
        return pills.indices.map { i ->
            val r = end - i * (w + gap)
            (r - w) to r
        }
    }
}
