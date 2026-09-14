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

    val Settings: ImageVector by lazy {
        icon("nav_settings") {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = STROKE, fill = null) {
                moveTo(14f, 11f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8f, 11f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14f, 11f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                fill = null,
            ) {
                moveTo(11f, 2.5f)
                verticalLineTo(5.1f)
                moveTo(11f, 16.9f)
                verticalLineTo(19.5f)
                moveTo(19.5f, 11f)
                horizontalLineTo(16.9f)
                moveTo(5.1f, 11f)
                horizontalLineTo(2.5f)
            }
        }
    }

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
