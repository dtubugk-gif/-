package il.rikavon.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The four bottom-bar glyphs from the canvas (stroke icons, 22dp). */
object NavIcons {
    private const val SIZE = 22f
    private const val STROKE = 1.8f
    private const val BARS_STROKE = 2.2f

    val Home: ImageVector by lazy {
        icon("nav_home") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineJoin = StrokeJoin.Round,
                fill = null,
            ) {
                moveTo(3.5f, 9.5f)
                lineTo(11f, 3f)
                lineTo(18.5f, 9.5f)
                verticalLineTo(19f)
                horizontalLineTo(13.5f)
                verticalLineTo(13.5f)
                horizontalLineTo(8.5f)
                verticalLineTo(19f)
                horizontalLineTo(3.5f)
                close()
            }
        }
    }

    val Gallery: ImageVector by lazy {
        icon("nav_gallery") {
            listOf(3f to 3f, 12f to 3f, 3f to 12f, 12f to 12f).forEach { (x, y) ->
                path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, fill = null) {
                    moveTo(x + 2f, y)
                    horizontalLineTo(x + 5f)
                    quadTo(x + 7f, y, x + 7f, y + 2f)
                    verticalLineTo(y + 5f)
                    quadTo(x + 7f, y + 7f, x + 5f, y + 7f)
                    horizontalLineTo(x + 2f)
                    quadTo(x, y + 7f, x, y + 5f)
                    verticalLineTo(y + 2f)
                    quadTo(x, y, x + 2f, y)
                    close()
                }
            }
        }
    }

    val Stats: ImageVector by lazy {
        icon("nav_stats") {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = BARS_STROKE,
                strokeLineCap = StrokeCap.Round,
                fill = null,
            ) {
                moveTo(4f, 18f)
                verticalLineTo(10f)
                moveTo(11f, 18f)
                verticalLineTo(4f)
                moveTo(18f, 18f)
                verticalLineTo(12f)
            }
        }
    }

    /** Three sliders with a ring knob each: "settings" without a gear. Used in the bar and on Home. */
    val Settings: ImageVector by lazy {
        icon("nav_settings") {
            SLIDERS.forEach { (y, knob) ->
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    fill = null,
                ) {
                    moveTo(SLIDER_START, y)
                    horizontalLineTo(knob - KNOB_GAP)
                    moveTo(knob + KNOB_GAP, y)
                    horizontalLineTo(SLIDER_END)
                }
                path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, fill = null) {
                    moveTo(knob + KNOB_RADIUS, y)
                    arcTo(
                        KNOB_RADIUS,
                        KNOB_RADIUS,
                        0f,
                        isMoreThanHalf = true,
                        isPositiveArc = true,
                        knob - KNOB_RADIUS,
                        y,
                    )
                    arcTo(
                        KNOB_RADIUS,
                        KNOB_RADIUS,
                        0f,
                        isMoreThanHalf = true,
                        isPositiveArc = true,
                        knob + KNOB_RADIUS,
                        y,
                    )
                    close()
                }
            }
        }
    }

    /** Slider rows as (y, knob x): the knobs sit at different positions so the glyph reads as controls. */
    private val SLIDERS = listOf(5.5f to 14f, 11f to 8f, 16.5f to 12.5f)
    private const val SLIDER_START = 3f
    private const val SLIDER_END = 19f
    private const val KNOB_RADIUS = 2.1f
    private const val KNOB_GAP = 3.3f

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = SIZE.dp,
                defaultHeight = SIZE.dp,
                viewportWidth = SIZE,
                viewportHeight = SIZE,
            ).apply(block)
            .build()

    @Suppress("unused")
    private val fillType = PathFillType.NonZero
}
