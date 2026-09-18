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
import il.rikavon.core.data.repo.CloudKey
import il.rikavon.core.data.repo.CloudKeysRepository
import il.rikavon.core.data.repo.SettingsLockRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.feature.blocker.contact.CallDiagnosis
import il.rikavon.feature.blocker.contact.CallDoctor
import il.rikavon.feature.blocker.profile.FocusProfileManager
import il.rikavon.feature.blocker.profile.FocusProfileState
import il.rikavon.feature.blocker.profile.SystemAppCandidate
import il.rikavon.feature.blocker.service.ServiceStarter
import il.rikavon.feature.blocker.ui.common.PinGate
import il.rikavon.feature.blocker.ui.common.StrictCountdown
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.MascotVoice
import il.rikavon.feature.mascot.sound.Speaker
import il.rikavon.notifications.DailySummaryScheduler
import kotlinx.coroutines.Dispatchers
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
    /** Inside the focus profile: pre-installed apps that can still be switched on there. */
    val systemApps: List<SystemAppCandidate> = emptyList(),
    /** The AI brain has a key, so the pet's lines come from the model. */
    val aiConfigured: Boolean = false,
    /** The realistic voice has a key, so the pet speaks through OpenAI's speech model. */
    val voiceConfigured: Boolean = false,
    /** Which engine actually said the pet's last line; null until it said one this session. */
    val speaker: Speaker? = null,
    /** Why the pet is or is not calling; null until known. */
    val callDiagnosis: CallDiagnosis? = null,
)

private data class CloudState(
    val message: BackupMessage?,
    val ai: Boolean,
    val voice: Boolean,
    val speaker: Speaker?,
    val calls: CallDiagnosis,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val lockRepository: SettingsLockRepository,
    private val cloudKeys: CloudKeysRepository,
    private val backup: BackupRepository,
    private val permissions: PermissionChecker,
    private val starter: ServiceStarter,
    private val summaryScheduler: DailySummaryScheduler,
    private val focusProfile: FocusProfileManager,
    selectedMascot: SelectedMascot,
    voice: MascotVoice,
    private val callDoctor: CallDoctor,
) : ViewModel() {
    private val permissionState = MutableStateFlow(permissions.state())
    private val profileState = MutableStateFlow(focusProfile.state())
    private val systemApps = MutableStateFlow<List<SystemAppCandidate>>(emptyList())
    val countdown = StrictCountdown(viewModelScope)
    private val backupMessage = MutableStateFlow<BackupMessage?>(null)
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
            combine(permissionState, profileState, systemApps) { perms, profile, apps -> Triple(perms, profile, apps) },
            countdown.remaining,
            combine(
                backupMessage,
                cloudKeys.key(CloudKey.BRAIN),
                cloudKeys.key(CloudKey.VOICE),
                voice.speaker,
                callDoctor.diagnosis(permissionState),
            ) { message, brain, key, speaker, calls ->
                CloudState(message, brain != null, key != null, speaker, calls)
            },
        ) { prefs, skin, (perms, profile, apps), c, cloud ->
            SettingsUiState(
                prefs,
                skin,
                perms,
                Tier.of(prefs.premium),
                c,
                cloud.message,
                versionName,
                profile,
                apps,
                cloud.ai,
                cloud.voice,
                cloud.speaker,
                cloud.calls,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    /** The settings lock: switching enforcement off, and removing the lock itself, go through it. */
    val lock =
        PinGate(
            pinHash = { state.value.settings?.pinHash },
            onPinChosen = { hash -> viewModelScope.launch { lockRepository.setPinHash(hash) } },
        )

    init {
        refreshSystemApps()
    }

    fun refreshPermissions() {
        permissionState.value = permissions.state()
        profileState.value = focusProfile.state()
        refreshSystemApps()
    }

    fun provisioningIntent(): Intent = focusProfile.provisioningIntent()

    fun openProfile() {
        focusProfile.openInsideProfile()
    }

    /** Inside the focus profile: switches a pre-installed app on so it can be limited there. */
    fun enableSystemApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            focusProfile.enableSystemApp(packageName)
            systemApps.value = focusProfile.preinstalledCandidates()
        }
    }

    private fun refreshSystemApps() {
        viewModelScope.launch(Dispatchers.IO) { systemApps.value = focusProfile.preinstalledCandidates() }
    }

    /** Turning strict mode off goes through the lock, then is itself delayed when strict mode is on. */
    fun setStrictMode(enabled: Boolean) {
        if (enabled) {
            viewModelScope.launch { settings.setStrictMode(true) }
            return
        }
        loosen { settings.setStrictMode(false) }
    }

    fun setTracking(enabled: Boolean) {
        if (enabled) {
            viewModelScope.launch {
                settings.setTrackingEnabled(true)
                starter.startIfConfigured()
            }
            return
        }
        loosen {
            settings.setTrackingEnabled(false)
            starter.startIfConfigured()
        }
    }

    /** Switching something off: the PIN when settings are locked, then the strict-mode delay when that is on. */
    private fun loosen(action: suspend () -> Unit) {
        lock.require {
            if (state.value.settings?.strictMode ==
                true
            ) {
                countdown.start(action)
            } else {
                viewModelScope.launch { action() }
            }
        }
    }

    fun setReminder(minutes: Int) = viewModelScope.launch { settings.setReminderMinutes(minutes) }

    fun setDailySummary(enabled: Boolean, hour: Int) {
        viewModelScope.launch {
            settings.setDailySummary(enabled, hour)
            summaryScheduler.reschedule()
        }
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    /** Sounds and the pet's voice, either or both. */
    fun setAudio(sounds: Boolean? = null, voice: Boolean? = null) =
        viewModelScope.launch { settings.setAudio(soundsEnabled = sounds, voiceEnabled = voice) }

    fun setPetMessages(enabled: Boolean) = viewModelScope.launch { settings.setPetMessagesEnabled(enabled) }

    fun setPetCalls(enabled: Boolean) {
        if (enabled) {
            viewModelScope.launch {
                settings.setPetCallsEnabled(true)
            }
        } else {
            offBehindLock { settings.setPetCallsEnabled(false) }
        }
    }

    /** Rings a test call now, through the same path a real one takes. */
    fun testCall() = viewModelScope.launch { callDoctor.testCall() }

    fun setBreathingGate(enabled: Boolean) {
        if (enabled) {
            viewModelScope.launch {
                settings.setBreathingGateEnabled(true)
            }
        } else {
            offBehindLock { settings.setBreathingGateEnabled(false) }
        }
    }

    private fun offBehindLock(action: suspend () -> Unit) = lock.require { viewModelScope.launch { action() } }

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

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
