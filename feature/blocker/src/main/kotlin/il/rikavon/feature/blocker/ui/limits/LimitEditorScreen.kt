package il.rikavon.feature.blocker.ui.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
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
import il.rikavon.core.ui.components.BottomActionBar
import il.rikavon.core.ui.components.ConfirmDialog
import il.rikavon.core.ui.components.GroupCard
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.MinutesSlider
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SettingSwitchRow
import il.rikavon.core.ui.components.SkeletonList
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.ThinBar
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
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

/** Limit editor: app hero with today's usage, the limit value and slider, options, save pinned at the bottom. */
@Composable
fun LimitEditorScreen(onBack: () -> Unit, viewModel: LimitEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val icon = remember(state.packageName) { viewModel.icon() }
    val scrollBehavior = rememberPinnedTopBarBehavior()
    var confirmRemove by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.limit_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (state.loaded) {
                BottomActionBar(
                    primaryText = stringResource(R.string.limit_save),
                    onPrimary = { viewModel.save(onBack) },
                    secondaryText = if (state.exists) stringResource(R.string.limit_remove) else null,
                    onSecondary = if (state.exists) ({ confirmRemove = true }) else null,
                    secondaryDestructive = true,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (!state.loaded) {
            SkeletonList(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.lg),
        ) {
            AppHero(state = state, icon = icon)
            SectionLabel(stringResource(R.string.limit_section_limit), modifier = Modifier.padding(top = Spacing.sm))
            Text(
                text = if (state.fullBlock) stringResource(R.string.limit_full_block) else formatMinutes(state.minutes),
                style = MaterialTheme.typography.displaySmall,
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
            SectionLabel(stringResource(R.string.limit_section_options), modifier = Modifier.padding(top = Spacing.sm))
            GroupCard {
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
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.limit_remove_confirm_title),
            body = stringResource(R.string.limit_remove_confirm_body, state.label),
            confirmText = stringResource(R.string.limit_remove_confirm),
            onConfirm = {
                confirmRemove = false
                viewModel.remove(onBack)
            },
            onDismiss = { confirmRemove = false },
        )
    }
    state.strictCountdown?.let { seconds ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCountdown,
            title = { Text(stringResource(R.string.limit_strict_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    stringResource(R.string.limit_strict_body, seconds),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {},
            dismissButton = {
                LinkButton(text = stringResource(R.string.limit_strict_cancel), onClick = viewModel::cancelCountdown)
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun AppHero(state: LimitEditorUiState, icon: android.graphics.drawable.Drawable?) {
    val extras = LocalExtraColors.current
    val limitText = if (state.fullBlock) stringResource(R.string.limit_full_block) else formatMinutes(state.minutes)
    val ratio = (state.usage.minutes.toFloat() / state.minutes.coerceAtLeast(1)).coerceIn(0f, 1f)
    val barColor =
        when {
            ratio >= 1f -> extras.danger
            ratio >= NEAR_LIMIT -> extras.overLimit
            else -> MaterialTheme.colorScheme.primary
        }
    SurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = Spacing.sm)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                AppIcon(drawable = icon, label = state.label, size = HERO_ICON)
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.label, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(
                        text = stringResource(R.string.limit_today_opens, state.usage.opens),
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.onSurfaceMuted,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.limit_usage_today),
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.limit_today_used, formatMinutes(state.usage.minutes), limitText),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ratio >= NEAR_LIMIT) barColor else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            ThinBar(progress = ratio, color = barColor, height = BAR_HEIGHT)
        }
    }
}

private const val SLIDER_STEP = 5
private const val NEAR_LIMIT = 0.66f
private val HERO_ICON = Spacing.xxxl + Spacing.sm
private val BAR_HEIGHT = Spacing.sm - Spacing.xs / 2
