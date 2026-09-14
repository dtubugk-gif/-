package il.rikavon.feature.blocker.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.core.ui.util.formatClock
import il.rikavon.feature.blocker.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.schedules_title), onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.requestNew { onEdit(Schedule.NEW_ID) } }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.schedules_add))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.schedules.isEmpty()) {
            EmptyState(text = stringResource(R.string.schedules_empty), modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + FAB_CLEARANCE,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
            title = { Text(stringResource(R.string.schedules_free_cap_title)) },
            text = { Text(stringResource(R.string.schedules_free_cap_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissFreeCap) { Text(stringResource(R.string.apps_free_cap_ok)) }
            },
        )
    }
}

@Composable
private fun ScheduleRow(schedule: Schedule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val locale = Locale.getDefault()
    val days =
        orderedDays()
            .filter {
                it in schedule.days
            }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, locale) }
    val summary =
        stringResource(
            R.string.schedules_days_summary,
            days,
            formatClock(schedule.startMinute),
            formatClock(schedule.endMinute),
            schedule.packages.size,
        )
    val toggleDescription = stringResource(R.string.schedule_enabled_description, schedule.name)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .clickable(onClick = onClick, role = Role.Button)
                .heightIn(min = TouchTarget + 16.dp)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(schedule.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(scheduleTypeLabel(schedule.type)) + " · " + summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = schedule.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = toggleDescription },
        )
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

private val FAB_CLEARANCE = 88.dp
