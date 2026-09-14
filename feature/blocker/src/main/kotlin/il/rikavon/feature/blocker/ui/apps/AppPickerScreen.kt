package il.rikavon.feature.blocker.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.ErrorState
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.ListRow
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SkeletonList
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R

/**
 * App picker: search field, filter chips, then one row per app with a switch. Four states: list,
 * skeleton while installed apps load, empty search, and a permission error with a fix action.
 */
@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    onOpenLimit: (String) -> Unit,
    onOpenPermissions: (() -> Unit)? = null,
    viewModel: AppPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = rememberPinnedTopBarBehavior()

    Scaffold(
        topBar = {
            RikavonTopBar(title = stringResource(R.string.apps_title), onBack = onBack, scrollBehavior = scrollBehavior)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(
                        top = padding.calculateTopPadding(),
                    ).nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            SearchField(query = state.query, onQueryChange = viewModel::setQuery)
            FilterRow(state = state, viewModel = viewModel)
            if (!state.hasUsagePermission) {
                ErrorState(
                    title = stringResource(R.string.apps_permission_error_title),
                    body = stringResource(R.string.apps_permission_error_body),
                    actionLabel = stringResource(R.string.apps_grant_permission),
                    onAction = onOpenPermissions,
                )
            }
            when {
                state.loading -> SkeletonList()
                state.rows.isEmpty() ->
                    EmptyState(
                        title = stringResource(R.string.apps_empty_title),
                        body = stringResource(R.string.apps_empty_body),
                        icon = Icons.Filled.Search,
                    )
                else ->
                    LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Spacing.xl)) {
                        if (state.tier == Tier.FREE) {
                            item {
                                Text(
                                    text = stringResource(R.string.apps_free_hint, Tier.FREE.maxLimitedApps),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalExtraColors.current.onSurfaceFaint,
                                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                                )
                            }
                        }
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
    }

    if (state.showFreeCap) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFreeCap,
            title = { Text(stringResource(R.string.apps_free_cap_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.apps_free_cap_body), style = MaterialTheme.typography.bodyMedium) },
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
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.apps_clear_search))
                }
            }
        },
        placeholder = { Text(stringResource(R.string.apps_search)) },
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

@Composable
private fun FilterRow(state: AppPickerUiState, viewModel: AppPickerViewModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = state.sort == AppSort.USAGE,
            onClick = { viewModel.setSort(AppSort.USAGE) },
            label = { Text(stringResource(R.string.apps_sort_usage), style = MaterialTheme.typography.labelMedium) },
            enabled = state.hasUsagePermission,
            modifier = Modifier.heightIn(min = Sizes.chip),
        )
        FilterChip(
            selected = state.sort == AppSort.NAME,
            onClick = { viewModel.setSort(AppSort.NAME) },
            label = { Text(stringResource(R.string.apps_sort_name), style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.heightIn(min = Sizes.chip),
        )
        FilterChip(
            selected = state.showSystem,
            onClick = { viewModel.setShowSystem(!state.showSystem) },
            label = { Text(stringResource(R.string.apps_show_system), style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.heightIn(min = Sizes.chip),
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
    val subtitle =
        if (row.limit != null) {
            stringResource(R.string.apps_tracked) + " · " + limitLabel(row.limit)
        } else {
            stringResource(R.string.apps_usage_week, formatMinutes(row.weekMinutes))
        }
    ListRow(
        title = row.app.label,
        subtitle = subtitle,
        subtitleColor = if (tracked) MaterialTheme.colorScheme.primary else LocalExtraColors.current.onSurfaceMuted,
        onClick = if (tracked) onOpen else onToggle,
        leading = { AppIcon(drawable = drawable, label = row.app.label) },
        trailing = {
            Switch(
                checked = tracked,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
        },
    )
}

@Composable
private fun limitLabel(limit: AppLimit): String =
    if (limit.fullBlock) stringResource(R.string.limit_full_block) else formatMinutes(limit.limitMinutes)
