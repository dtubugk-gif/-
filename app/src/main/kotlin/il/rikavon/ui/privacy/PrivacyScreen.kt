package il.rikavon.ui.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import il.rikavon.R
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionHeader

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.privacy_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ScreenPadding),
        ) {
            Text(
                text = stringResource(R.string.privacy_summary),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
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
    SectionHeader(stringResource(title))
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 6.dp),
    )
}
