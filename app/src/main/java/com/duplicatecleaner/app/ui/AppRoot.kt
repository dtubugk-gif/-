package com.duplicatecleaner.app.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.duplicatecleaner.app.Permissions
import com.duplicatecleaner.app.R
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.util.formatBytes
import com.duplicatecleaner.app.util.openExternally

@Composable
fun AppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onSystemConfirmationResult(result.resultCode == Activity.RESULT_OK) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    val confirmation = state.systemConfirmation
    LaunchedEffect(confirmation) {
        if (confirmation != null) {
            systemDeleteLauncher.launch(IntentSenderRequest.Builder(confirmation.intentSender).build())
            viewModel.onSystemConfirmationLaunched()
        }
    }

    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(messageText(context, message))
            viewModel.dismissMessage()
        }
    }

    val openExternal: (FileEntry) -> Unit = { entry ->
        if (!openExternally(context, entry)) viewModel.showOpenFailed()
    }

    when (state.stage) {
        Stage.START, Stage.SCANNING -> ScanScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onRequestMediaPermission = { permissionLauncher.launch(Permissions.mediaPermissions()) },
            onOpenAllFilesSettings = { Permissions.openAllFilesAccessSettings(context) },
            onToggleKind = viewModel::toggleKind,
            onStartScan = viewModel::startScan,
            onCancelScan = viewModel::cancelScan,
            onShowResults = viewModel::showResults,
        )
        Stage.RESULTS -> {
            BackHandler(onBack = viewModel::backToStart)
            ResultsScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onBack = viewModel::backToStart,
                onRescan = viewModel::startScan,
                onFilter = viewModel::setFilter,
                onToggleCollapsed = viewModel::toggleCollapsed,
                onSetKeeper = viewModel::setKeeper,
                onToggleSelected = viewModel::toggleSelected,
                onSelectAll = viewModel::selectAllVisible,
                onClearSelection = viewModel::clearVisibleSelection,
                onDeleteSelected = viewModel::requestDeleteSelected,
                onDeleteOne = viewModel::requestDeleteOne,
                onPreview = { groupId, entry -> viewModel.openPreview(groupId, entry.key) },
            )
        }
    }

    state.preview?.let { preview ->
        val group = state.groups.firstOrNull { it.id == preview.groupId }
        if (group != null) {
            PreviewDialog(
                group = group,
                initialKey = preview.fileKey,
                keeperKey = state.keeperOf(group),
                selected = state.selected,
                onClose = viewModel::closePreview,
                onSetKeeper = { key -> viewModel.setKeeper(group.id, key) },
                onToggleSelected = viewModel::toggleSelected,
                onDeleteOne = viewModel::requestDeleteOne,
                onDeleteGroupDuplicates = { viewModel.requestDeleteGroupDuplicates(group.id) },
                onOpenExternal = openExternal,
            )
        }
    }

    when (val dialog = state.dialog) {
        is DialogState.ConfirmDeleteMany -> ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete_title, dialog.entries.size),
            body = stringResource(
                R.string.confirm_delete_body,
                dialog.entries.size,
                formatBytes(dialog.entries.sumOf { it.size }),
            ),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        is DialogState.ConfirmDeleteOne -> ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete_one_title),
            body = stringResource(
                R.string.confirm_delete_one_body,
                dialog.entry.path.ifEmpty { dialog.entry.name },
            ),
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }

    state.deletion?.let { DeletingDialog(it) }
}

@Composable
private fun ConfirmDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DeletingDialog(deletion: DeletionState) {
    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.deleting_title)) },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { if (deletion.total == 0) 0f else deletion.done.toFloat() / deletion.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.deleting_progress, deletion.done, deletion.total))
                if (deletion.waitingForSystem) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.deleting_system_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { },
    )
}

private fun messageText(context: Context, message: UiMessage): String = when (message) {
    is UiMessage.Deleted ->
        if (message.failed > 0) {
            context.getString(R.string.deleted_partial, message.count, formatBytes(message.bytes), message.failed)
        } else {
            context.getString(R.string.deleted_summary, message.count, formatBytes(message.bytes))
        }
    UiMessage.DeleteCancelled -> context.getString(R.string.delete_cancelled)
    UiMessage.DeleteFailed -> context.getString(R.string.delete_failed)
    is UiMessage.ScanFailed -> context.getString(R.string.scan_failed, message.reason)
    UiMessage.OpenFailed -> context.getString(R.string.open_failed)
}
