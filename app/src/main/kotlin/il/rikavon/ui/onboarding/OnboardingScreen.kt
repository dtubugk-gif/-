package il.rikavon.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.R
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.permissions.AppPermission
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.permissions.PermissionState
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.LocalReducedMotion
import il.rikavon.core.ui.anim.floatLoop
import il.rikavon.core.ui.anim.pageTransform
import il.rikavon.core.ui.anim.popEnter
import il.rikavon.core.ui.anim.popExit
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.Pill
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SegmentPills
import il.rikavon.core.ui.components.ThinBar
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.feature.blocker.service.ServiceStarter
import il.rikavon.feature.mascot.model.Localized
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.ui.MascotView
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ordered onboarding pages; permissions the platform does not need are skipped automatically. */
enum class OnboardingStep(val permission: AppPermission?) {
    WELCOME(null),
    USAGE(AppPermission.USAGE_ACCESS),
    OVERLAY(AppPermission.OVERLAY),
    NOTIFICATIONS(AppPermission.NOTIFICATIONS),
    EXACT_ALARM(AppPermission.EXACT_ALARM),
    BATTERY(AppPermission.BATTERY_OPTIMIZATION),
    DONE(null),
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val permissions: PermissionState? = null,
    val skin: MascotSkin? = null,
    val steps: List<OnboardingStep> = OnboardingStep.entries,
    val language: AppLanguage = AppLanguage.ENGLISH,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissions: PermissionChecker,
    private val settings: SettingsRepository,
    private val starter: ServiceStarter,
    selectedMascot: SelectedMascot,
) : ViewModel() {
    private val step = MutableStateFlow(OnboardingStep.WELCOME)
    private val permissionState = MutableStateFlow(permissions.state())
    private val steps = OnboardingStep.entries.filter { it.appliesToThisDevice() }

    val state: StateFlow<OnboardingUiState> =
        combine(step, permissionState, selectedMascot.skin, settings.settings) { s, p, skin, prefs ->
            OnboardingUiState(s, p, skin, steps, prefs.language)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            OnboardingUiState(steps = steps),
        )

    fun refreshPermissions() {
        permissionState.value = permissions.state()
    }

    /** Language choice on the welcome page; the activity recreates with the new locale. */
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun next() {
        val index = steps.indexOf(step.value)
        if (index < steps.lastIndex) step.value = steps[index + 1]
    }

    fun back() {
        val index = steps.indexOf(step.value)
        if (index > 0) step.value = steps[index - 1]
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            starter.startIfConfigured()
            onDone()
        }
    }

    private fun OnboardingStep.appliesToThisDevice(): Boolean =
        when (this) {
            OnboardingStep.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            OnboardingStep.EXACT_ALARM -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            else -> true
        }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Onboarding: progress line, the pet (wilted until the permission is granted), one title, one paragraph,
 * and exactly one filled action pinned at the bottom with text buttons for back / skip.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
        }

    val step = state.step
    val granted = step.permission?.let { state.permissions?.granted(it) } ?: false
    val index = state.steps.indexOf(step).coerceAtLeast(0)
    val needsGrant = step.permission != null && !granted
    val extras = LocalExtraColors.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = ScreenPadding, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                when {
                    needsGrant ->
                        PrimaryButton(
                            text = stringResource(R.string.onboarding_grant),
                            onClick = {
                                when (step) {
                                    OnboardingStep.NOTIFICATIONS ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    else -> context.startActivity(step.settingsIntent(context.packageName))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    step == OnboardingStep.DONE ->
                        PrimaryButton(
                            text = stringResource(R.string.onboarding_finish),
                            onClick = { viewModel.finish(onDone) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    else ->
                        PrimaryButton(
                            text = stringResource(R.string.onboarding_next),
                            onClick = viewModel::next,
                            modifier = Modifier.fillMaxWidth(),
                        )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LinkButton(
                        text = stringResource(R.string.onboarding_back),
                        onClick = viewModel::back,
                        enabled =
                            index > 0,
                    )
                    if (needsGrant) {
                        LinkButton(text = stringResource(R.string.onboarding_skip), onClick = viewModel::next)
                    }
                }
            }
        },
    ) { padding ->
        val progressLabel = stringResource(R.string.onboarding_progress)
        val reduced = LocalReducedMotion.current
        val hebrewUi =
            state.language == AppLanguage.HEBREW ||
                (state.language == AppLanguage.SYSTEM && UiLanguage.current() == Localized.DEFAULT_LANGUAGE)
        val progress by animateFloatAsState(
            targetValue = (index + 1).toFloat() / state.steps.size,
            animationSpec = if (reduced) AnimationSpecs.Reduced else AnimationSpecs.Count,
            label = "onboardingProgress",
        )
        var lastIndex by remember { mutableIntStateOf(index) }
        val forward = index >= lastIndex
        SideEffect { lastIndex = index }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_of, index + 1, state.steps.size),
                style = MaterialTheme.typography.labelMedium,
                color = extras.onSurfaceMuted,
                modifier = Modifier.fillMaxWidth(),
            )
            ThinBar(
                progress = progress,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm).semantics { contentDescription = progressLabel },
            )
            Spacer(Modifier.height(Spacing.xl))
            state.skin?.let { skin ->
                MascotView(
                    skin = skin,
                    stage = if (granted || step.permission == null) MascotStage.PRISTINE else MascotStage.WILTED,
                    interactive = true,
                    modifier =
                        Modifier
                            .floatLoop()
                            .fillMaxWidth(MASCOT_WIDTH_FRACTION)
                            .aspectRatio(1f),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            AnimatedContent(
                targetState = step,
                transitionSpec = pageTransform(forward = forward, reduced = reduced),
                label = "onboardingStep",
                modifier = Modifier.fillMaxWidth(),
            ) { current ->
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(current.titleRes()),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(current.bodyRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = extras.onSurfaceMuted,
                    )
                    if (current == OnboardingStep.WELCOME) {
                        Spacer(Modifier.height(Spacing.xl))
                        Text(
                            text = stringResource(R.string.onboarding_language_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = extras.onSurfaceMuted,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        SegmentPills(
                            options = listOf(AppLanguage.ENGLISH, AppLanguage.HEBREW),
                            selected =
                                if (state.language ==
                                    AppLanguage.HEBREW
                                ) {
                                    AppLanguage.HEBREW
                                } else {
                                    AppLanguage.ENGLISH
                                },
                            onSelect = viewModel::setLanguage,
                            label = {
                                stringResource(
                                    if (it ==
                                        AppLanguage.HEBREW
                                    ) {
                                        R.string.language_hebrew
                                    } else {
                                        R.string.language_english
                                    },
                                )
                            },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = step.permission != null && granted,
                enter = popEnter(reduced),
                exit = popExit(reduced),
            ) {
                Pill(
                    text = stringResource(R.string.onboarding_granted),
                    color = extras.success,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
            if (step == OnboardingStep.DONE && state.permissions?.coreGranted == false) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.onboarding_limited_mode),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = extras.onSurfaceMuted,
                )
            }
        }
    }
}

private fun OnboardingStep.titleRes(): Int =
    when (this) {
        OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
        OnboardingStep.USAGE -> R.string.onboarding_usage_title
        OnboardingStep.OVERLAY -> R.string.onboarding_overlay_title
        OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
        OnboardingStep.EXACT_ALARM -> R.string.onboarding_alarm_title
        OnboardingStep.BATTERY -> R.string.onboarding_battery_title
        OnboardingStep.DONE -> R.string.onboarding_done_title
    }

private fun OnboardingStep.bodyRes(): Int =
    when (this) {
        OnboardingStep.WELCOME -> R.string.onboarding_welcome_body
        OnboardingStep.USAGE -> R.string.onboarding_usage_body
        OnboardingStep.OVERLAY -> R.string.onboarding_overlay_body
        OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_body
        OnboardingStep.EXACT_ALARM -> R.string.onboarding_alarm_body
        OnboardingStep.BATTERY -> R.string.onboarding_battery_body
        OnboardingStep.DONE -> R.string.onboarding_done_body
    }

internal fun OnboardingStep.settingsIntent(packageName: String): Intent =
    when (this) {
        OnboardingStep.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        OnboardingStep.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        OnboardingStep.EXACT_ALARM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            }
        OnboardingStep.BATTERY ->
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName"),
            )
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private const val MASCOT_WIDTH_FRACTION = 0.55f
