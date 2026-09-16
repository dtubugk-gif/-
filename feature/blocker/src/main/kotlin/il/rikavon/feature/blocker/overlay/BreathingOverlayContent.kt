package il.rikavon.feature.blocker.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.LocalReducedMotion
import il.rikavon.core.ui.anim.popIn
import il.rikavon.core.ui.anim.tickTransform
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.RikavonTheme
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.theme.schemeFromAccent
import il.rikavon.feature.blocker.R
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.ui.MascotView
import kotlinx.coroutines.delay

/**
 * The breathing pause: shown over an app the first time it opens after its limit changed. A circle that
 * swells and shrinks with a four-second breath, the pet inside it, "breathe in / breathe out", and a
 * countdown. Only when it reaches zero does the way in open; leaving is always one tap away.
 */
@Composable
fun BreathingOverlayContent(
    skin: MascotSkin,
    appLabel: String,
    seconds: Int,
    reducedMotion: Boolean,
    onEnter: () -> Unit,
    onLeave: () -> Unit,
) {
    val scheme = schemeFromAccent(Color(skin.themeColorArgb), skin.surfaceTintArgb?.let { Color(it) })
    RikavonTheme(scheme = scheme, reducedMotion = reducedMotion) {
        var remaining by remember { mutableIntStateOf(seconds) }
        var inhale by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(SECOND_MILLIS)
                remaining--
            }
        }
        LaunchedEffect(reducedMotion) {
            if (reducedMotion) return@LaunchedEffect
            while (true) {
                delay(AnimationSpecs.BREATH_HALF_CYCLE_MILLIS.toLong())
                inhale = !inhale
            }
        }
        val breath = rememberInfiniteTransition(label = "breath")
        val scale by
            breath.animateFloat(
                initialValue = if (reducedMotion) 1f else AnimationSpecs.BREATH_MIN_SCALE,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        tween(AnimationSpecs.BREATH_HALF_CYCLE_MILLIS, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                label = "breathScale",
            )
        val extras = LocalExtraColors.current
        val done = remaining == 0
        val phaseRes =
            when {
                reducedMotion -> R.string.breathe_hold
                inhale -> R.string.breathe_in
                else -> R.string.breathe_out
            }
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = ScreenPadding, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.breathe_headline),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.breathe_body, appLabel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = extras.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
                )
                Spacer(Modifier.height(Spacing.xxl))
                Box(modifier = Modifier.size(CIRCLE_SIZE), contentAlignment = Alignment.Center) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    )
                    MascotView(
                        skin = skin,
                        stage = MascotStage.PRISTINE,
                        interactive = false,
                        modifier = Modifier.size(MASCOT_SIZE),
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    text = stringResource(phaseRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Spacing.lg))
                if (done) {
                    PrimaryButton(
                        text = stringResource(R.string.breathe_enter),
                        onClick = onEnter,
                        modifier = Modifier.popIn().widthIn(min = BUTTON_MIN_WIDTH),
                    )
                } else {
                    AnimatedContent(
                        targetState = remaining,
                        transitionSpec = tickTransform(LocalReducedMotion.current),
                        label = "countdown",
                    ) { value ->
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                LinkButton(text = stringResource(R.string.breathe_leave), onClick = onLeave)
            }
        }
    }
}

private const val SECOND_MILLIS = 1_000L
private val TEXT_MAX_WIDTH = 320.dp
private val CIRCLE_SIZE = 220.dp
private val MASCOT_SIZE = 128.dp
private val BUTTON_MIN_WIDTH = 160.dp
