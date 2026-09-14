package il.rikavon.feature.blocker.ui.stats

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.Bar
import il.rikavon.core.ui.components.BarChart
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionHeader
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenScore: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dayFormatter = remember { DateTimeFormatter.ofPattern("d/M", Locale.getDefault()) }

    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.stats_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + ScreenPadding,
                ),
        ) {
            if (!state.hasUsagePermission) {
                item { EmptyState(text = stringResource(R.string.stats_no_permission)) }
            }
            if (!state.hasLimits) {
                item { EmptyState(text = stringResource(R.string.stats_empty)) }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatsRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.range == range,
                            onClick = { viewModel.setRange(range) },
                            label = {
                                Text(
                                    stringResource(
                                        if (range ==
                                            StatsRange.WEEK
                                        ) {
                                            R.string.stats_range_7
                                        } else {
                                            R.string.stats_range_30
                                        },
                                    ),
                                )
                            },
                            modifier = Modifier.heightIn(min = TouchTarget),
                        )
                    }
                }
            }
            item {
                SectionHeader(stringResource(R.string.stats_minutes_per_day))
                val labelEvery = if (state.range == StatsRange.MONTH) MONTH_LABEL_EVERY else 1
                BarChart(
                    bars =
                        state.days.mapIndexed { index, day ->
                            Bar(
                                label = day.date.format(dayFormatter),
                                value = day.minutes.toFloat(),
                                valueText = formatMinutesPlain(day.minutes),
                                highlighted = index == state.days.lastIndex,
                            )
                        },
                    labelEvery = labelEvery,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
            item {
                SectionHeader(stringResource(R.string.stats_opens_per_day))
                val labelEvery = if (state.range == StatsRange.MONTH) MONTH_LABEL_EVERY else 1
                BarChart(
                    bars =
                        state.days.mapIndexed { index, day ->
                            Bar(
                                label = day.date.format(dayFormatter),
                                value = day.opens.toFloat(),
                                valueText = stringResource(R.string.stats_opens_value, day.opens),
                                highlighted = index == state.days.lastIndex,
                            )
                        },
                    labelEvery = labelEvery,
                    barColor = MaterialTheme.colorScheme.secondary,
                    height = SMALL_CHART_HEIGHT,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
            item {
                SectionHeader(stringResource(R.string.stats_peak_hours))
                val peak =
                    state.opensByHour
                        .withIndex()
                        .maxByOrNull { it.value }
                        ?.index
                BarChart(
                    bars =
                        state.opensByHour.mapIndexed { hour, count ->
                            Bar(
                                label = stringResource(R.string.stats_hour_label, hour),
                                value = count.toFloat(),
                                valueText = stringResource(R.string.stats_opens_value, count),
                                highlighted = hour == peak && count > 0,
                            )
                        },
                    labelEvery = HOUR_LABEL_EVERY,
                    barColor = MaterialTheme.colorScheme.tertiary,
                    height = SMALL_CHART_HEIGHT,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
            state.comparison?.let { comparison ->
                item {
                    SectionHeader(stringResource(R.string.stats_week_compare))
                    Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
                        Text(
                            text =
                                stringResource(
                                    R.string.stats_week_minutes,
                                    formatMinutes(comparison.minutes),
                                    formatMinutes(comparison.previousMinutes),
                                ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.stats_week_opens,
                                    comparison.opens,
                                    comparison.previousOpens,
                                ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val verdict =
                            when {
                                comparison.minutes < comparison.previousMinutes -> R.string.stats_week_better
                                comparison.minutes > comparison.previousMinutes -> R.string.stats_week_worse
                                else -> R.string.stats_week_same
                            }
                        Text(
                            text = stringResource(verdict),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.stats_per_app)) }
            items(state.todayApps, key = { it.packageName }) { app ->
                AppStatRow(app = app, icon = { viewModel.icon(app.packageName) })
            }
            item {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onOpenScore,
                    modifier = Modifier.padding(horizontal = ScreenPadding).heightIn(min = TouchTarget),
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
    val limitMinutes = app.limit?.limitMinutes ?: 0
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(drawable = drawable, label = app.label, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text =
                    stringResource(R.string.stats_app_line, formatMinutes(app.usage.minutes), app.usage.opens) +
                        if (app.limit != null &&
                            !app.limit.fullBlock
                        ) {
                            " " + stringResource(R.string.stats_of_limit, formatMinutes(limitMinutes))
                        } else {
                            ""
                        },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = {
                    if (limitMinutes >
                        0
                    ) {
                        (app.usage.minutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(6.dp),
            )
        }
    }
}

private fun formatMinutesPlain(minutes: Int): String = minutes.toString()

private val SMALL_CHART_HEIGHT = 110.dp
private const val MONTH_LABEL_EVERY = 5
private const val HOUR_LABEL_EVERY = 3
