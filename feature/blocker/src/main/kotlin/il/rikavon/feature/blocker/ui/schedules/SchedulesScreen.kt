package il.rikavon.feature.blocker.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.util.formatClock
import il.rikavon.feature.blocker.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Schedules list: cards with a switch, a FAB to add, and an empty state that doubles as onboarding. */
@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = rememberPinnedTopBarBehavior()
    val create = { viewModel.requestNew { onEdit(Schedule.NEW_ID) } }

    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.schedules_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.schedules.isNotEmpty()) {
                FloatingActionButton(
                    onClick = create,
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.schedules_add))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.schedules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.schedules_empty_title),
                    body = stringResource(R.string.schedules_empty_body),
                    icon = Icons.Filled.DateRange,
                    action = { PrimaryButton(text = stringResource(R.string.schedules_create), onClick = create) },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + Spacing.sm,
                        bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.schedules, key = { it.id }) { schedule ->
                    ScheduleRow(
                        schedule = schedule,
                        onClick = { onEdit(schedule.id) },
                        onToggle = { viewModel.setEnabled(schedule, it) },
                    )
                }
            }
        }
    }

    if (state.showFreeCap) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFreeCap,
            title = {
                Text(
                    stringResource(R.string.schedules_free_cap_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    stringResource(R.string.schedules_free_cap_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                LinkButton(
                    text = stringResource(R.string.apps_free_cap_ok),
                    onClick = viewModel::dismissFreeCap,
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun ScheduleRow(schedule: Schedule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val locale = Locale.getDefault()
    val days =
        orderedDays()
            .filter { it in schedule.days }
            .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, locale) }
    val summary =
        stringResource(
            R.string.schedules_days_summary,
            days,
            formatClock(schedule.startMinute),
            formatClock(schedule.endMinute),
            schedule.packages.size,
        )
    val toggleDescription = stringResource(R.string.schedule_enabled_description, schedule.name)
    SurfaceCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(scheduleTypeLabel(schedule.type)) + " · " + summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.onSurfaceMuted,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
        }
    }
}

internal fun scheduleTypeLabel(type: ScheduleType): Int =
    when (type) {
        ScheduleType.WORK -> R.string.schedule_type_work
        ScheduleType.STUDY -> R.string.schedule_type_study
        ScheduleType.SLEEP -> R.string.schedule_type_sleep
        ScheduleType.CUSTOM -> R.string.schedule_type_custom
    }

/** Week starting on the locale's first day (Sunday in Israel). */
internal fun orderedDays(): List<DayOfWeek> {
    val first =
        java.time.temporal.WeekFields
            .of(Locale.getDefault())
            .firstDayOfWeek
    return List(DayOfWeek.entries.size) { first.plus(it.toLong()) }
}

private val FAB_CLEARANCE = Spacing.xxxl + Spacing.xxl
