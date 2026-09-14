package il.rikavon.core.ui.anim

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// Motion helpers shared by every screen. All of them animate only transform and opacity, read their timings
// from AnimationSpecs, and collapse to a short fade (or nothing) when LocalReducedMotion is on.

/** Row [index] fades in and rises from below with a capped stagger; re-runs when [key] changes. */
@Composable
fun Modifier.enterFromBelow(index: Int, key: Any? = Unit): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        if (reduced) {
            progress.animateTo(1f, AnimationSpecs.Reduced)
        } else {
            delay(
                index.coerceAtMost(AnimationSpecs.LIST_STAGGER_MAX_INDEX) * AnimationSpecs.LIST_STAGGER_MILLIS.toLong(),
            )
            progress.animateTo(1f, AnimationSpecs.ListEnter)
        }
    }
    val offsetPx = with(LocalDensity.current) { AnimationSpecs.LIST_ENTER_OFFSET_DP.dp.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = if (reduced) 0f else (1f - p) * offsetPx
    }
}

/** Scales in from 80 % with a light spring and fades; the standard way for something to appear. */
@Composable
fun Modifier.popIn(key: Any? = Unit): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.animateTo(1f, if (reduced) AnimationSpecs.Reduced else AnimationSpecs.PopIn)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val s = if (reduced) 1f else AnimationSpecs.POP_IN_FROM_SCALE + (1f - AnimationSpecs.POP_IN_FROM_SCALE) * p
        scaleX = s
        scaleY = s
    }
}

/** A slow vertical float (sine), for the living mascot on the home screen. Off under reduced motion. */
@Composable
fun Modifier.floatLoop(): Modifier {
    val reduced = LocalReducedMotion.current
    if (reduced) return this
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        phase.animateTo(
            1f,
            infiniteRepeatable(tween(AnimationSpecs.FLOAT_LOOP_MILLIS, easing = LinearEasing)),
        )
    }
    val amplitudePx = with(LocalDensity.current) { AnimationSpecs.FLOAT_AMPLITUDE_DP.dp.toPx() }
    return graphicsLayer { translationY = (sin(phase.value * 2f * PI.toFloat()) * amplitudePx) }
}

/** A brief scale bounce every time [trigger] changes (bottom-bar icons, streak pill). */
@Composable
fun Modifier.bounceOn(trigger: Any?, peak: Float = AnimationSpecs.NAV_BOUNCE_SCALE): Modifier {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (!armed) {
            armed = true
            return@LaunchedEffect
        }
        if (reduced) return@LaunchedEffect
        scale.snapTo(peak)
        scale.animateTo(1f, AnimationSpecs.NavBounce)
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Counts from the previous value to [value]; colour follows along. */
@Composable
fun AnimatedNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
) {
    val reduced = LocalReducedMotion.current
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = if (reduced) AnimationSpecs.Reduced else AnimationSpecs.Count,
        label = "number",
    )
    Text(text = format(animated.roundToInt()), style = style, color = color, modifier = modifier)
}

/** Content transform for a value that ticks (clock, counter): the new value slides up over the old one. */
fun <T> tickTransform(reduced: Boolean): AnimatedContentTransitionScope<T>.() -> ContentTransform =
    {
        if (reduced) {
            fadeIn(AnimationSpecs.Reduced) togetherWith fadeOut(AnimationSpecs.Reduced)
        } else {
            val slide = tween<IntOffset>(AnimationSpecs.COMPONENT_MILLIS, easing = AnimationSpecs.Emphasized)
            (fadeIn(AnimationSpecs.Component) + slideInVertically(slide) { it / TICK_FRACTION }) togetherWith
                (fadeOut(AnimationSpecs.Component) + slideOutVertically(slide) { -it / TICK_FRACTION })
        }
    }

/** Fade-through for swapping one block of content for another (M3): fade + scale 0.96 → 1. */
fun <T> fadeThroughTransform(reduced: Boolean): AnimatedContentTransitionScope<T>.() -> ContentTransform =
    {
        if (reduced) {
            fadeIn(AnimationSpecs.Reduced) togetherWith fadeOut(AnimationSpecs.Reduced)
        } else {
            (
                fadeIn(tween(AnimationSpecs.TAB_SWITCH_MILLIS, easing = AnimationSpecs.Emphasized)) +
                    scaleIn(
                        tween(AnimationSpecs.TAB_SWITCH_MILLIS, easing = AnimationSpecs.Emphasized),
                        initialScale = AnimationSpecs.TAB_SWITCH_SCALE,
                    )
            ) togetherWith
                (
                    fadeOut(tween(AnimationSpecs.MICRO_MILLIS, easing = FastOutSlowInEasing)) +
                        scaleOut(tween(AnimationSpecs.MICRO_MILLIS), targetScale = AnimationSpecs.TAB_SWITCH_SCALE)
                )
        }
    }

/** Directional page transform (onboarding): forward slides in from the end, back from the start. */
fun <T> pageTransform(forward: Boolean, reduced: Boolean): AnimatedContentTransitionScope<T>.() -> ContentTransform =
    {
        if (reduced) {
            fadeIn(AnimationSpecs.Reduced) togetherWith fadeOut(AnimationSpecs.Reduced)
        } else {
            val direction = if (forward) SlideDirection.Start else SlideDirection.End
            (
                fadeIn(tween(AnimationSpecs.SCREEN_TRANSITION_MILLIS)) +
                    slideIntoContainer(
                        direction,
                        tween(AnimationSpecs.SCREEN_TRANSITION_MILLIS, easing = AnimationSpecs.Emphasized),
                    ) {
                        it /
                            PAGE_FRACTION
                    }
            ) togetherWith
                (
                    fadeOut(tween(AnimationSpecs.COMPONENT_MILLIS)) +
                        slideOutOfContainer(
                            direction,
                            tween(AnimationSpecs.SCREEN_TRANSITION_MILLIS, easing = AnimationSpecs.Emphasized),
                        ) {
                            it /
                                PAGE_FRACTION
                        }
                )
        }
    }

/** Wraps [AnimatedContent] with the fade-through transform. */
@Composable
fun <T> FadeThrough(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "fadeThrough",
    content: @Composable (T) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = targetState,
        transitionSpec = fadeThroughTransform(reduced),
        label = label,
        modifier = modifier,
    ) { content(it) }
}

/** Runs [block] once per [key] change after the enter animation window, for chained choreography. */
@Composable
fun AfterEnter(key: Any?, block: suspend () -> Unit) {
    LaunchedEffect(key) {
        launch {
            delay(AnimationSpecs.LIST_ENTER_MILLIS.toLong())
            block()
        }
    }
}

/** Enter/exit pair for elements that appear inside a column (granted pill, next-limit line). */
fun popEnter(reduced: Boolean): EnterTransition =
    if (reduced) {
        fadeIn(AnimationSpecs.Reduced)
    } else {
        fadeIn(AnimationSpecs.Component) +
            scaleIn(AnimationSpecs.PopIn, initialScale = AnimationSpecs.POP_IN_FROM_SCALE)
    }

fun popExit(reduced: Boolean): ExitTransition =
    if (reduced) {
        fadeOut(AnimationSpecs.Reduced)
    } else {
        fadeOut(AnimationSpecs.Micro) +
            scaleOut(AnimationSpecs.Micro, targetScale = AnimationSpecs.POP_IN_FROM_SCALE)
    }

private const val TICK_FRACTION = 3
private const val PAGE_FRACTION = 4
