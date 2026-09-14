package il.rikavon.feature.blocker.ui.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.MinutesSlider
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SettingSwitchRow
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LimitEditorUiState(
    val packageName: String = "",
    val label: String = "",
    val minutes: Int = AppLimit.DEFAULT_MINUTES,
    val fullBlock: Boolean = false,
    val enabled: Boolean = true,
    val usage: AppUsage = AppUsage.empty(""),
    val strictMode: Boolean = false,
    val strictCountdown: Int? = null,
    val exists: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class LimitEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val limits: LimitsRepository,
    private val installed: InstalledAppsSource,
    usage: UsageRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val packageName: String = checkNotNull(savedState[ARG_PACKAGE])
    private val draft =
        MutableStateFlow(LimitEditorUiState(packageName = packageName, label = installed.label(packageName)))
    private val countdown = MutableStateFlow<Int?>(null)
    private var pendingAfterCountdown: (suspend () -> Unit)? = null

    val state: StateFlow<LimitEditorUiState> =
        combine(draft, usage.today, settings.settings, countdown) {
            d,
            snapshot,
            prefs,
            c,
            ->
            d.copy(usage = snapshot.usageOf(packageName), strictMode = prefs.strictMode, strictCountdown = c)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), draft.value)

    init {
        viewModelScope.launch {
            val existing = limits.byPackage(packageName)
            draft.value =
                draft.value.copy(
                    minutes = existing?.limitMinutes ?: AppLimit.DEFAULT_MINUTES,
                    fullBlock = existing?.fullBlock ?: false,
                    enabled = existing?.enabled ?: true,
                    exists = existing != null,
                    loaded = true,
                )
        }
    }

    fun icon() = installed.icon(packageName)

    fun setMinutes(value: Int) {
        draft.value = draft.value.copy(minutes = value)
    }

    fun setFullBlock(value: Boolean) {
        draft.value = draft.value.copy(fullBlock = value)
    }

    /** Disabling under strict mode waits [AnimationSpecs.STRICT_MODE_DELAY_SECONDS]; enabling is instant. */
    fun setEnabled(value: Boolean) {
        if (value || !state.value.strictMode) {
            draft.value = draft.value.copy(enabled = value)
            return
        }
        startCountdown { draft.value = draft.value.copy(enabled = false) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val d = draft.value
            limits.save(AppLimit(packageName, d.minutes, d.fullBlock, d.enabled, createdAt = 0L))
            onDone()
        }
    }

    fun remove(onDone: () -> Unit) {
        if (!state.value.strictMode) {
            viewModelScope.launch {
                limits.remove(packageName)
                onDone()
            }
            return
        }
        startCountdown {
            limits.remove(packageName)
            onDone()
        }
    }

    fun cancelCountdown() {
        countdown.value = null
        pendingAfterCountdown = null
    }

    private fun startCountdown(action: suspend () -> Unit) {
        pendingAfterCountdown = action
        viewModelScope.launch {
            for (remaining in AnimationSpecs.STRICT_MODE_DELAY_SECONDS downTo 1) {
                if (pendingAfterCountdown == null) return@launch
                countdown.value = remaining
                delay(ONE_SECOND_MILLIS)
            }
            val pending = pendingAfterCountdown ?: return@launch
            countdown.value = null
            pendingAfterCountdown = null
            pending()
        }
    }

    companion object {
        const val ARG_PACKAGE = "packageName"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val ONE_SECOND_MILLIS = 1_000L
    }
}

@Composable
fun LimitEditorScreen(onBack: () -> Unit, viewModel: LimitEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val icon = remember(state.packageName) { viewModel.icon() }

    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.limit_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ScreenPadding),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppIcon(drawable = icon, label = state.label, size = 56.dp)
                Column {
                    Text(state.label, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = stringResource(R.string.limit_today_opens, state.usage.opens),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val limitText =
                if (state.fullBlock) {
                    stringResource(
                        R.string.limit_full_block,
                    )
                } else {
                    formatMinutes(state.minutes)
                }
            Text(
                text = stringResource(R.string.limit_today_used, formatMinutes(state.usage.minutes), limitText),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            LinearProgressIndicator(
                progress = { (state.usage.minutes.toFloat() / state.minutes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding, vertical = 8.dp)
                        .height(10.dp),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.limit_minutes_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            Text(
                text = formatMinutes(state.minutes),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            MinutesSlider(
                value = state.minutes,
                onValueChange = viewModel::setMinutes,
                min = AppLimit.MIN_MINUTES,
                max = AppLimit.MAX_MINUTES,
                step = SLIDER_STEP,
                enabled = !state.fullBlock,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )

            SettingSwitchRow(
                title = stringResource(R.string.limit_full_block),
                subtitle = stringResource(R.string.limit_full_block_hint),
                checked = state.fullBlock,
                onCheckedChange = viewModel::setFullBlock,
            )
            SettingSwitchRow(
                title = stringResource(R.string.limit_enabled),
                subtitle = stringResource(R.string.limit_enabled_hint),
                checked = state.enabled,
                onCheckedChange = viewModel::setEnabled,
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.save(onBack) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding)
                        .heightIn(min = TouchTarget + 8.dp),
            ) {
                Text(stringResource(R.string.limit_save))
            }
            if (state.exists) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { viewModel.remove(onBack) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding)
                            .heightIn(min = TouchTarget),
                ) {
                    Text(stringResource(R.string.limit_remove))
                }
            }
        }
    }

    state.strictCountdown?.let { seconds ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCountdown,
            title = { Text(stringResource(R.string.limit_strict_title)) },
            text = { Text(stringResource(R.string.limit_strict_body, seconds)) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelCountdown) { Text(stringResource(R.string.limit_strict_cancel)) }
            },
        )
    }
}

private const val SLIDER_STEP = 5
