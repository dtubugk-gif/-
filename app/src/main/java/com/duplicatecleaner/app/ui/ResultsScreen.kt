package com.duplicatecleaner.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.duplicatecleaner.app.R
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onFilter: (MediaKind?) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onSetKeeper: (String, String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteOne: (FileEntry) -> Unit,
    onPreview: (String, FileEntry) -> Unit,
) {
    val visible = state.visibleGroups
    val selectedEntries = state.visibleSelectedEntries
    val selectedBytes = selectedEntries.sumOf { it.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onRescan) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.rescan))
                    }
                },
            )
        },
        bottomBar = {
            if (state.groups.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.select_all_duplicates)) }
                            TextButton(onClick = onClearSelection) { Text(stringResource(R.string.clear_selection)) }
                        }
                        Button(
                            onClick = onDeleteSelected,
                            enabled = selectedEntries.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (selectedEntries.isEmpty()) stringResource(R.string.nothing_selected)
                                else stringResource(R.string.delete_selected, selectedEntries.size, formatBytes(selectedBytes)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.groups.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding), onRescan = onRescan)
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { SummaryCard(state) }
            item(key = "filters") { FilterRow(state, onFilter) }
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.selection_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(visible, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    keeperKey = state.keeperOf(group),
                    selected = state.selected,
                    expanded = group.id !in state.collapsed,
                    onToggleExpanded = { onToggleCollapsed(group.id) },
                    onSetKeeper = { key -> onSetKeeper(group.id, key) },
                    onToggleSelected = onToggleSelected,
                    onDelete = onDeleteOne,
                    onPreview = { entry -> onPreview(group.id, entry) },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(state: UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.summary_space, formatBytes(state.totalWastedBytes)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.summary_groups, state.groups.size) + " · " +
                    stringResource(R.string.summary_files, state.totalRedundantFiles),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.summary_scanned, state.scannedFiles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun FilterRow(state: UiState, onFilter: (MediaKind?) -> Unit) {
    val counts = state.groups.groupingBy { it.kind }.eachCount()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.filter == null,
            onClick = { onFilter(null) },
            label = { Text("${stringResource(R.string.filter_all)} (${state.groups.size})") },
        )
        val labels = listOf(
            MediaKind.IMAGE to R.string.filter_images,
            MediaKind.VIDEO to R.string.filter_videos,
            MediaKind.AUDIO to R.string.filter_audio,
            MediaKind.OTHER to R.string.filter_other,
        )
        for ((kind, label) in labels) {
            val count = counts[kind] ?: 0
            if (count == 0) continue
            FilterChip(
                selected = state.filter == kind,
                onClick = { onFilter(if (state.filter == kind) null else kind) },
                label = { Text("${stringResource(label)} ($count)") },
                leadingIcon = { Icon(kindIcon(kind), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onRescan: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_duplicates), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_duplicates_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRescan) { Text(stringResource(R.string.rescan)) }
    }
}
