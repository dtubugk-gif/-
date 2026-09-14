package il.rikavon.feature.mascot.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import kotlin.math.sin

/**
 * Used only when a mascot folder ships without a Lottie file for a stage. Still alive: it breathes when
 * healthy and twitches when rotten. Never a static image.
 */
@Composable
fun FallbackMascot(skin: MascotSkin, stage: MascotStage, playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fallbackIdle")
    val periodMillis = lerp(ROTTEN_PERIOD_MILLIS, HEALTHY_PERIOD_MILLIS, stage.health).toInt()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val base = Color(skin.themeColorArgb)
    val body = lerp(ROT_COLOR, base, stage.health)
    Canvas(modifier = modifier) {
        val t = if (playing) phase else 0f
        val breath = 1f + BREATH_AMOUNT * stage.health * sin(t * TWO_PI)
        val twitch = (1f - stage.health) * TWITCH_AMOUNT * sin(t * TWO_PI * TWITCH_FREQ)
        val w = size.width * BODY_FRACTION * breath
        val h = size.height * BODY_FRACTION * (2f - breath)
        val left = (size.width - w) / 2 + twitch * size.width
        val top = (size.height - h) / 2
        drawOval(color = body, topLeft = Offset(left, top), size = Size(w, h))
        val eyeY = top + h * EYE_Y
        val eyeR = w * EYE_RADIUS
        drawCircle(EYE_COLOR, eyeR, Offset(left + w * EYE_LEFT_X, eyeY))
        drawCircle(EYE_COLOR, eyeR, Offset(left + w * EYE_RIGHT_X, eyeY))
    }
}

private val ROT_COLOR = Color(0xFF4B5A3A)
private val EYE_COLOR = Color(0xFF0D0E11)
private const val HEALTHY_PERIOD_MILLIS = 3200f
private const val ROTTEN_PERIOD_MILLIS = 900f
private const val BREATH_AMOUNT = 0.04f
private const val TWITCH_AMOUNT = 0.03f
private const val TWITCH_FREQ = 5f
private const val BODY_FRACTION = 0.72f
private const val EYE_Y = 0.42f
private const val EYE_RADIUS = 0.06f
private const val EYE_LEFT_X = 0.36f
private const val EYE_RIGHT_X = 0.64f
private const val TWO_PI = (2 * Math.PI).toFloat()
