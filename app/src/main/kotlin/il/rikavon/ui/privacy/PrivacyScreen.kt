package il.rikavon.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import il.rikavon.R
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.Spacing

/** Privacy: the promise in one line, then one card per topic. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val scrollBehavior = rememberPinnedTopBarBehavior()
    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.privacy_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.privacy_summary),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
            )
            Section(R.string.privacy_collect_title, R.string.privacy_collect_body)
            Section(R.string.privacy_network_title, R.string.privacy_network_body)
            Section(R.string.privacy_permissions_title, R.string.privacy_permissions_body)
            Section(R.string.privacy_storage_title, R.string.privacy_storage_body)
            Section(R.string.privacy_delete_title, R.string.privacy_delete_body)
        }
    }
}

@Composable
private fun Section(title: Int, body: Int) {
    SurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = Spacing.xs)) {
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
