package il.rikavon.ui.premium

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.R
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.components.BottomActionBar
import il.rikavon.core.ui.components.Pill
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
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

/** Premium: two tier cards and one purchase action pinned at the bottom; no paywall anywhere else. */
@Composable
fun PremiumScreen(onBack: () -> Unit, viewModel: PremiumViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = rememberPinnedTopBarBehavior()
    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.premium_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (state.tier != Tier.PREMIUM) {
                Column {
                    BottomActionBar(
                        primaryText = stringResource(R.string.premium_buy),
                        onPrimary = viewModel::purchase,
                        primaryEnabled = state.billingAvailable,
                        modifier = Modifier.padding(bottom = if (state.billingAvailable) Spacing.sm else 0.dp),
                    )
                    if (!state.billingAvailable) {
                        Text(
                            text = stringResource(R.string.premium_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalExtraColors.current.onSurfaceMuted,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier.fillMaxWidth().padding(
                                    horizontal = ScreenPadding,
                                    vertical = Spacing.sm,
                                ),
                        )
                    }
                }
            }
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
                text = stringResource(R.string.premium_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtraColors.current.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
            )
            SectionLabel(stringResource(R.string.premium_free_title))
            SurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                Text(
                    text = stringResource(R.string.premium_free_body, Tier.FREE.maxLimitedApps, Tier.FREE.maxSchedules),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SectionLabel(stringResource(R.string.premium_paid_title), modifier = Modifier.padding(top = Spacing.md))
            SurfaceCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column {
                    Pill(text = stringResource(R.string.premium_badge), color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.premium_paid_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
            if (state.tier == Tier.PREMIUM) {
                Text(
                    text = stringResource(R.string.premium_active),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.lg),
                )
            }
        }
    }
}
