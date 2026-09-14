package il.rikavon.ui.home

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.AppUsage
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.permissions.PermissionState
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.blocker.service.ServiceStarter
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.MascotSoundPlayer
import il.rikavon.feature.mascot.ui.MascotEffect
import il.rikavon.feature.mascot.ui.MascotEffectTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackedAppRow(val limit: AppLimit, val label: String, val usage: AppUsage)

data class HomeUiState(
    val skin: MascotSkin? = null,
    val stage: MascotStage = MascotStage.PRISTINE,
    val tracked: List<TrackedAppRow> = emptyList(),
    val streak: Int = 0,
    val permissions: PermissionState? = null,
    val effect: MascotEffectTrigger? = null,
    val trackingEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    val mascotLine: String = "",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val selectedMascot: SelectedMascot,
    private val scoreProvider: FocusScoreProvider,
    private val usage: UsageRepository,
    limits: LimitsRepository,
    settings: SettingsRepository,
    private val installed: InstalledAppsSource,
    private val permissions: PermissionChecker,
    private val texts: MascotTexts,
    private val sounds: MascotSoundPlayer,
    private val starter: ServiceStarter,
) : ViewModel() {
    private val permissionState = MutableStateFlow(permissions.state())
    private val effect = MutableStateFlow<MascotEffectTrigger?>(null)
    private val mascotLine = MutableStateFlow("")
    private var lastStage: MascotStage? = null
    private var effectSerial = 0

    val state: StateFlow<HomeUiState> =
        combine(
            selectedMascot.skin,
            scoreProvider.stage,
            combine(limits.limits, usage.today) { list, snapshot ->
                list.map { TrackedAppRow(it, installed.label(it.packageName), snapshot.usageOf(it.packageName)) }
            },
            settings.settings,
            combine(permissionState, effect, mascotLine) { p, e, l -> Triple(p, e, l) },
        ) { skin, stage, tracked, prefs, extras ->
            val (perms, currentEffect, line) = extras
            HomeUiState(
                skin = skin,
                stage = stage,
                tracked = tracked.sortedByDescending { it.usage.minutes },
                streak = prefs.currentStreak,
                permissions = perms,
                effect = currentEffect,
                trackingEnabled = prefs.trackingEnabled,
                soundsEnabled = prefs.soundsEnabled,
                mascotLine = line,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    init {
        viewModelScope.launch {
            scoreProvider.stage.collect { stage ->
                val previous = lastStage
                lastStage = stage
                if (previous != null && previous != stage) {
                    val kind = if (stage.key > previous.key) MascotEffect.SCORE_UP else MascotEffect.SCORE_DOWN
                    effect.value = MascotEffectTrigger(kind, ++effectSerial)
                }
            }
        }
    }

    /** Called on every resume: refreshes usage, permissions and makes sure the service is up. */
    fun onResume() {
        viewModelScope.launch {
            permissionState.value = permissions.state()
            usage.refresh()
            starter.startIfConfigured()
        }
    }

    fun icon(packageName: String): Drawable? = installed.icon(packageName)

    fun tapText(language: String): String {
        val skin = state.value.skin ?: return ""
        return texts.stageText(skin, state.value.stage, language).also { mascotLine.value = it }
    }

    fun onLongPress() {
        val skin = state.value.skin ?: return
        if (state.value.soundsEnabled) skin.soundAsset?.let { sounds.play(it) }
    }

    fun onEffectSound(effect: MascotEffect) {
        if (!state.value.soundsEnabled) return
        sounds.play(
            if (effect ==
                MascotEffect.SCORE_UP
            ) {
                MascotSoundPlayer.SCORE_UP
            } else {
                MascotSoundPlayer.SCORE_DOWN
            },
        )
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
