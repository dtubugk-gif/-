package il.rikavon.feature.blocker.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.ui.anim.enterFromBelow
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.Bar
import il.rikavon.core.ui.components.BarChart
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.ErrorState
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.ListRow
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.RikavonLargeTopBar
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SegmentPills
import il.rikavon.core.ui.components.StatTile
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.rememberLargeTopBarBehavior
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.util.formatClock
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Statistics: large collapsing title, range control, chart card with the week delta, three tiles,
 * per-app rows. Error when usage access is missing, empty (with the one primary action) until an app is tracked.
 */
@Composable
fun StatsScreen(
    onBack: (() -> Unit)?,
    onOpenScore: () -> Unit,
    onOpenApps: () -> Unit = {},
    onOpenPermissions: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current
    val locale = Locale.getDefault()
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("d/M", locale) }
    val todayLabel = stringResource(R.string.stats_day_today)
    val scrollBehavior = if (onBack == null) rememberLargeTopBarBehavior() else rememberPinnedTopBarBehavior()

    Scaffold(
        topBar = {
            if (onBack == null) {
                RikavonLargeTopBar(title = stringResource(R.string.stats_title), scrollBehavior = scrollBehavior)
            } else {
                RikavonTopBar(
                    title = stringResource(R.string.stats_title),
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Spacing.xl,
                ),
        ) {
            if (!state.hasUsagePermission) {
                item {
                    ErrorState(
                        title = stringResource(R.string.stats_permission_error_title),
                        body = stringResource(R.string.stats_permission_error_body),
                        actionLabel = stringResource(R.string.stats_grant_permission),
                        onAction = onOpenPermissions,
                    )
                }
            }
            if (!state.hasLimits) {
                item {
                    EmptyState(
                        title = stringResource(R.string.stats_empty_title),
                        body = stringResource(R.string.stats_empty_body),
                        icon = Icons.Filled.Info,
                        action = { PrimaryButton(text = stringResource(R.string.stats_add_app), onClick = onOpenApps) },
                    )
                }
                return@LazyColumn
            }
            item {
                SegmentPills(
                    options = StatsRange.entries,
                    selected = state.range,
                    onSelect = viewModel::setRange,
                    label = {
                        stringResource(
                            if (it ==
                                StatsRange.WEEK
                            ) {
                                R.string.stats_range_7
                            } else {
                                R.string.stats_range_30
                            },
                        )
                    },
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm).fillMaxWidth(),
                )
            }
            item {
                SurfaceCard(
                    modifier =
                        Modifier
                            .padding(
                                horizontal = ScreenPadding,
                                vertical = Spacing.sm,
                            ).fillMaxWidth()
                            .enterFromBelow(0, key = state.range),
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.stats_daily_screen_time),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            state.weekDeltaPercent?.let { delta ->
                                Text(
                                    text =
                                        when {
                                            delta < 0 -> stringResource(R.string.stats_delta_less, -delta)
                                            delta > 0 -> stringResource(R.string.stats_delta_more, delta)
                                            else -> stringResource(R.string.stats_delta_same)
                                        },
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        if (delta <
                                            0
                                        ) {
                                            extras.success
                                        } else if (delta > 0) {
                                            extras.danger
                                        } else {
                                            extras.onSurfaceMuted
                                        },
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        val month = state.range == StatsRange.MONTH
                        BarChart(
                            bars =
                                state.days.mapIndexed { index, day ->
                                    val last = index == state.days.lastIndex
                                    Bar(
                                        label =
                                            when {
                                                last -> todayLabel
                                                month -> day.date.format(monthFormatter)
                                                else ->
                                                    day.date.dayOfWeek
                                                        .getDisplayName(
                                                            TextStyle.SHORT,
                                                            locale,
                                                        ).removePrefix(DAY_PREFIX_HE)
                                                        .trim()
                                            },
                                        value = day.minutes.toFloat(),
                                        valueText = day.minutes.toString(),
                                        highlighted = last,
                                    )
                                },
                            labelEvery = if (month) MONTH_LABEL_EVERY else 1,
                            height = CHART_HEIGHT,
                            mutedColor = extras.track,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    StatTile(
                        value = state.todayOpens.toString(),
                        caption = stringResource(R.string.stats_tile_opens),
                        modifier = Modifier.weight(1f).enterFromBelow(0, key = state.range),
                    )
                    StatTile(
                        value =
                            stringResource(
                                R.string.stats_hours_minutes,
                                state.todayMinutes / MINUTES_PER_HOUR,
                                state.todayMinutes % MINUTES_PER_HOUR,
                            ),
                        caption =
                            stringResource(
                                R.string.stats_tile_peak,
                                state.peakHour?.let { formatClock(it * MINUTES_PER_HOUR) } ?: "–",
                            ),
                        modifier = Modifier.weight(1f).enterFromBelow(1, key = state.range),
                    )
                    StatTile(
                        value = state.streak.toString(),
                        caption = stringResource(R.string.stats_tile_streak),
                        valueColor = extras.success,
                        modifier = Modifier.weight(1f).enterFromBelow(2, key = state.range),
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.stats_per_app), modifier = Modifier.padding(top = Spacing.sm)) }
            itemsIndexed(state.todayApps, key = { _, item -> item.packageName }) { index, app ->
                val drawable = remember(app.packageName) { viewModel.icon(app.packageName) }
                ListRow(
                    title = app.label,
                    subtitle =
                        stringResource(
                            R.string.stats_app_row,
                            formatMinutes(app.usage.minutes),
                            app.usage.opens,
                        ),
                    leading = { AppIcon(drawable = drawable, label = app.label) },
                    chevron = false,
                    modifier = Modifier.enterFromBelow(index),
                )
            }
            item {
                SectionLabel(
                    stringResource(R.string.stats_opens_per_day),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                SurfaceCard(modifier = Modifier.padding(horizontal = ScreenPadding).fillMaxWidth()) {
                    val month = state.range == StatsRange.MONTH
                    BarChart(
                        bars =
                            state.days.mapIndexed { index, day ->
                                Bar(
                                    label =
                                        if (month) {
                                            day.date.format(
                                                monthFormatter,
                                            )
                                        } else {
                                            day.date.dayOfWeek
                                                .getDisplayName(
                                                    TextStyle.SHORT,
                                                    locale,
                                                ).removePrefix(DAY_PREFIX_HE)
                                                .trim()
                                        },
                                    value = day.opens.toFloat(),
                                    valueText = stringResource(R.string.stats_opens_value, day.opens),
                                    highlighted = index == state.days.lastIndex,
                                )
                            },
                        labelEvery = if (month) MONTH_LABEL_EVERY else 1,
                        barColor = MaterialTheme.colorScheme.secondary,
                        mutedColor = extras.track,
                        height = SMALL_CHART_HEIGHT,
                    )
                }
            }
            item {
                LinkButton(
                    text = stringResource(R.string.stats_score_link),
                    onClick = onOpenScore,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                )
            }
        }
    }
}

private val CHART_HEIGHT = 150.dp
private val SMALL_CHART_HEIGHT = 100.dp
private const val MONTH_LABEL_EVERY = 5
private const val MINUTES_PER_HOUR = 60
private const val DAY_PREFIX_HE = "יום"
