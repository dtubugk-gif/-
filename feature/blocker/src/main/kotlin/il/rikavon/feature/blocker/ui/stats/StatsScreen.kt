package il.rikavon.feature.blocker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.Bar
import il.rikavon.core.ui.components.BarChart
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.ScreenTitle
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SegmentPills
import il.rikavon.core.ui.components.StatTile
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.util.formatClock
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Statistics, design 1e: range pills, self-drawing chart card, three tiles, per-app rows. */
@Composable
fun StatsScreen(
    onBack: (() -> Unit)?,
    onOpenScore: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val extras = LocalExtraColors.current
    val locale = Locale.getDefault()
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("d/M", locale) }
    val todayLabel = stringResource(R.string.stats_day_today)

    Scaffold(
        topBar = { if (onBack != null) RikavonTopBar(title = stringResource(R.string.stats_title), onBack = onBack) },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + ScreenPadding,
                ),
        ) {
            if (onBack == null) {
                item { ScreenTitle(title = stringResource(R.string.stats_title)) }
            }
            if (!state.hasUsagePermission) {
                item { EmptyState(text = stringResource(R.string.stats_no_permission)) }
            }
            if (!state.hasLimits) {
                item { EmptyState(text = stringResource(R.string.stats_empty)) }
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
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 10.dp),
                )
            }
            item {
                SurfaceCard(modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp).fillMaxWidth()) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.stats_daily_screen_time),
                                style = MaterialTheme.typography.bodyMedium,
                                color = extras.onSurfaceMuted,
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
                        Spacer(Modifier.height(14.dp))
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
                                        valueText = formatMinutesPlain(day.minutes),
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
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        value = state.todayOpens.toString(),
                        caption = stringResource(R.string.stats_tile_opens),
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.streak.toString(),
                        caption = stringResource(R.string.stats_tile_streak),
                        valueColor = extras.success,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item { SectionLabel(stringResource(R.string.stats_per_app)) }
            items(state.todayApps, key = { it.packageName }) { app ->
                AppStatRow(app = app, icon = { viewModel.icon(app.packageName) })
            }
            item {
                SectionLabel(stringResource(R.string.stats_opens_per_day), modifier = Modifier.padding(top = 10.dp))
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
                TextButton(
                    onClick = onOpenScore,
                    modifier =
                        Modifier
                            .padding(
                                horizontal = ScreenPadding,
                                vertical = 8.dp,
                            ).heightIn(min = TouchTarget),
                ) {
                    Text(stringResource(R.string.stats_score_link))
                }
            }
        }
    }
}

@Composable
private fun AppStatRow(app: AppStat, icon: () -> android.graphics.drawable.Drawable?) {
    val drawable = remember(app.packageName) { icon() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(drawable = drawable, label = app.label, size = 30.dp)
        Text(
            app.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.stats_app_row, formatMinutes(app.usage.minutes), app.usage.opens),
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.onSurfaceMuted,
            maxLines = 1,
        )
    }
}

private fun formatMinutesPlain(minutes: Int): String = minutes.toString()

private val CHART_HEIGHT = 150.dp
private val SMALL_CHART_HEIGHT = 100.dp
private const val MONTH_LABEL_EVERY = 5
private const val MINUTES_PER_HOUR = 60
private const val DAY_PREFIX_HE = "יום"
