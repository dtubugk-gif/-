package il.rikavon.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.R
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.Pill
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.mascot.ui.MASCOT_SHARED_KEY
import il.rikavon.feature.mascot.ui.MascotView
import il.rikavon.feature.mascot.ui.UiLanguage

@Composable
fun HomeScreen(
    onOpenApps: () -> Unit,
    onOpenLimit: (String) -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = UiLanguage.current()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + ScreenPadding,
                ),
        ) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Pill(
                        text = stringResource(R.string.home_streak, state.streak),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier =
                            Modifier
                                .clickable(onClick = onOpenAchievements, role = Role.Button)
                                .heightIn(min = 32.dp),
                    )
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(TouchTarget)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                }
            }
            item {
                val skin = state.skin
                val mascotHeight = (LocalConfiguration.current.screenHeightDp * MASCOT_SCREEN_FRACTION).dp
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(mascotHeight.coerceAtLeast(MASCOT_MIN_HEIGHT)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (skin != null) {
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
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
                        )
                    }
                }
                Text(
                    text = state.mascotLine.ifBlank { skin?.name?.resolve(language).orEmpty() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding)
                            .heightIn(min = 48.dp),
                )
            }
            val perms = state.permissions
            if (perms != null && !perms.coreGranted) {
                item {
                    LimitedModeBanner(onClick = onOpenOnboarding)
                }
            }
            if (!state.trackingEnabled) {
                item {
                    Text(
                        text = stringResource(R.string.home_tracking_paused),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
                    )
                }
            }
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                    ) {
                        Text(stringResource(R.string.home_gallery))
                    }
                    OutlinedButton(onClick = onOpenStats, modifier = Modifier.weight(1f).heightIn(min = TouchTarget)) {
                        Text(stringResource(R.string.home_stats))
                    }
                    OutlinedButton(
                        onClick = onOpenSchedules,
                        modifier = Modifier.weight(1f).heightIn(min = TouchTarget),
                    ) {
                        Text(stringResource(R.string.home_schedules))
                    }
                }
            }
            if (state.tracked.isEmpty()) {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_limits),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenApps, modifier = Modifier.heightIn(min = TouchTarget + 8.dp)) {
                            Text(stringResource(R.string.home_pick_apps))
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_tracked_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = onOpenApps, modifier = Modifier.heightIn(min = TouchTarget)) {
                            Text(stringResource(R.string.home_edit_apps))
                        }
                    }
                }
                items(state.tracked, key = { it.limit.packageName }) { row ->
                    TrackedRow(row = row, icon = {
                        viewModel.icon(row.limit.packageName)
                    }, onClick = { onOpenLimit(row.limit.packageName) })
                }
            }
        }
    }
}

@Composable
private fun LimitedModeBanner(onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 6.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(14.dp),
    ) {
        Text(stringResource(R.string.home_limited_title), style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(R.string.home_limited_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackedRow(row: TrackedAppRow, icon: () -> android.graphics.drawable.Drawable?, onClick: () -> Unit) {
    val drawable = remember(row.limit.packageName) { icon() }
    val limitText =
        if (row.limit.fullBlock) {
            stringResource(
                R.string.home_full_block,
            )
        } else {
            formatMinutes(row.limit.limitMinutes)
        }
    val ratio =
        if (row.limit.fullBlock) {
            if (row.usage.minutes > 0) 1f else 0f
        } else {
            (row.usage.minutes.toFloat() / row.limit.limitMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, role = Role.Button)
                .heightIn(min = TouchTarget + 12.dp)
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(drawable = drawable, label = row.label, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.home_usage_of_limit, formatMinutes(row.usage.minutes), limitText),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (ratio >=
                            1f
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            LinearProgressIndicator(
                progress = { ratio },
                color = if (ratio >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(6.dp),
            )
        }
    }
}

private const val MASCOT_SCREEN_FRACTION = 0.4f
private val MASCOT_MIN_HEIGHT = 220.dp
