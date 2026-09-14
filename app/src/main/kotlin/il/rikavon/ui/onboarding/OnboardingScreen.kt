package il.rikavon.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.R
import il.rikavon.core.data.permissions.AppPermission
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.permissions.PermissionState
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.feature.blocker.service.ServiceStarter
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.ui.MascotView
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
        combine(step, permissionState, selectedMascot.skin) { s, p, skin ->
            OnboardingUiState(s, p, skin, steps)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            OnboardingUiState(steps = steps),
        )

    fun refreshPermissions() {
        permissionState.value = permissions.state()
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { (index + 1).toFloat() / state.steps.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            state.skin?.let { skin ->
                MascotView(
                    skin = skin,
                    stage = if (granted || step.permission == null) MascotStage.PRISTINE else MascotStage.WILTED,
                    interactive = true,
                    modifier =
                        Modifier
                            .fillMaxWidth(MASCOT_WIDTH_FRACTION)
                            .aspectRatio(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(step.titleRes()),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(step.bodyRes()),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            if (step.permission != null) {
                if (granted) {
                    Text(
                        text = stringResource(R.string.onboarding_granted),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Button(
                        onClick = {
                            when (step) {
                                OnboardingStep.NOTIFICATIONS ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                else -> context.startActivity(step.settingsIntent(context.packageName))
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = TouchTarget + 8.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_grant))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = viewModel::back,
                    enabled = index > 0,
                    modifier = Modifier.heightIn(min = TouchTarget),
                ) {
                    Text(stringResource(R.string.onboarding_back))
                }
                if (step == OnboardingStep.DONE) {
                    Button(onClick = { viewModel.finish(onDone) }, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                } else {
                    TextButton(onClick = viewModel::next, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Text(
                            stringResource(
                                if (step.permission != null &&
                                    !granted
                                ) {
                                    R.string.onboarding_skip
                                } else {
                                    R.string.onboarding_next
                                },
                            ),
                        )
                    }
                }
            }
            if (step == OnboardingStep.DONE && state.permissions?.coreGranted == false) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_limited_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
