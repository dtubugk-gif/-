package il.rikavon.feature.mascot.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import il.rikavon.core.ui.anim.AnimationSpecs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(val angle: Float, val distance: Float, val radius: Float, val hueShift: Float)

/** A radial burst of dots fired whenever [serial] changes. Skipped entirely under reduced motion. */
@Composable
fun ParticleBurst(serial: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }
    val spreadPx = with(LocalDensity.current) { AnimationSpecs.PARTICLE_SPREAD_DP.dp.toPx() }
    val maxRadiusPx = with(LocalDensity.current) { MAX_RADIUS_DP.dp.toPx() }

    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        val random = Random(serial)
        particles =
            List(AnimationSpecs.PARTICLE_COUNT) {
                Particle(
                    angle = random.nextFloat() * TWO_PI,
                    distance = spreadPx * (MIN_DISTANCE + random.nextFloat() * (1f - MIN_DISTANCE)),
                    radius = maxRadiusPx * (MIN_RADIUS + random.nextFloat() * (1f - MIN_RADIUS)),
                    hueShift = random.nextFloat(),
                )
            }
        progress.snapTo(0f)
        progress.animateTo(1f, AnimationSpecs.Particle)
        particles = emptyList()
    }

    if (particles.isEmpty()) return
    Canvas(modifier = modifier) {
        val p = progress.value
        val center = Offset(size.width / 2, size.height / 2)
        particles.forEach { particle ->
            val d = particle.distance * p
            val pos = center + Offset(cos(particle.angle) * d, sin(particle.angle) * d - GRAVITY * p * p * spreadPx)
            drawCircle(
                color = color.copy(alpha = (1f - p) * ALPHA_MAX),
                radius = particle.radius * (1f - p * SHRINK),
                center = pos,
            )
        }
    }
}

private const val TWO_PI = (2 * PI).toFloat()
private const val MIN_DISTANCE = 0.45f
private const val MIN_RADIUS = 0.35f
private const val MAX_RADIUS_DP = 7
private const val GRAVITY = 0.25f
private const val ALPHA_MAX = 0.95f
private const val SHRINK = 0.6f
