package il.rikavon.feature.blocker.contact

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import il.rikavon.core.ui.components.CallIcons
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.pressScale
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.RikavonTheme
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.theme.schemeFromAccent
import il.rikavon.feature.blocker.R
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.sound.VoiceIssue
import il.rikavon.feature.mascot.ui.MascotView

/**
 * The call screen, laid out like the phone app's: who is calling and why (or the call timer), the pet in a
 * ring that pulses while it rings, dials or talks and glows while it listens, one live caption of what the
 * pet is saying and what it heard, then the round buttons: answer / decline while ringing; mute, speaker,
 * keyboard and end once connected.
 */
@Composable
fun PetCallContent(
    skin: MascotSkin,
    petName: String,
    appLabel: String,
    state: VoiceCallState,
    reducedMotion: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onKeyboard: () -> Unit,
    onPromise: () -> Unit,
) {
    val scheme = schemeFromAccent(Color(skin.themeColorArgb), skin.surfaceTintArgb?.let { Color(it) })
    RikavonTheme(scheme = scheme, reducedMotion = reducedMotion) {
        val extras = LocalExtraColors.current
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding()
                    .padding(horizontal = ScreenPadding, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = statusLine(state),
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
                text = subtitle(state, petName, appLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = extras.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = SUBTITLE_MIN_HEIGHT),
            )
            Spacer(Modifier.weight(1f))
            Avatar(skin, state, reducedMotion)
            Spacer(Modifier.height(Spacing.lg))
            Captions(state)
            Spacer(Modifier.weight(1f))
            hint(state, petName)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Spacing.md),
                )
            }
            when (state.phase) {
                CallPhase.RINGING -> RingingButtons(onAnswer, onDecline)
                CallPhase.DIALING -> EndButton(onHangUp)
                CallPhase.CONNECTED -> ConnectedButtons(state, onMute, onSpeaker, onKeyboard, onHangUp)
                CallPhase.ENDED -> Spacer(Modifier.height(BUTTON))
            }
            if (state.phase == CallPhase.CONNECTED && state.incoming) {
                Spacer(Modifier.height(Spacing.md))
                LinkButton(text = stringResource(R.string.call_promise), onClick = onPromise)
            }
        }
    }
}

@Composable
private fun statusLine(state: VoiceCallState): String =
    when (state.phase) {
        CallPhase.RINGING -> stringResource(R.string.call_incoming)
        CallPhase.DIALING -> stringResource(R.string.call_dialing)
        CallPhase.CONNECTED -> clock(state.seconds)
        CallPhase.ENDED -> stringResource(R.string.call_ended)
    }

@Composable
private fun subtitle(state: VoiceCallState, petName: String, appLabel: String): String =
    when {
        state.phase == CallPhase.RINGING && appLabel.isNotBlank() -> stringResource(R.string.call_about, appLabel)
        state.phase != CallPhase.CONNECTED -> ""
        state.turn == CallTurn.SPEAKING -> stringResource(R.string.call_speaking, petName)
        state.turn == CallTurn.LISTENING -> stringResource(R.string.call_listening)
        state.muted -> stringResource(R.string.call_muted, petName)
        else -> ""
    }

/** One reason the conversation is one-sided, if any, worded as the thing to do about it. */
@Composable
private fun hint(state: VoiceCallState, petName: String): String? =
    when {
        state.phase != CallPhase.CONNECTED -> null
        state.hearing == Hearing.NO_PERMISSION -> stringResource(R.string.call_no_mic_permission, petName)
        state.hearing == Hearing.UNAVAILABLE -> stringResource(R.string.call_no_recognizer, petName)
        state.voiceIssue == VoiceIssue.NO_ENGINE -> stringResource(R.string.call_voice_no_engine, petName)
        state.voiceIssue == VoiceIssue.NO_LANGUAGE -> stringResource(R.string.call_voice_no_language, petName)
        state.voiceIssue == VoiceIssue.MUTED -> stringResource(R.string.call_voice_muted, petName)
        else -> null
    }

private fun clock(seconds: Int): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).toString().padStart(2, '0')
    val rest = (seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    return "$minutes:$rest"
}

