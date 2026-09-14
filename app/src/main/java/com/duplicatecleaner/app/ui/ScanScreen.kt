package com.duplicatecleaner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duplicatecleaner.app.Permissions
import com.duplicatecleaner.app.R
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.model.ScanPhase
import com.duplicatecleaner.app.model.ScanProgress

@Composable
fun ScanScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onRequestMediaPermission: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onToggleKind: (MediaKind) -> Unit,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onShowResults: () -> Unit,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.scan_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.stage == Stage.SCANNING) {
                ProgressCard(progress = state.progress, onCancel = onCancelScan)
            } else {
                PermissionCard(
                    title = stringResource(R.string.perm_media_title),
                    description = stringResource(
                        if (state.hasPartialMediaAccess) R.string.perm_partial_desc else R.string.perm_media_desc,
                    ),
                    granted = state.hasMediaPermission && !state.hasPartialMediaAccess,
                    actionLabel = stringResource(R.string.perm_grant),
                    onAction = onRequestMediaPermission,
                )
                if (Permissions.supportsAllFilesAccessSetting) {
                    PermissionCard(
                        title = stringResource(R.string.perm_all_files_title),
                        description = stringResource(R.string.perm_all_files_desc),
                        granted = state.hasAllFilesAccess,
                        actionLabel = stringResource(R.string.perm_open_settings),
                        onAction = onOpenAllFilesSettings,
                    )
                }
                ScopeCard(state = state, onToggleKind = onToggleKind)

                Button(
                    onClick = onStartScan,
                    enabled = state.canStartScan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start_scan), style = MaterialTheme.typography.titleMedium)
                }
                if (state.scope.isEmpty()) {
                    Text(
                        text = stringResource(R.string.scope_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.hasResults) {
                    OutlinedButton(onClick = onShowResults, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.show_last_results))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (granted) stringResource(R.string.perm_granted) else description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ScopeCard(state: UiState, onToggleKind: (MediaKind) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.scope_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopeChip(state, MediaKind.IMAGE, stringResource(R.string.scope_images), onToggleKind)
                ScopeChip(state, MediaKind.VIDEO, stringResource(R.string.scope_videos), onToggleKind)
                ScopeChip(state, MediaKind.AUDIO, stringResource(R.string.scope_audio), onToggleKind)
            }
            ScopeChip(
                state = state,
                kind = MediaKind.OTHER,
                label = stringResource(R.string.scope_other),
                onToggleKind = onToggleKind,
                enabled = state.hasAllFilesAccess,
            )
            if (!state.hasAllFilesAccess) {
                Text(
                    text = stringResource(R.string.scope_other_needs_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScopeChip(
    state: UiState,
    kind: MediaKind,
    label: String,
    onToggleKind: (MediaKind) -> Unit,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = kind in state.scope && enabled,
        onClick = { onToggleKind(kind) },
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(kindIcon(kind), contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun ProgressCard(progress: ScanProgress?, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val phaseText = when (progress?.phase) {
                null, ScanPhase.LISTING -> R.string.phase_listing
                ScanPhase.SIZES -> R.string.phase_sizes
                ScanPhase.QUICK_HASH -> R.string.phase_quick_hash
                ScanPhase.FULL_HASH -> R.string.phase_full_hash
            }
            Text(stringResource(phaseText), style = MaterialTheme.typography.titleMedium)

            val determinate = progress != null && progress.phase != ScanPhase.LISTING && progress.total > 0
            if (determinate) {
                LinearProgressIndicator(
                    progress = { progress!!.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val countText = when {
                progress == null -> ""
                progress.phase == ScanPhase.LISTING -> stringResource(R.string.progress_found, progress.done)
                else -> stringResource(R.string.progress_count, progress.done, progress.total)
            }
            Text(countText, style = MaterialTheme.typography.bodyMedium)
            progress?.currentName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}
