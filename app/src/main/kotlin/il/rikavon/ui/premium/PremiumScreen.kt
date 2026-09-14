package il.rikavon.ui.premium

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.R
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionHeader
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.entitlement.BillingGateway
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PremiumUiState(val tier: Tier = Tier.FREE, val billingAvailable: Boolean = false)

@HiltViewModel
class PremiumViewModel @Inject constructor(settings: SettingsRepository, private val billing: BillingGateway) :
    ViewModel() {
        val state: StateFlow<PremiumUiState> =
            settings.settings
                .map { PremiumUiState(Tier.of(it.premium), billing.isAvailable()) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PremiumUiState())

        fun purchase() {
            viewModelScope.launch { billing.purchasePremium() }
        }

        companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

@Composable
fun PremiumScreen(onBack: () -> Unit, viewModel: PremiumViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.premium_title), onBack = onBack) },
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
                text = stringResource(R.string.premium_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            SectionHeader(stringResource(R.string.premium_free_title))
            Text(
                text = stringResource(R.string.premium_free_body, Tier.FREE.maxLimitedApps, Tier.FREE.maxSchedules),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            SectionHeader(stringResource(R.string.premium_paid_title))
            Text(
                text = stringResource(R.string.premium_paid_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            Spacer(Modifier.height(20.dp))
            if (state.tier == Tier.PREMIUM) {
                Text(
                    text = stringResource(R.string.premium_active),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            } else {
                Button(
                    onClick = viewModel::purchase,
                    enabled = state.billingAvailable,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding)
                            .heightIn(min = TouchTarget + 8.dp),
                ) {
                    Text(stringResource(R.string.premium_buy))
                }
                if (!state.billingAvailable) {
                    Text(
                        text = stringResource(R.string.premium_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
