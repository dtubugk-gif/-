package il.rikavon.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.R
import il.rikavon.core.ui.anim.AnimatedNumber
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.FadeThrough
import il.rikavon.core.ui.anim.LocalReducedMotion
import il.rikavon.core.ui.anim.bounceOn
import il.rikavon.core.ui.anim.enterFromBelow
import il.rikavon.core.ui.anim.floatLoop
import il.rikavon.core.ui.anim.popEnter
import il.rikavon.core.ui.anim.popExit
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.DotChip
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.ErrorState
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SkeletonBlock
import il.rikavon.core.ui.components.SkeletonList
import il.rikavon.core.ui.components.SpeechBubble
import il.rikavon.core.ui.components.SquareIconButton
import il.rikavon.core.ui.components.ThinBar
import il.rikavon.core.ui.components.pressScale
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Radius
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.theme.scoreColor
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.mascot.ui.MASCOT_SHARED_KEY
import il.rikavon.feature.mascot.ui.MascotView
import il.rikavon.feature.mascot.ui.UiLanguage
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home, the pet's room: date + greeting header with the streak pill and settings, the living mascot with its
 * line, the focus score, then today's tracked apps. Skeleton while the pet loads, an error card when
 * permissions are missing, an empty state (with the one primary action) until the first app is tracked.
 */
