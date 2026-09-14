package il.rikavon.feature.blocker.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.RikavonTheme
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.theme.rotScheme
import il.rikavon.feature.blocker.R
import il.rikavon.feature.blocker.engine.BlockDecision
import il.rikavon.feature.mascot.model.HourBucket
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.ui.MascotEntrance
import il.rikavon.feature.mascot.ui.MascotView
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * The block screen (design 1c): a huge "Stop.", the mascot at its worst entering dramatically, the reason
 * plus the mascot's line, a time-of-day aside, a "try again in" card and one close pill. Always rot-green.
 */
@Composable
fun BlockOverlayContent(
    decision: BlockDecision,
    skin: MascotSkin,
    appLabel: String,
    limitMinutes: Int,
    message: String,
    reducedMotion: Boolean,
    onClose: () -> Unit,
) {
    RikavonTheme(scheme = rotScheme(), reducedMotion = reducedMotion) {
        var textVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(if (reducedMotion) 0L else AnimationSpecs.BLOCK_TEXT_DELAY_MILLIS.toLong())
            textVisible = true
        }
        val language = UiLanguage.current()
        val extras = LocalExtraColors.current
        val reasonLine =
            when (decision.reason) {
                BlockReason.LIMIT_REACHED -> stringResource(R.string.block_reason_limit_line, limitMinutes, appLabel)
                BlockReason.FULL_BLOCK -> stringResource(R.string.block_reason_full_line, appLabel)
                BlockReason.SCHEDULE -> stringResource(R.string.block_reason_schedule_line, appLabel)
            }
        val flavor =
            stringResource(
                when (HourBucket.of(LocalTime.now().hour)) {
                    HourBucket.MORNING -> R.string.block_flavor_morning
                    HourBucket.DAY -> R.string.block_flavor_day
                    HourBucket.EVENING -> R.string.block_flavor_evening
                    HourBucket.NIGHT -> R.string.block_flavor_night
                },
            )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenPadding, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.block_headline),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.lg))
                MascotView(
                    skin = skin,
                    stage = MascotStage.ROTTEN,
                    interactive = true,
                    entrance = MascotEntrance.DRAMATIC,
                    textForTap = {
                        skin
                            .stage(MascotStage.ROTTEN)
                            .texts
                            .resolve(language)
                            ?.randomOrNull()
                            .orEmpty()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(MASCOT_WIDTH_FRACTION)
                            .aspectRatio(1f),
                )
                Spacer(Modifier.height(Spacing.sm))
                AnimatedVisibility(
                    visible = textVisible,
                    enter =
                        if (reducedMotion) {
                            fadeIn(AnimationSpecs.Reduced)
                        } else {
                            fadeIn(tween(AnimationSpecs.BLOCK_TEXT_MILLIS)) +
                                slideInVertically(
                                    tween(
                                        AnimationSpecs.BLOCK_TEXT_MILLIS,
                                        easing = AnimationSpecs.EmphasizedDecelerate,
                                    ),
                                ) {
                                    it /
                                        2
                                }
                        },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = reasonLine + "\n" + message,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = flavor,
                            style = MaterialTheme.typography.bodyMedium,
                            color = extras.onSurfaceMuted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.xl))
                        RetryTimer(decision = decision)
                        Spacer(Modifier.height(Spacing.lg))
                        PrimaryButton(
                            text = stringResource(R.string.block_close),
                            onClick = onClose,
                            modifier = Modifier.widthIn(min = CLOSE_MIN_WIDTH),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryTimer(decision: BlockDecision) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(decision.retryAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    val remaining = (decision.retryAtMillis - now).coerceAtLeast(0)
    val hours = remaining / MILLIS_PER_HOUR
    val minutes = (remaining % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
    val seconds = (remaining % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND
    val clock = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    SurfaceCard(padding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(R.string.block_try_again_label),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtraColors.current.onSurfaceMuted,
            )
            Text(
                text = clock,
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val MASCOT_WIDTH_FRACTION = 0.6f
private val TEXT_MAX_WIDTH = 320.dp
private val CLOSE_MIN_WIDTH = 160.dp
private const val TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
