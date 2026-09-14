package il.rikavon.feature.blocker.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R

@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    onOpenLimit: (String) -> Unit,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.apps_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.apps_search)) },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.sort == AppSort.USAGE,
                    onClick = { viewModel.setSort(AppSort.USAGE) },
                    label = { Text(stringResource(R.string.apps_sort_usage)) },
                    enabled = state.hasUsagePermission,
                    modifier = Modifier.heightIn(min = TouchTarget),
                )
                FilterChip(
                    selected = state.sort == AppSort.NAME,
                    onClick = { viewModel.setSort(AppSort.NAME) },
                    label = { Text(stringResource(R.string.apps_sort_name)) },
                    modifier = Modifier.heightIn(min = TouchTarget),
                )
                FilterChip(
                    selected = state.showSystem,
                    onClick = { viewModel.setShowSystem(!state.showSystem) },
                    label = { Text(stringResource(R.string.apps_show_system)) },
                    modifier = Modifier.heightIn(min = TouchTarget),
                )
            }
            if (!state.hasUsagePermission) {
                Text(
                    text = stringResource(R.string.apps_no_usage_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
                )
            }
            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
                )
            } else if (state.rows.isEmpty()) {
                EmptyState(text = stringResource(R.string.apps_empty))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + ScreenPadding)) {
                items(state.rows, key = { it.app.packageName }) { row ->
                    AppRowItem(
                        row = row,
                        icon = { viewModel.icon(row.app.packageName) },
                        onToggle = { viewModel.toggle(row.app, onOpenLimit) },
                        onOpen = { onOpenLimit(row.app.packageName) },
                    )
                }
            }
        }
    }

    if (state.showFreeCap) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFreeCap,
            title = { Text(stringResource(R.string.apps_free_cap_title)) },
            text = { Text(stringResource(R.string.apps_free_cap_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissFreeCap) { Text(stringResource(R.string.apps_free_cap_ok)) }
            },
        )
    }
}

@Composable
private fun AppRowItem(
    row: AppRow,
    icon: () -> android.graphics.drawable.Drawable?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val drawable = remember(row.app.packageName) { icon() }
    val toggleDescription = stringResource(R.string.apps_toggle_description, row.app.label)
    val tracked = row.limit != null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget + 16.dp)
                .toggleable(
                    value = tracked,
                    role = Role.Switch,
                    onValueChange = { if (tracked) onOpen() else onToggle() },
                ).padding(horizontal = ScreenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(drawable = drawable, label = row.app.label)
        Column(modifier = Modifier.weight(1f)) {
            Text(row.app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            val subtitle =
                if (row.limit != null) {
                    stringResource(R.string.apps_tracked) + " · " + limitLabel(row.limit)
                } else {
                    stringResource(R.string.apps_usage_week, formatMinutes(row.weekMinutes))
                }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (row.limit !=
                        null
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
            )
        }
        Switch(
            checked = tracked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = toggleDescription },
        )
    }
}

@Composable
private fun limitLabel(limit: il.rikavon.core.data.model.AppLimit): String =
    if (limit.fullBlock) stringResource(R.string.limit_full_block) else formatMinutes(limit.limitMinutes)
