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
import il.rikavon.core.data.repo.BreathingGateRepository
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.Pause
import il.rikavon.core.data.repo.PauseReason
import il.rikavon.core.data.repo.PauseRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.components.AppIcon
import il.rikavon.core.ui.components.BottomActionBar
import il.rikavon.core.ui.components.ConfirmDialog
import il.rikavon.core.ui.components.GroupCard
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.ListRow
import il.rikavon.core.ui.components.MinutesSlider
import il.rikavon.core.ui.components.PinDialog
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SettingSwitchRow
import il.rikavon.core.ui.components.SkeletonList
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.ThinBar
import il.rikavon.core.ui.components.TonalButton
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R
import il.rikavon.feature.blocker.ui.common.PinGate
import il.rikavon.feature.blocker.ui.sites.untilLabel
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
    /** Opens allowed per day; 0 is off. */
    val maxOpens: Int = 0,
    /** Longest sitting in minutes before a break; 0 is off. */
    val sessionMinutes: Int = 0,
    /** The pet calls and begs the moment this app opens. */
    val callOnOpen: Boolean = true,
    /** A "block now" or a session break in force for this app. */
    val pause: Pause? = null,
    val exists: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class LimitEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val limits: LimitsRepository,
    private val installed: InstalledAppsSource,
    usage: UsageRepository,
    private val settings: SettingsRepository,
    private val gate: BreathingGateRepository,
    private val pauses: PauseRepository,
    private val time: TimeSource,
) : ViewModel() {
    val packageName: String = checkNotNull(savedState[ARG_PACKAGE])
    private var existing: AppLimit? = null
    private val draft =
        MutableStateFlow(LimitEditorUiState(packageName = packageName, label = installed.label(packageName)))
    private val countdown = MutableStateFlow<Int?>(null)
    private var pendingAfterCountdown: (suspend () -> Unit)? = null

    @Volatile
    private var pinHash: String? = null

    /** The settings lock: anything that loosens this limit goes through it. */
    val lock = PinGate(pinHash = { pinHash })

    val state: StateFlow<LimitEditorUiState> =
        combine(draft, usage.today, settings.settings, countdown, pauses.pauses) { d, snapshot, prefs, c, pauseList ->
            pinHash = prefs.pinHash
            d.copy(
                usage = snapshot.usageOf(packageName),
                strictMode = prefs.strictMode,
                strictCountdown = c,
                pause = pauseList.firstOrNull { it.packageName == packageName && it.untilMillis > time.nowMillis() },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), draft.value)

    init {
        viewModelScope.launch {
            val existing = limits.byPackage(packageName).also { this@LimitEditorViewModel.existing = it }
            draft.value =
                draft.value.copy(
                    minutes = existing?.limitMinutes ?: AppLimit.DEFAULT_MINUTES,
                    fullBlock = existing?.fullBlock ?: false,
                    maxOpens = existing?.maxOpens ?: 0,
                    sessionMinutes = existing?.sessionMinutes ?: 0,
                    callOnOpen = existing?.callOnOpen ?: true,
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

    fun setMaxOpens(value: Int) {
        draft.value = draft.value.copy(maxOpens = value)
    }

    fun setSessionMinutes(value: Int) {
        draft.value = draft.value.copy(sessionMinutes = value)
    }

    fun setCallOnOpen(value: Boolean) {
        draft.value = draft.value.copy(callOnOpen = value)
    }

    /** Block now, for [minutes]; null means until tomorrow. Tightening never asks for anything. */
    fun pauseFor(minutes: Int?) {
        viewModelScope.launch {
            val now = time.now()
            val until =
                if (minutes == null) {
                    now
                        .toLocalDate()
                        .plusDays(1)
                        .atStartOfDay(now.zone)
                        .toInstant()
                        .toEpochMilli()
                } else {
                    time.nowMillis() + minutes * MILLIS_PER_MINUTE
                }
            pauses.pause(packageName, until, PauseReason.MANUAL)
        }
    }

    fun liftPause() = guarded { pauses.lift(packageName) }

    /** Disabling is loosening: the PIN if settings are locked, then the strict-mode delay; enabling is instant. */
    fun setEnabled(value: Boolean) {
        if (value) {
            draft.value = draft.value.copy(enabled = true)
            return
        }
        guarded { draft.value = draft.value.copy(enabled = false) }
    }

    /**
     * Saves; a changed (or new) limit also arms the breathing pause for that app's next open. A change that
     * gives more room goes through the settings lock first.
     */
    fun save(onDone: () -> Unit) {
        val d = draft.value
        val after =
            AppLimit(packageName, d.minutes, d.fullBlock, d.enabled, 0L, d.maxOpens, d.sessionMinutes, d.callOnOpen)
        val persist: suspend () -> Unit = {
            val changed = existing?.let { it != after.copy(createdAt = it.createdAt) } ?: true
            limits.save(after)
            if (changed && settings.current().breathingGateEnabled) gate.mark(packageName)
            onDone()
        }
        if (LimitLoosening.isLooser(existing, after)) {
            lock.require { viewModelScope.launch { persist() } }
        } else {
            viewModelScope.launch { persist() }
        }
    }

    fun remove(onDone: () -> Unit) =
        guarded {
            limits.remove(packageName)
            onDone()
        }

    /** Loosening: the PIN when settings are locked, then the strict-mode delay when that is on. */
    private fun guarded(action: suspend () -> Unit) {
        lock.require {
            if (state.value.strictMode) startCountdown(action) else viewModelScope.launch { action() }
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
        private const val MILLIS_PER_MINUTE = 60_000L
        const val PAUSE_SHORT_MINUTES = 15
        const val PAUSE_HOUR_MINUTES = 60
        const val PAUSE_LONG_MINUTES = 180
    }
}

/** Limit editor: app hero with today's usage, the limit value and slider, options, save pinned at the bottom. */
@Composable
fun LimitEditorScreen(onBack: () -> Unit, viewModel: LimitEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lock by viewModel.lock.state.collectAsStateWithLifecycle()
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
            SectionLabel(stringResource(R.string.limit_section_pet), modifier = Modifier.padding(top = Spacing.sm))
            GroupCard {
                SettingSwitchRow(
                    title = stringResource(R.string.limit_call_on_open),
                    subtitle = stringResource(R.string.limit_call_on_open_hint),
                    checked = state.callOnOpen,
                    onCheckedChange = viewModel::setCallOnOpen,
                )
            }
            BlockNowSection(state, viewModel)
            CapSection(
                title = stringResource(R.string.limit_opens),
                hint = stringResource(R.string.limit_opens_hint),
                value = state.maxOpens,
                valueText = stringResource(R.string.limit_opens_value, state.maxOpens),
                max = AppLimit.MAX_OPENS,
                step = 1,
                enabled = !state.fullBlock,
                onValueChange = viewModel::setMaxOpens,
            )
            CapSection(
                title = stringResource(R.string.limit_session),
                hint = stringResource(R.string.limit_session_hint),
                value = state.sessionMinutes,
                valueText = stringResource(R.string.limit_session_value, state.sessionMinutes),
                max = AppLimit.MAX_SESSION_MINUTES,
                step = AppLimit.MIN_SESSION_MINUTES,
                enabled = !state.fullBlock,
                onValueChange = viewModel::setSessionMinutes,
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
    if (lock.asking) {
        PinDialog(
            title = stringResource(R.string.pin_enter_title),
            body = stringResource(R.string.pin_enter_body),
            confirmText = stringResource(R.string.pin_ok),
            cancelText = stringResource(R.string.pin_cancel),
            error = if (lock.wrong) stringResource(R.string.pin_wrong) else null,
            onSubmit = viewModel.lock::submit,
            onDismiss = viewModel.lock::cancel,
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

/** "Block now": the running pause with a way out of it, or four durations to start one. */
@Composable
private fun BlockNowSection(state: LimitEditorUiState, viewModel: LimitEditorViewModel) {
    SectionLabel(stringResource(R.string.limit_section_block_now), modifier = Modifier.padding(top = Spacing.sm))
    GroupCard {
        val pause = state.pause
        if (pause != null) {
            val res =
                if (pause.reason ==
                    PauseReason.BREAK
                ) {
                    R.string.limit_paused_break
                } else {
                    R.string.limit_paused_until
                }
            ListRow(
                title = stringResource(res, untilLabel(pause.untilMillis)),
                subtitle = stringResource(R.string.limit_block_now_hint),
                trailing = {
                    LinkButton(
                        text = stringResource(R.string.limit_pause_lift),
                        onClick = viewModel::liftPause,
                    )
                },
            )
        } else {
            ListRow(
                title = stringResource(R.string.limit_section_block_now),
                subtitle = stringResource(R.string.limit_block_now_hint),
            )
            Column(
                modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TonalButton(
                        text = stringResource(R.string.limit_pause_15),
                        onClick = { viewModel.pauseFor(LimitEditorViewModel.PAUSE_SHORT_MINUTES) },
                        modifier = Modifier.weight(1f),
                    )
                    TonalButton(
                        text = stringResource(R.string.limit_pause_60),
                        onClick = { viewModel.pauseFor(LimitEditorViewModel.PAUSE_HOUR_MINUTES) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TonalButton(
                        text = stringResource(R.string.limit_pause_180),
                        onClick = { viewModel.pauseFor(LimitEditorViewModel.PAUSE_LONG_MINUTES) },
                        modifier = Modifier.weight(1f),
                    )
                    TonalButton(
                        text = stringResource(R.string.limit_pause_tomorrow),
                        onClick = { viewModel.pauseFor(null) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One optional cap (opens per day, one sitting): the value, "Off" at zero, a slider and one line of why. */
@Composable
private fun CapSection(
    title: String,
    hint: String,
    value: Int,
    valueText: String,
    max: Int,
    step: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    SectionLabel(title, modifier = Modifier.padding(top = Spacing.sm))
    Text(
        text = if (value == 0) stringResource(R.string.limit_off) else valueText,
        style = MaterialTheme.typography.titleLarge,
        color = if (value == 0) LocalExtraColors.current.onSurfaceMuted else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
    MinutesSlider(
        value = value,
        onValueChange = onValueChange,
        min = 0,
        max = max,
        step = step,
        enabled = enabled,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = LocalExtraColors.current.onSurfaceMuted,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
}