@Composable
private fun Avatar(skin: MascotSkin, state: VoiceCallState, reducedMotion: Boolean) {
    val lively = state.phase == CallPhase.RINGING || state.phase == CallPhase.DIALING || state.turn == CallTurn.SPEAKING
    val pulse = rememberInfiniteTransition(label = "ring")
    val scale by
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (reducedMotion || !lively) 1f else RING_PULSE_SCALE,
            animationSpec = infiniteRepeatable(tween(RING_PULSE_MILLIS), RepeatMode.Reverse),
            label = "ringScale",
        )
    val listening = state.turn == CallTurn.LISTENING
    Box(modifier = Modifier.size(AVATAR_RING), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .then(
                        if (listening) {
                            Modifier.border(LISTEN_RING, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
        )
        MascotView(
            skin = skin,
            stage = if (state.incoming) MascotStage.WORN else MascotStage.PRISTINE,
            interactive = false,
            modifier = Modifier.size(AVATAR),
        )
    }
}

/** What the pet is saying, and under it what it is hearing: subtitles, not a chat. */
@Composable
private fun Captions(state: VoiceCallState) {
    val extras = LocalExtraColors.current
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = CAPTION_MIN_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (state.phase == CallPhase.CONNECTED && state.caption.isNotBlank()) {
            Text(
                text = state.caption,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        if (state.turn == CallTurn.LISTENING && state.heard.isNotBlank()) {
            Text(
                text = state.heard,
                style = MaterialTheme.typography.bodyMedium,
                color = extras.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RingingButtons(onAnswer: () -> Unit, onDecline: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(
            icon = CallIcons.CallEnd,
            label = stringResource(R.string.call_decline),
            background = MaterialTheme.colorScheme.error,
            tint = MaterialTheme.colorScheme.onError,
            onClick = onDecline,
        )
        RoundButton(
            icon = Icons.Filled.Call,
            label = stringResource(R.string.call_answer),
            background = MaterialTheme.colorScheme.primary,
            tint = MaterialTheme.colorScheme.onPrimary,
            onClick = onAnswer,
        )
    }
}

@Composable
private fun EndButton(onHangUp: () -> Unit) {
    RoundButton(
        icon = CallIcons.CallEnd,
        label = stringResource(R.string.call_end),
        background = MaterialTheme.colorScheme.error,
        tint = MaterialTheme.colorScheme.onError,
        onClick = onHangUp,
    )
}

@Composable
private fun ConnectedButtons(
    state: VoiceCallState,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onKeyboard: () -> Unit,
    onHangUp: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(
            icon = if (state.muted) CallIcons.MicOff else CallIcons.Mic,
            label = stringResource(if (state.muted) R.string.call_unmute else R.string.call_mute),
            background = if (state.muted) scheme.primary else scheme.surfaceContainerHigh,
            tint = if (state.muted) scheme.onPrimary else scheme.onSurface,
            onClick = onMute,
        )
        RoundButton(
            icon = CallIcons.Speaker,
            label = stringResource(if (state.speaker) R.string.call_speaker else R.string.call_earpiece),
            background = if (state.speaker) scheme.primary else scheme.surfaceContainerHigh,
            tint = if (state.speaker) scheme.onPrimary else scheme.onSurface,
            onClick = onSpeaker,
        )
        RoundButton(
            icon = CallIcons.Keyboard,
            label = stringResource(R.string.call_keyboard),
            background = scheme.surfaceContainerHigh,
            tint = scheme.onSurface,
            onClick = onKeyboard,
        )
        RoundButton(
            icon = CallIcons.CallEnd,
            label = stringResource(R.string.call_end),
            background = scheme.error,
            tint = scheme.onError,
            onClick = onHangUp,
        )
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(
            modifier =
                Modifier
                    .size(BUTTON)
                    .pressScale(interaction)
                    .background(background, CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    ).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(BUTTON_ICON))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalExtraColors.current.onSurfaceMuted,
        )
    }
}

private const val RING_PULSE_SCALE = 1.08f
private const val RING_PULSE_MILLIS = 900
private const val SECONDS_PER_MINUTE = 60
private val AVATAR_RING = 220.dp
private val AVATAR = 140.dp
private val LISTEN_RING = 3.dp
private val BUTTON = 64.dp
private val BUTTON_ICON = 28.dp
private val CAPTION_MIN_HEIGHT = 84.dp
private val SUBTITLE_MIN_HEIGHT = 20.dp
