package io.github.dtubugk.island.island

import android.graphics.RectF
import io.github.dtubugk.island.data.IslandConfig
import io.github.dtubugk.island.data.TextSide
import kotlin.math.max
import kotlin.math.min

/** The front camera hole, in screen pixels. */
data class Hole(val centerX: Float, val centerY: Float, val diameter: Float)

/** The screen the island lives on, in pixels. [hole] is null on displays without a cutout. */
data class ScreenSpec(
    val width: Float,
    val statusBarHeight: Float,
    val density: Float,
    val hole: Hole?,
)

/**
 * A resting island shape. [offsetX] moves its center away from the camera, used when the island
 * stretches sideways to swallow Samsung's status-bar chip.
 */
data class IslandShape(val width: Float, val height: Float, val radius: Float, val offsetX: Float = 0f) {
    /** This shape grown to also cover [cover] (screen pixels), keeping the top edge. */
    fun covering(cover: RectF?, layout: IslandLayout): IslandShape {
        if (cover == null || cover.isEmpty) return this
        val pad = 4f * layout.density
        val c = layout.centerX + offsetX
        val left = minOf(c - width / 2f, cover.left - pad).coerceAtLeast(0f)
        val right = maxOf(c + width / 2f, cover.right + pad).coerceAtMost(layout.screenWidth)
        val h = maxOf(height, cover.bottom + pad - layout.top)
        return IslandShape(right - left, h, h / 2f, (left + right) / 2f - layout.centerX)
    }
}

/**
 * Resolved island geometry in screen pixels. Every shape is horizontally centered on [centerX]
 * and hangs from [top], so morphing between shapes only ever grows or shrinks around that anchor.
 */
data class IslandLayout(
    val centerX: Float,
    val top: Float,
    val idle: IslandShape,
    /** Island with something on each side of the camera: music, a call, a timer. */
    val compact: IslandShape,
    /** Wide pill for short notices: charging, silent mode, headphones. */
    val charging: IslandShape,
    /** The tap-open info card; other cards share its width and corner radius. */
    val expanded: IslandShape,
    /** Height of the band at the top of the expanded card that holds the camera. */
    val bandHeight: Float,
    /** Camera position relative to the island: x from [centerX], y from [top]. */
    val holeOffsetX: Float,
    val holeCenterY: Float,
    val holeRadius: Float,
    /** Where the idle text sits: its center, relative to [centerX]. */
    val textCenterX: Float,
    val density: Float,
    /** Screen width in pixels, to keep stretched shapes on screen. */
    val screenWidth: Float = 0f,
) {
    /** A card as wide as [expanded], with [contentHeight] below the camera band. */
    fun card(contentHeight: Float): IslandShape {
        val h = bandHeight + contentHeight
        return IslandShape(expanded.width, h, min(expanded.radius, h / 2f))
    }
}

object IslandGeometry {
    private const val TEXT_GAP_DP = 5f
    private const val SCREEN_MARGIN_DP = 8f
    private const val CARD_MAX_WIDTH_DP = 440f
    private const val CARD_ROW_DP = 64f
    private const val CARD_BOTTOM_DP = 12f
    private const val CARD_RADIUS_DP = 36f
    private const val CHARGING_MIN_WIDTH_DP = 232f
    private const val COMPACT_MIN_WIDTH_DP = 168f

    /** Gap between the text's outer edge and the island's end. Centers a single glyph in the round cap. */
    fun textInset(height: Float, textWidth: Float): Float = max((height - textWidth) / 2f, 0.3f * height)

    /**
     * The narrowest idle island that fits the camera plus the text on one side, keeping the
     * island centered on the camera so it reads as one piece of hardware.
     */
    fun minIdleWidth(height: Float, holeDiameter: Float, textWidth: Float, density: Float): Float {
        if (textWidth <= 0f) return max(height * 1.6f, holeDiameter + height)
        val half = holeDiameter / 2f + TEXT_GAP_DP * density + textWidth + textInset(height, textWidth)
        return 2f * half
    }

    fun compute(screen: ScreenSpec, config: IslandConfig, textWidth: Float): IslandLayout {
        val dp = screen.density
        val margin = SCREEN_MARGIN_DP * dp
        val holeD = screen.hole?.diameter ?: 0f
        val cameraX = screen.hole?.centerX ?: (screen.width / 2f)
        val cameraY = screen.hole?.centerY ?: (screen.statusBarHeight / 2f)

        // The island must always swallow the camera, whatever the slider says, with a black
        // margin wide enough that the lens edge never peeks out if the reported hole is a bit off.
        val minHeight = holeD + 10f * dp
        val height = max(config.heightDp.coerceIn(IslandConfig.MIN_HEIGHT_DP, IslandConfig.MAX_HEIGHT_DP) * dp, minHeight)

        val maxWidth = screen.width - 2f * margin
        val minWidth = min(minIdleWidth(height, holeD, textWidth, dp), maxWidth)
        val width = (config.widthDp * dp).coerceIn(minWidth, maxWidth)

        val halfRoom = width / 2f + margin
        val centerX = if (screen.width >= 2f * halfRoom) {
            (cameraX + config.offsetXDp * dp).coerceIn(halfRoom, screen.width - halfRoom)
        } else {
            screen.width / 2f
        }
        val top = max(0f, cameraY + config.offsetYDp * dp - height / 2f)

        val cardRoom = 2f * min(centerX, screen.width - centerX) - 2f * margin
        val cardWidth = max(width, min(min(CARD_MAX_WIDTH_DP * dp, maxWidth), cardRoom))
        val bandHeight = max(height, 30f * dp)
        val cardHeight = bandHeight + (CARD_ROW_DP + CARD_BOTTOM_DP) * dp
        val chargingWidth = min(max(width, CHARGING_MIN_WIDTH_DP * dp), cardWidth)
        val compactWidth = min(max(width, COMPACT_MIN_WIDTH_DP * dp), cardWidth)

        val inset = textInset(height, textWidth)
        val textCenter = width / 2f - inset - textWidth / 2f

        return IslandLayout(
            centerX = centerX,
            top = top,
            idle = IslandShape(width, height, height / 2f),
            compact = IslandShape(compactWidth, height, height / 2f),
            charging = IslandShape(chargingWidth, height, height / 2f),
            expanded = IslandShape(cardWidth, cardHeight, min(CARD_RADIUS_DP * dp, cardHeight / 2f)),
            bandHeight = bandHeight,
            holeOffsetX = cameraX - centerX,
            holeCenterY = cameraY - top,
            holeRadius = holeD / 2f,
            textCenterX = if (config.textSide == TextSide.RIGHT) textCenter else -textCenter,
            density = dp,
            screenWidth = screen.width,
        )
    }
}
