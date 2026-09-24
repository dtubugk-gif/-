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
    /** Redraw cadence while visible, for waveforms and running clocks. 0 = static. */
    open val refreshMs: Long = 0L
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
        val color = IslandColors.of(config.colorIndex)
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
) : Scene("info") {
    override val isCard = true
    override fun shape(l: IslandLayout) = l.expanded

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
        p.fill.shader = LinearGradient(tileLeft, row - tile / 2f, tileRight, row + tile / 2f, IslandColors.GRADIENT_START, IslandColors.GRADIENT_END, Shader.TileMode.CLAMP)
        p.fill.alpha = (alpha * 255).roundToInt()
        c.drawRoundRect(tileLeft, row - tile / 2f, tileRight, row + tile / 2f, 16f * dp, 16f * dp, p.fill)
        p.fill.shader = null
        val avatar = config.text.ifBlank { IslandConfig.DEFAULT_TEXT }
        val fit = min(28f * dp, 28f * dp * (tile - 14f * dp) / max(p.measure(avatar, 28f * dp, p.bold), 1f))
        p.label(c, avatar, tileLeft + tile / 2f, row, fit, Color.WHITE, alpha, Paint.Align.CENTER, p.bold)

        val gx = left + 36f * dp
        p.circle(c, gx, row, 20f * dp, 0x24FFFFFF, alpha)
        p.icon(c, R.drawable.ic_island_settings, gx, row, 20f * dp, Color.WHITE, alpha * 0.9f)

        val textRight = tileLeft - 14f * dp
        val room = textRight - (gx + 32f * dp)
        p.label(c, greeting, textRight, row - 10f * dp, 17f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, p.semibold, room)
        p.label(c, date, textRight, row + 12f * dp, 13.5f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.regular, room)
    }

    override fun tap(x: Float, y: Float, f: Frame): Tap {
        val row = f.top + f.layout.bandHeight + 32f * f.dp
        return if (near(x, y, f.left(shape(f.layout)) + 36f * f.dp, row, 28f * f.dp)) Tap.Settings else Tap.Collapse
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
) : Scene(key) {
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

    override fun tap(x: Float, y: Float, f: Frame) = Tap.Dismiss
}

// --- music -------------------------------------------------------------------------------------

/** Artwork by the camera on the right, live waveform on the left: the classic island. */
class MediaCompactScene(
    private val media: MediaState,
    /** Samsung's own chip for this song, in screen pixels: the island stretches over it. */
    private val cover: RectF? = null,
) : Scene("media-c:${media.packageName}") {
    override val refreshMs get() = if (media.playing) FRAME_MS else 0L
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
class MediaCardScene(private val media: MediaState) : Scene("media-card:${media.packageName}") {
    override val isCard = true
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
        p.waveform(c, left + inset + 11f * dp, band, 22f * dp, 14f * dp, media.accent, media.playing, f.animMs, alpha)

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

private fun LiveActivity.accent() = if (kind == LiveKind.CALL) GREEN else ORANGE
private fun LiveActivity.iconRes() = if (kind == LiveKind.CALL) R.drawable.ic_call else R.drawable.ic_timer
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
    override fun shape(l: IslandLayout) = l.compact.covering(cover, l)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val h = s.height
        val mid = f.top + h / 2f
        val p = f.p
        p.icon(c, activity.iconRes(), f.right(s) - h / 2f, mid, h * 0.58f, activity.accent(), alpha)
        val inset = h * 0.42f
        val clock = activity.clock(f.nowMs)
        if (clock != null) {
            p.label(c, clock, f.left(s) + inset, mid, h * 0.46f, activity.accent(), alpha, Paint.Align.LEFT, p.semibold, f.sideRoom(s, inset))
        } else {
            p.label(c, activity.title, f.left(s) + inset, mid, h * 0.4f, Color.WHITE, alpha, Paint.Align.LEFT, p.medium, f.sideRoom(s, inset))
        }
    }

    override fun tap(x: Float, y: Float, f: Frame) = Tap.Expand
}

class LiveCardScene(private val activity: LiveActivity) : Scene("live-card:${activity.key}") {
    override val isCard = true
    override val refreshMs get() = if (activity.chronometerBase > 0L) 1000L else 0L
    private val actions = activity.actions.take(3)
    override fun shape(l: IslandLayout) = l.card((if (actions.isEmpty()) 76f else 128f) * l.density)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val p = f.p
        val dp = f.dp
        val left = f.left(s)
        val right = f.right(s)
        val band = f.bandY()
        val inset = 24f * dp
        p.label(c, activity.appLabel, right - inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.medium, f.sideRoom(s, inset))
        activity.clock(f.nowMs)?.let {
            p.label(c, it, left + inset, band, 15f * dp, activity.accent(), alpha, Paint.Align.LEFT, p.semibold)
        }

        val row = f.top + f.layout.bandHeight + 32f * dp
        val icon = 52f * dp
        val ix = right - 18f * dp - icon / 2f
        p.circle(c, ix, row, icon / 2f, activity.accent(), alpha)
        p.icon(c, activity.iconRes(), ix, row, icon * 0.5f, Color.WHITE, alpha)
        val textRight = ix - icon / 2f - 14f * dp
        val room = textRight - (left + inset)
        p.label(c, activity.title, textRight, row - 11f * dp, 17f * dp, Color.WHITE, alpha, Paint.Align.RIGHT, p.semibold, room)
        p.label(c, activity.text, textRight, row + 12f * dp, 14f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.regular, room)

        actionRects(f).forEachIndexed { i, (l, r) ->
            val a = actions[i]
            val (bg, fg) = actionColors(a.title)
            val y = actionsY(f)
            p.pill(c, l, y - 21f * dp, r, y + 21f * dp, bg, a.title, fg, 15f * dp, alpha)
        }
    }

    override fun tap(x: Float, y: Float, f: Frame): Tap {
        if (abs(y - actionsY(f)) <= 26f * f.dp) {
            actionRects(f).forEachIndexed { i, (l, r) -> if (x in l..r) return Tap.Launch(actions[i].intent) }
        }
        return Tap.Launch(activity.openApp)
    }

    private fun actionsY(f: Frame) = f.top + f.layout.bandHeight + 100f * f.dp

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

// --- messages ------------------------------------------------------------------------------------

class MessageScene(
    private val appLabel: String,
    private val icon: Drawable?,
    private val title: String,
    private val text: String,
    private val open: PendingIntent?,
    key: String,
) : Scene("msg:$key") {
    override val isCard = true
    override fun shape(l: IslandLayout) = l.card(76f * l.density)

    override fun draw(c: Canvas, f: Frame, alpha: Float) {
        val s = shape(f.layout)
        val p = f.p
        val dp = f.dp
        val left = f.left(s)
        val right = f.right(s)
        val band = f.bandY()
        val inset = 24f * dp
        p.label(c, appLabel, right - inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.RIGHT, p.medium, f.sideRoom(s, inset))
        p.label(c, "עכשיו", left + inset, band, 13f * dp, SECONDARY, alpha, Paint.Align.LEFT, p.medium)

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
    }

    override fun tap(x: Float, y: Float, f: Frame) = Tap.Launch(open)
}
