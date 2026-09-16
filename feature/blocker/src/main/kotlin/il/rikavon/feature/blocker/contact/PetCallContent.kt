package il.rikavon.feature.blocker.contact

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import il.rikavon.core.ui.anim.enterFromBelow
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SecondaryButton
import il.rikavon.core.ui.components.SurfaceCard
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
 * The call screen. Ringing: the pet in a pulsing ring, "Incoming call", answer / decline. Answered: the pet
 * at the top and its lines arriving one by one, then "I'll stop" and "Hang up".
 */
@Composable
fun PetCallContent(
    skin: MascotSkin,
    petName: String,
    appLabel: String,
    lines: List<String>,
    answered: Boolean,
    reducedMotion: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onPromise: () -> Unit,
    onHangUp: () -> Unit,
) {
    val scheme = schemeFromAccent(Color(skin.themeColorArgb), skin.surfaceTintArgb?.let { Color(it) })
    RikavonTheme(scheme = scheme, reducedMotion = reducedMotion) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (answered) {
                Answered(skin, petName, lines, reducedMotion, onPromise, onHangUp)
            } else {
                Ringing(skin, petName, appLabel, reducedMotion, onAnswer, onDecline)
            }
        }
    }
}

@Composable
private fun Ringing(
    skin: MascotSkin,
    petName: String,
    appLabel: String,
    reducedMotion: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "ring")
    val scale by
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (reducedMotion) 1f else RING_PULSE_SCALE,
            animationSpec = infiniteRepeatable(tween(RING_PULSE_MILLIS), RepeatMode.Reverse),
            label = "ringScale",
        )
    val extras = LocalExtraColors.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = ScreenPadding, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = stringResource(R.string.call_incoming),
            style = MaterialTheme.typography.labelLarge,
            color = extras.onSurfaceMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = petName,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.call_about, appLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = extras.onSurfaceMuted,
        )
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(AVATAR_RING), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            )
            MascotView(skin = skin, stage = MascotStage.WORN, interactive = false, modifier = Modifier.size(AVATAR))
        }
        Spacer(Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SecondaryButton(
                text = stringResource(R.string.call_decline),
                onClick = onDecline,
                destructive = true,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.call_answer),
                onClick = onAnswer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Answered(
    skin: MascotSkin,
    petName: String,
    lines: List<String>,
    reducedMotion: Boolean,
    onPromise: () -> Unit,
    onHangUp: () -> Unit,
) {
    var shown by remember { mutableIntStateOf(if (reducedMotion) lines.size else 0) }
    LaunchedEffect(lines) {
        while (shown < lines.size) {
            delay(if (reducedMotion) 0L else AnimationSpecs.CALL_LINE_GAP_MILLIS)
            shown++
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = ScreenPadding, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MascotView(skin = skin, stage = MascotStage.WORN, interactive = true, modifier = Modifier.size(AVATAR))
        Spacer(Modifier.height(Spacing.sm))
        Text(text = petName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.lg))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            lines.take(shown).forEachIndexed { index, line ->
                SurfaceCard(modifier = Modifier.fillMaxWidth().enterFromBelow(index, key = line)) {
                    Text(text = line, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = stringResource(R.string.call_promise),
            onClick = onPromise,
            enabled = shown == lines.size,
            modifier = Modifier.fillMaxWidth(),
        )
        LinkButton(text = stringResource(R.string.call_hang_up), onClick = onHangUp)
    }
}

private const val RING_PULSE_SCALE = 1.08f
private const val RING_PULSE_MILLIS = 900
private val AVATAR_RING = 220.dp
private val AVATAR = 140.dp
