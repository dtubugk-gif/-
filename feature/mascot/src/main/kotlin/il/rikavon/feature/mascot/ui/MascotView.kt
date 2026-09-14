package il.rikavon.feature.mascot.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.LocalReducedMotion
import il.rikavon.feature.mascot.R
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import kotlinx.coroutines.launch

/** One-shot effects the host can fire at the mascot. */
enum class MascotEffect { SCORE_UP, SCORE_DOWN }

/** Bump [serial] to fire [effect] again. */
data class MascotEffectTrigger(val effect: MascotEffect, val serial: Int)

enum class MascotEntrance { NONE, DRAMATIC }

/**
 * The living mascot. Always animating (Lottie idle loop per stage), morphs between stages, reacts to taps
 * and long presses, celebrates or mourns score changes, and honours reduced-motion.
 *
 * Idle playback stops automatically when the hosting lifecycle is not resumed (screen off, background).
 */
@Composable
fun MascotView(
    skin: MascotSkin,
    stage: MascotStage,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    entrance: MascotEntrance = MascotEntrance.NONE,
    effectTrigger: MascotEffectTrigger? = null,
    textForTap: (() -> String)? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onEffectSound: ((MascotEffect) -> Unit)? = null,
    sharedKey: String? = null,
) {
    val reduced = LocalReducedMotion.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val squash = remember { Animatable(0f) }
    val reaction = remember { Animatable(0f) }
    val entranceProgress = remember { Animatable(if (entrance == MascotEntrance.DRAMATIC) 0f else 1f) }
    val effectPop = remember { Animatable(0f) }
    val effectDim = remember { Animatable(0f) }
    var particleSerial by remember { mutableStateOf(0) }
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }
    var textSerial by remember { mutableStateOf(0) }

    LaunchedEffect(entrance) {
        if (entrance == MascotEntrance.DRAMATIC) {
            entranceProgress.snapTo(0f)
            entranceProgress.animateTo(1f, if (reduced) AnimationSpecs.Reduced else AnimationSpecs.BlockEntranceDrop)
        }
    }

    LaunchedEffect(effectTrigger) {
        val trigger = effectTrigger ?: return@LaunchedEffect
        onEffectSound?.invoke(trigger.effect)
        when (trigger.effect) {
            MascotEffect.SCORE_UP -> {
                if (!reduced) particleSerial++
                effectPop.snapTo(1f)
                effectPop.animateTo(0f, if (reduced) AnimationSpecs.Reduced else AnimationSpecs.ScoreUpPop)
            }
            MascotEffect.SCORE_DOWN -> {
                effectDim.snapTo(1f)
                effectDim.animateTo(0f, if (reduced) AnimationSpecs.Reduced else AnimationSpecs.ScoreDownFade)
            }
        }
    }

    val name = skin.name.resolve(UiLanguage.current()) ?: skin.id
    val description = stringResource(R.string.mascot_content_description, name, stageLabel(stage))

    val gestureModifier =
        if (interactive) {
            Modifier.pointerInput(skin.id, stage) {
                detectTapGestures(
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTap?.invoke()
                        textForTap?.invoke()?.takeIf { it.isNotBlank() }?.let { text ->
                            floatingTexts += FloatingText(textSerial++, text)
                        }
                        scope.launch {
                            squash.snapTo(1f)
                            squash.animateTo(0f, if (reduced) AnimationSpecs.Reduced else AnimationSpecs.TapSquash)
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress?.invoke()
                        if (!reduced) {
                            scope.launch {
                                reaction.snapTo(0f)
                                reaction.animateTo(1f, AnimationSpecs.Reaction)
                                reaction.snapTo(0f)
                            }
                        }
                    },
                )
            }
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .semantics {
                    contentDescription = description
                    if (interactive) role = Role.Button
                }.then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        val dropPx = with(density) { AnimationSpecs.BLOCK_ENTRANCE_DROP_DP.dp.toPx() }
        val transform = remember(skin.reaction) { ReactionTransforms.forPreset(skin.reaction) }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = squash.value
                        val e = entranceProgress.value
                        val pop = effectPop.value
                        val r = transform(reaction.value, size.width)
                        scaleX =
                            lerp(1f, AnimationSpecs.TAP_SQUASH_X, s) * (1f + pop * POP_SCALE) * r.scaleX *
                            (if (reduced) 1f else e)
                        scaleY =
                            lerp(1f, AnimationSpecs.TAP_SQUASH_Y, s) * (1f + pop * POP_SCALE) * r.scaleY *
                            (if (reduced) 1f else e)
                        translationY = (if (reduced) 0f else -(1f - e) * dropPx) + r.translationY
                        translationX = r.translationX
                        rotationZ =
                            (if (reduced) 0f else (1f - e) * AnimationSpecs.BLOCK_ENTRANCE_ROTATION) + r.rotationZ
                        rotationY = r.rotationY
                        cameraDistance = CAMERA_DISTANCE * density.density
                        alpha =
                            (if (reduced) e else minOf(1f, e * ENTRANCE_FADE_BOOST)) * r.alpha *
                            (1f - effectDim.value * DIM_AMOUNT)
                    }.then(sharedElementModifier(sharedKey)),
        ) {
            StageMorph(skin = skin, stage = stage, playing = playing, reduced = reduced)
        }

        if (!reduced) {
            ParticleBurst(
                serial = particleSerial,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
        }

        floatingTexts.forEach { item ->
            FloatingTextView(
                item = item,
                reduced = reduced,
                onDone = { floatingTexts.remove(item) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun StageMorph(skin: MascotSkin, stage: MascotStage, playing: Boolean, reduced: Boolean) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = { morphTransition(reduced) },
        label = "mascotStage",
    ) { target ->
        StageLottie(skin = skin, stage = target, playing = playing)
    }
}

private fun morphTransition(reduced: Boolean): ContentTransform =
    if (reduced) {
        fadeIn(AnimationSpecs.Reduced) togetherWith fadeOut(AnimationSpecs.Reduced)
    } else {
        (
            fadeIn(AnimationSpecs.StageMorph) +
                scaleIn(AnimationSpecs.StageMorph, initialScale = AnimationSpecs.STAGE_MORPH_SCALE_IN)
        ) togetherWith (
            fadeOut(AnimationSpecs.StageMorph) +
                scaleOut(AnimationSpecs.StageMorph, targetScale = AnimationSpecs.STAGE_MORPH_SCALE_OUT)
        )
    }

@Composable
private fun StageLottie(skin: MascotSkin, stage: MascotStage, playing: Boolean) {
    val asset = skin.stage(stage).lottieAsset
    if (asset == null) {
        FallbackMascot(skin = skin, stage = stage, playing = playing, modifier = Modifier.fillMaxSize())
        return
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(asset))
    val speed = lerp(AnimationSpecs.IDLE_SPEED_ROTTEN, AnimationSpecs.IDLE_SPEED_HEALTHY, stage.health)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = playing,
        speed = speed,
        restartOnPlay = false,
    )
    if (composition == null) {
        FallbackMascot(skin = skin, stage = stage, playing = playing, modifier = Modifier.fillMaxSize())
    } else {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun stageLabel(stage: MascotStage): String =
    stringResource(
        when (stage) {
            MascotStage.PRISTINE -> R.string.mascot_stage_pristine
            MascotStage.FRESH -> R.string.mascot_stage_fresh
            MascotStage.WORN -> R.string.mascot_stage_worn
            MascotStage.WILTED -> R.string.mascot_stage_wilted
            MascotStage.ROTTING -> R.string.mascot_stage_rotting
            MascotStage.ROTTEN -> R.string.mascot_stage_rotten
        },
    )

data class FloatingText(val id: Int, val text: String)

@Composable
private fun FloatingTextView(item: FloatingText, reduced: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val rise = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val risePx = with(LocalDensity.current) { AnimationSpecs.FLOAT_TEXT_RISE_DP.dp.toPx() }
    LaunchedEffect(item.id) {
        launch {
            rise.animateTo(1f, if (reduced) tween(AnimationSpecs.REDUCED_MILLIS) else AnimationSpecs.FloatTextRise)
        }
        alpha.animateTo(0f, if (reduced) tween(AnimationSpecs.FLOAT_TEXT_MILLIS) else AnimationSpecs.FloatTextFade)
        onDone()
    }
    Text(
        text = item.text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier =
            modifier.graphicsLayer {
                translationY = -rise.value * (if (reduced) 0f else risePx)
                this.alpha = alpha.value
            },
    )
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private const val POP_SCALE = 0.14f
private const val DIM_AMOUNT = 0.55f
private const val CAMERA_DISTANCE = 12f
private const val ENTRANCE_FADE_BOOST = 2f
