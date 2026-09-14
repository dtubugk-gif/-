package il.rikavon.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import il.rikavon.core.ui.R
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.LocalReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Bar(val label: String, val value: Float, val valueText: String, val highlighted: Boolean = false)

/**
 * A bar chart that draws itself: each bar grows in with a 40ms stagger. RTL-aware: the first bar is
 * on the trailing edge for right-to-left layouts, matching reading order.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    mutedColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    threshold: Float? = null,
    thresholdColor: Color = MaterialTheme.colorScheme.error,
    labelEvery: Int = 1,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember(bars.size) { bars.map { Animatable(0f) } }
    LaunchedEffect(bars) {
        progress.forEachIndexed { index, animatable ->
            launch {
                if (reduced) {
                    animatable.snapTo(1f)
                } else {
                    delay(index.toLong() * AnimationSpecs.CHART_STAGGER_MILLIS)
                    animatable.animateTo(1f, AnimationSpecs.ChartBar)
                }
            }
        }
    }
    val barFormat = stringResource(R.string.core_ui_chart_bar)
    val description =
        stringResource(
            R.string.core_ui_chart_description,
            bars.joinToString { barFormat.format(it.label, it.valueText) },
        )
    val layoutDirection = LocalLayoutDirection.current
    val maxValue = (bars.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(threshold ?: 0f).coerceAtLeast(1f)

    Column(modifier = modifier.semantics { contentDescription = description }) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height),
        ) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val barWidth = slot * BAR_FILL
            val corner = CornerRadius(barWidth / CORNER_DIVISOR)
            bars.forEachIndexed { index, bar ->
                val visualIndex = if (layoutDirection == LayoutDirection.Rtl) bars.size - 1 - index else index
                val fraction = (bar.value / maxValue).coerceIn(0f, 1f) * progress[index].value
                val barHeight = (size.height * fraction).coerceAtLeast(MIN_BAR_PX)
                val left = visualIndex * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (bar.highlighted) barColor else mutedColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
            threshold?.let { t ->
                val y = size.height - size.height * (t / maxValue).coerceIn(0f, 1f)
                drawLine(
                    color = thresholdColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = THRESHOLD_STROKE,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEachIndexed { index, bar ->
                Text(
                    text = if (index % labelEvery == 0) bar.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(top = 6.dp),
                )
            }
        }
    }
}

private const val BAR_FILL = 0.62f
private const val CORNER_DIVISOR = 3f
private const val MIN_BAR_PX = 4f
private const val THRESHOLD_STROKE = 3f