@Composable
fun HomeScreen(
    onOpenApps: () -> Unit,
    onOpenLimit: (String) -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onCallPet: () -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = UiLanguage.current()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    val extras = LocalExtraColors.current

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Spacing.lg,
                ),
        ) {
            item {
                Header(
                    state = state,
                    onOpenAchievements = onOpenAchievements,
                    onCallPet = onCallPet,
                    onOpenSettings = onOpenSettings,
                )
            }
            if (state.skin == null) {
                item {
                    SkeletonBlock(
                        height = MASCOT_MIN_HEIGHT,
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                    )
                }
                item { SkeletonList(rows = 2) }
                return@LazyColumn
            }
            item { MascotStage(state = state, language = language, viewModel = viewModel) }
            item { ScoreBlock(state = state) }
            val perms = state.permissions
            if (perms != null && !perms.coreGranted) {
                item {
                    ErrorState(
                        title = stringResource(R.string.home_permission_error_title),
                        body = stringResource(R.string.home_permission_error_body),
                        actionLabel = stringResource(R.string.home_fix_permissions),
                        onAction = onOpenOnboarding,
                    )
                }
            }
            if (!state.trackingEnabled) {
                item {
                    Text(
                        text = stringResource(R.string.home_tracking_paused),
                        style = MaterialTheme.typography.bodyMedium,
                        color = extras.danger,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.home_today), modifier = Modifier.padding(top = Spacing.sm)) }
            if (state.tracked.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        icon = Icons.Filled.Add,
                        action = { PrimaryButton(text = stringResource(R.string.home_add_app), onClick = onOpenApps) },
                        onClick = onOpenApps,
                    )
                }
            } else {
                itemsIndexed(state.tracked, key = { _, item -> item.limit.packageName }) { index, row ->
                    TrackedRow(
                        row = row,
                        icon = { viewModel.icon(row.limit.packageName) },
                        onClick = { onOpenLimit(row.limit.packageName) },
                        modifier = Modifier.enterFromBelow(index),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        DotChip(
                            text = stringResource(R.string.home_add_app),
                            dot = MaterialTheme.colorScheme.primary,
                            onClick = onOpenApps,
                        )
                        DotChip(
                            text =
                                stringResource(R.string.home_schedules_row) + " · " +
                                    if (state.activeSchedules > 0) {
                                        stringResource(R.string.home_schedules_active, state.activeSchedules)
                                    } else {
                                        stringResource(R.string.home_schedules_none)
                                    },
                            dot = if (state.activeSchedules > 0) extras.success else extras.onSurfaceFaint,
                            onClick = onOpenSchedules,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: HomeUiState,
    onOpenAchievements: () -> Unit,
    onCallPet: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val extras = LocalExtraColors.current
    val pattern = stringResource(R.string.home_date_pattern)
    val locale = Locale.getDefault()
    val dateText =
        remember(state.date, pattern, locale) { state.date.format(DateTimeFormatter.ofPattern(pattern, locale)) }
    val greeting =
        stringResource(
            when (state.greeting) {
                Greeting.MORNING -> R.string.home_greeting_morning
                Greeting.NOON -> R.string.home_greeting_noon
                Greeting.EVENING -> R.string.home_greeting_evening
                Greeting.NIGHT -> R.string.home_greeting_night
            },
        )
    val achievementsLabel = stringResource(R.string.home_achievements)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = ScreenPadding, vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                dateText,
                style = MaterialTheme.typography.bodySmall,
                color = extras.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .bounceOn(trigger = state.streak)
                        .pressScale(interaction)
                        .heightIn(min = Sizes.chip)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onOpenAchievements,
                            role = Role.Button,
                        ).semantics { contentDescription = achievementsLabel }
                        .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_streak_pill, state.streak),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            SquareIconButton(
                icon = Icons.Filled.Call,
                contentDescription = stringResource(R.string.talk_call_pet),
                onClick = onCallPet,
            )
            SquareIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.home_settings),
                onClick = onOpenSettings,
            )
        }
        Text(
            greeting,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun MascotStage(state: HomeUiState, language: String, viewModel: HomeViewModel) {
    val skin = state.skin ?: return
    val mascotHeight = (LocalConfiguration.current.screenHeightDp * MASCOT_SCREEN_FRACTION).dp
    val line = state.mascotLine.ifBlank { viewModel.idleLine(language) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(mascotHeight.coerceAtLeast(MASCOT_MIN_HEIGHT)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        MascotView(
            skin = skin,
            stage = state.stage,
            effectTrigger = state.effect,
            textForTap = { viewModel.tapText(language) },
            onLongPress = viewModel::onLongPress,
            onEffectSound = viewModel::onEffectSound,
            sharedKey = MASCOT_SHARED_KEY,
            modifier =
                Modifier
                    .floatLoop()
                    .fillMaxHeight(MASCOT_FILL)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
        )
        FadeThrough(
            targetState = line,
            label = "mascotLine",
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.xs, start = ScreenPadding, end = ScreenPadding),
        ) { current ->
            if (current.isNotBlank()) SpeechBubble(text = current)
        }
    }
}

@Composable
private fun ScoreBlock(state: HomeUiState) {
    val extras = LocalExtraColors.current
    val color = scoreColor(state.score)
    val description = stringResource(R.string.home_score_description, state.score)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val animatedColor by animateColorAsState(
            color,
            tween(AnimationSpecs.COUNT_MILLIS, easing = AnimationSpecs.Emphasized),
            label = "scoreColor",
        )
        val progress by animateFloatAsState(state.score / MAX_SCORE, AnimationSpecs.Count, label = "scoreBar")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AnimatedNumber(
                value = state.score,
                style = MaterialTheme.typography.displayMedium,
                color = animatedColor,
                modifier = Modifier.bounceOn(trigger = state.score, peak = SCORE_BOUNCE),
            )
            Text(
                text = stringResource(R.string.home_score_label),
                style = MaterialTheme.typography.bodyMedium,
                color = extras.onSurfaceMuted,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }
        ThinBar(
            progress = progress,
            color = animatedColor,
            height = SCORE_BAR_HEIGHT,
            modifier = Modifier.width(SCORE_BAR_WIDTH).padding(top = Spacing.sm),
        )
        val next = state.nextLimit
        val reduced = LocalReducedMotion.current
        AnimatedVisibility(visible = next != null, enter = popEnter(reduced), exit = popExit(reduced)) {
            if (next == null) return@AnimatedVisibility
            Text(
                text =
                    when (next) {
                        is NextLimit.Upcoming ->
                            stringResource(R.string.home_next_limit, next.label, formatMinutes(next.minutesLeft))
                        is NextLimit.Reached -> stringResource(R.string.home_next_limit_reached, next.label)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = if (next is NextLimit.Reached) extras.overLimit else extras.onSurfaceMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun TrackedRow(
    row: TrackedAppRow,
    icon: () -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalExtraColors.current
    val drawable = remember(row.limit.packageName) { icon() }
    val limitText =
        if (row.limit.fullBlock) {
            stringResource(
                R.string.home_full_block,
            )
        } else {
            formatMinutes(row.limit.limitMinutes)
        }
    val ratio = row.ratio
    val barColor: Color =
        when {
            ratio >= 1f -> extras.danger
            ratio >= NEAR_LIMIT -> extras.overLimit
            else -> MaterialTheme.colorScheme.primary
        }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = Spacing.xs)
                .pressScale(interaction)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radius.lg))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick, role = Role.Button)
                .heightIn(min = Sizes.rowTwoLine)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        AppIcon(drawable = drawable, label = row.label)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_usage_of_limit, formatMinutes(row.usage.minutes), limitText),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ratio >= NEAR_LIMIT) barColor else extras.onSurfaceMuted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            ThinBar(progress = ratio, color = barColor)
        }
    }
}

private const val MASCOT_SCREEN_FRACTION = 0.38f
private const val MASCOT_FILL = 0.8f
private val MASCOT_MIN_HEIGHT = 220.dp
private val SCORE_BAR_WIDTH = 180.dp
private val SCORE_BAR_HEIGHT = 5.dp
private const val MAX_SCORE = 100f
private const val NEAR_LIMIT = 0.66f
private const val SCORE_BOUNCE = 1.08f
