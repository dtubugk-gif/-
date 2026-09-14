package il.rikavon.feature.blocker.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionHeader
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.core.ui.util.formatClock
import il.rikavon.feature.blocker.R
import java.time.format.TextStyle
import java.util.Locale

private enum class TimeTarget { START, END }

@Composable
fun ScheduleEditorScreen(onBack: () -> Unit, viewModel: ScheduleEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    val defaultName =
        stringResource(
            when (state.type) {
                ScheduleType.WORK -> R.string.schedule_name_default_work
                ScheduleType.STUDY -> R.string.schedule_name_default_study
                ScheduleType.SLEEP -> R.string.schedule_name_default_sleep
                ScheduleType.CUSTOM -> R.string.schedule_name_default_custom
            },
        )

    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.schedule_editor_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.schedule_name)) },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = 8.dp),
                )
            }
            item {
                SectionHeader(stringResource(R.string.schedule_type))
                FlowRow(
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScheduleType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            label = { Text(stringResource(scheduleTypeLabel(type))) },
                            modifier = Modifier.heightIn(min = TouchTarget),
                        )
                    }
                }
            }
            item {
                SectionHeader(stringResource(R.string.schedule_days))
                FlowRow(
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    orderedDays().forEach { day ->
                        val label = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        val selected = day in state.days
                        val description =
                            stringResource(
                                if (selected) R.string.schedule_day_selected else R.string.schedule_day_unselected,
                                day.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            )
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleDay(day) },
                            label = { Text(label) },
                            modifier =
                                Modifier
                                    .heightIn(min = TouchTarget)
                                    .semantics { contentDescription = description },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TimeField(
                        label = stringResource(R.string.schedule_start),
                        minute = state.startMinute,
                        onClick = { timeTarget = TimeTarget.START },
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = stringResource(R.string.schedule_end),
                        minute = state.endMinute,
                        onClick = { timeTarget = TimeTarget.END },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.crossesMidnight) {
                    Text(
                        text = stringResource(R.string.schedule_crosses_midnight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                    )
                }
            }
            item {
                SectionHeader(stringResource(R.string.schedule_apps))
                Text(
                    text = stringResource(R.string.schedule_apps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
            items(state.apps, key = { it.packageName }) { app ->
                val selected = app.packageName in state.packages
                val drawable = remember(app.packageName) { viewModel.icon(app.packageName) }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = TouchTarget + 8.dp)
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                            ) { viewModel.togglePackage(app.packageName) }
                            .padding(horizontal = ScreenPadding, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(drawable = drawable, label = app.label, size = 36.dp)
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Checkbox(checked = selected, onCheckedChange = null)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.save(defaultName, onBack) },
                    enabled = state.days.isNotEmpty(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding)
                            .heightIn(min = TouchTarget + 8.dp),
                ) {
                    Text(stringResource(R.string.schedule_save))
                }
                if (state.id != Schedule.NEW_ID) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.delete(onBack) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding)
                                .heightIn(min = TouchTarget),
                    ) {
                        Text(stringResource(R.string.schedule_delete))
                    }
                }
                Spacer(Modifier.height(ScreenPadding))
            }
        }
    }

    timeTarget?.let { target ->
        val initial = if (target == TimeTarget.START) state.startMinute else state.endMinute
        TimeDialog(
            initialMinute = initial,
            onDismiss = { timeTarget = null },
            onConfirm = { minute ->
                if (target == TimeTarget.START) viewModel.setStart(minute) else viewModel.setEnd(minute)
                timeTarget = null
            },
        )
    }
}

@Composable
private fun TimeField(label: String, minute: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = TouchTarget + 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(formatClock(minute), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val pickerState =
        rememberTimePickerState(
            initialHour = initialMinute / MINUTES_PER_HOUR,
            initialMinute = initialMinute % MINUTES_PER_HOUR,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_pick_time)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * MINUTES_PER_HOUR + pickerState.minute) }) {
                Text(stringResource(R.string.schedule_time_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_time_cancel)) } },
    )
}

private const val MINUTES_PER_HOUR = 60
