package il.rikavon.feature.blocker.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.BottomActionBar
import il.rikavon.core.ui.components.ConfirmDialog
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.util.formatClock
import il.rikavon.feature.blocker.R
import java.time.format.TextStyle
import java.util.Locale

private enum class TimeTarget { START, END }

/** Schedule editor: name, type, days, hours, app checklist; save pinned at the bottom, delete behind a confirmation. */
@Composable
fun ScheduleEditorScreen(onBack: () -> Unit, viewModel: ScheduleEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var timeTarget by remember { mutableStateOf<TimeTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scrollBehavior = rememberPinnedTopBarBehavior()
    val defaultName =
        stringResource(
            when (state.type) {
                ScheduleType.WORK -> R.string.schedule_name_default_work
                ScheduleType.STUDY -> R.string.schedule_name_default_study
                ScheduleType.SLEEP -> R.string.schedule_name_default_sleep
                ScheduleType.CUSTOM -> R.string.schedule_name_default_custom
            },
        )
    val editing = state.id != Schedule.NEW_ID

    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.schedule_editor_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomActionBar(
                primaryText = stringResource(R.string.schedule_save),
                onPrimary = { viewModel.save(defaultName, onBack) },
                primaryEnabled = state.days.isNotEmpty(),
                secondaryText = if (editing) stringResource(R.string.schedule_delete) else null,
                onSecondary = if (editing) ({ confirmDelete = true }) else null,
                secondaryDestructive = true,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom =
                        padding.calculateBottomPadding() + Spacing.lg,
                ),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.schedule_name)) },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = Spacing.sm)
                            .heightIn(min = Sizes.input),
                )
            }
            item {
                SectionLabel(stringResource(R.string.schedule_type))
                FlowRow(
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ScheduleType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            label = {
                                Text(
                                    stringResource(scheduleTypeLabel(type)),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier.heightIn(min = Sizes.chip),
                        )
                    }
                }
            }
            item {
                SectionLabel(stringResource(R.string.schedule_days), modifier = Modifier.padding(top = Spacing.sm))
                FlowRow(
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            modifier =
                                Modifier
                                    .heightIn(min = Sizes.chip)
                                    .semantics { contentDescription = description },
                        )
                    }
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.schedule_section_time),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                    )
                }
            }
            item {
                SectionLabel(stringResource(R.string.schedule_apps), modifier = Modifier.padding(top = Spacing.sm))
                Text(
                    text = stringResource(R.string.schedule_apps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.onSurfaceMuted,
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
                            .heightIn(min = Sizes.row)
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                            ) { viewModel.togglePackage(app.packageName) }
                            .padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    AppIcon(drawable = drawable, label = app.label)
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Checkbox(checked = selected, onCheckedChange = null)
                }
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
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.schedule_delete_confirm_title),
            body = stringResource(R.string.schedule_delete_confirm_body, state.name.ifBlank { defaultName }),
            confirmText = stringResource(R.string.schedule_delete_confirm),
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TimeField(label: String, minute: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SurfaceCard(onClick = onClick, modifier = modifier.heightIn(min = Sizes.rowTwoLine)) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalExtraColors.current.onSurfaceMuted)
            Text(
                formatClock(minute),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
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
        title = { Text(stringResource(R.string.schedule_pick_time), style = MaterialTheme.typography.titleLarge) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            LinkButton(
                text = stringResource(R.string.schedule_time_ok),
                onClick = { onConfirm(pickerState.hour * MINUTES_PER_HOUR + pickerState.minute) },
            )
        },
        dismissButton = { LinkButton(text = stringResource(R.string.schedule_time_cancel), onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

private const val MINUTES_PER_HOUR = 60
