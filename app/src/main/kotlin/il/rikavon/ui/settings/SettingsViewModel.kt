package il.rikavon.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.permissions.PermissionState
import il.rikavon.core.data.repo.BackupRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.feature.blocker.profile.FocusProfileManager
import il.rikavon.feature.blocker.profile.FocusProfileState
import il.rikavon.feature.blocker.service.ServiceStarter
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.notifications.DailySummaryScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupMessage {
    data object ExportDone : BackupMessage

    data object ImportDone : BackupMessage

    data object Failed : BackupMessage
}

data class SettingsUiState(
    val settings: Settings? = null,
    val skin: MascotSkin? = null,
    val permissions: PermissionState? = null,
    val tier: Tier = Tier.FREE,
    val strictCountdown: Int? = null,
    val backupMessage: BackupMessage? = null,
    val versionName: String = "",
    val profile: FocusProfileState = FocusProfileState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val permissions: PermissionChecker,
    private val starter: ServiceStarter,
    private val summaryScheduler: DailySummaryScheduler,
    private val focusProfile: FocusProfileManager,
    selectedMascot: SelectedMascot,
) : ViewModel() {
    private val permissionState = MutableStateFlow(permissions.state())
    private val profileState = MutableStateFlow(focusProfile.state())
    private val countdown = MutableStateFlow<Int?>(null)
    private val backupMessage = MutableStateFlow<BackupMessage?>(null)
    private var pendingAfterCountdown: (suspend () -> Unit)? = null
    private val versionName =
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        }.getOrDefault("")

    val state: StateFlow<SettingsUiState> =
        combine(
            settings.settings,
            selectedMascot.skin,
            combine(permissionState, profileState) { perms, profile -> perms to profile },
            countdown,
            backupMessage,
        ) { prefs, skin, (perms, profile), c, message ->
            SettingsUiState(prefs, skin, perms, Tier.of(prefs.premium), c, message, versionName, profile)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun refreshPermissions() {
        permissionState.value = permissions.state()
        profileState.value = focusProfile.state()
    }

    fun provisioningIntent(): Intent = focusProfile.provisioningIntent()

    fun openProfile() {
        focusProfile.openInsideProfile()
    }

    /** Turning strict mode off is itself delayed when strict mode is on. */
    fun setStrictMode(enabled: Boolean) {
        val current = state.value.settings ?: return
        if (enabled || !current.strictMode) {
            viewModelScope.launch { settings.setStrictMode(enabled) }
            return
        }
        startCountdown { settings.setStrictMode(false) }
    }

    fun setTracking(enabled: Boolean) {
        val current = state.value.settings ?: return
        if (enabled || !current.strictMode) {
            viewModelScope.launch {
                settings.setTrackingEnabled(enabled)
                starter.startIfConfigured()
            }
            return
        }
        startCountdown {
            settings.setTrackingEnabled(false)
            starter.startIfConfigured()
        }
    }

    fun cancelCountdown() {
        countdown.value = null
        pendingAfterCountdown = null
    }

    fun setDailySummary(enabled: Boolean, hour: Int) {
        viewModelScope.launch {
            settings.setDailySummary(enabled, hour)
            summaryScheduler.reschedule()
        }
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    fun setSounds(enabled: Boolean) = viewModelScope.launch { settings.setSoundsEnabled(enabled) }

    fun setVoice(enabled: Boolean) = viewModelScope.launch { settings.setVoiceEnabled(enabled) }

    fun setReduceMotion(mode: ReduceMotionMode) = viewModelScope.launch { settings.setReduceMotion(mode) }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch { settings.setLanguage(language) }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val result =
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { backup.export(it) } ?: error("no stream")
                }
            backupMessage.value = if (result.isSuccess) BackupMessage.ExportDone else BackupMessage.Failed
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val result =
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { backup.import(it).getOrThrow() }
                        ?: error("no stream")
                }
            backupMessage.value = if (result.isSuccess) BackupMessage.ImportDone else BackupMessage.Failed
            if (result.isSuccess) {
                starter.startIfConfigured()
                summaryScheduler.reschedule()
            }
        }
    }

    fun clearBackupMessage() {
        backupMessage.value = null
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
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val ONE_SECOND_MILLIS = 1_000L
    }
}
