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
import il.rikavon.core.data.repo.ScheduleRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.time.TimeSource
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
import java.time.LocalDate
import javax.inject.Inject

data class TrackedAppRow(val limit: AppLimit, val label: String, val usage: AppUsage) {
    /** 0..1+ of the limit used; a fully blocked app is either 0 or over. */
    val ratio: Float
        get() =
            if (limit.fullBlock) {
                if (usage.minutes > 0) 1f else 0f
            } else {
                usage.minutes.toFloat() / limit.limitMinutes.coerceAtLeast(1)
            }

    val remainingMinutes: Int get() = if (limit.fullBlock) 0 else (limit.limitMinutes - usage.minutes).coerceAtLeast(0)
}

enum class Greeting { MORNING, NOON, EVENING, NIGHT }

/** The app closest to its limit, or the first one already over it. */
sealed interface NextLimit {
    data class Upcoming(val label: String, val minutesLeft: Int) : NextLimit

    data class Reached(val label: String) : NextLimit
}

data class HomeUiState(
    val skin: MascotSkin? = null,
    val stage: MascotStage = MascotStage.PRISTINE,
    val score: Int = 100,
    val tracked: List<TrackedAppRow> = emptyList(),
    val nextLimit: NextLimit? = null,
    val streak: Int = 0,
    val activeSchedules: Int = 0,
    val permissions: PermissionState? = null,
    val effect: MascotEffectTrigger? = null,
    val trackingEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    val mascotLine: String = "",
    val greeting: Greeting = Greeting.MORNING,
    val date: LocalDate = LocalDate.now(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val selectedMascot: SelectedMascot,
    private val scoreProvider: FocusScoreProvider,
    private val usage: UsageRepository,
    limits: LimitsRepository,
    schedules: ScheduleRepository,
    settings: SettingsRepository,
    private val installed: InstalledAppsSource,
    private val permissions: PermissionChecker,
    private val texts: MascotTexts,
    private val sounds: MascotSoundPlayer,
    private val starter: ServiceStarter,
    private val time: TimeSource,
) : ViewModel() {
    private val permissionState = MutableStateFlow(permissions.state())
    private val effect = MutableStateFlow<MascotEffectTrigger?>(null)
    private val mascotLine = MutableStateFlow("")
    private var lastStage: MascotStage? = null
    private var effectSerial = 0

    private val tracked =
        combine(limits.limits, usage.today) { list, snapshot ->
            list
                .map { TrackedAppRow(it, installed.label(it.packageName), snapshot.usageOf(it.packageName)) }
                .sortedByDescending { it.ratio }
        }

    private val extras = combine(permissionState, effect, mascotLine) { p, e, l -> Triple(p, e, l) }

    val state: StateFlow<HomeUiState> =
        combine(
            selectedMascot.skin,
            scoreProvider.score,
            tracked,
            settings.settings,
            extras,
        ) { skin, score, rows, prefs, ex ->
            val (perms, currentEffect, line) = ex
            HomeUiState(
                skin = skin,
                stage = MascotStage.fromScore(score.total),
                score = score.total,
                tracked = rows,
                nextLimit = nextLimit(rows),
                streak = prefs.currentStreak,
                permissions = perms,
                effect = currentEffect,
                trackingEnabled = prefs.trackingEnabled,
                soundsEnabled = prefs.soundsEnabled,
                mascotLine = line,
                greeting = greetingFor(time.localTime().hour),
                date = time.today(),
            )
        }.combine(schedules.schedules) { s, list ->
            val today = time.today().dayOfWeek
            s.copy(activeSchedules = list.count { it.enabled && today in it.days })
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

    /** A fresh line for the speech bubble when nothing was tapped yet. */
    fun idleLine(language: String): String {
        val skin = state.value.skin ?: return ""
        if (mascotLine.value.isBlank()) mascotLine.value = texts.stageText(skin, state.value.stage, language)
        return mascotLine.value
    }

    fun onLongPress() {
        val skin = state.value.skin ?: return
        if (state.value.soundsEnabled) skin.soundAsset?.let { sounds.play(it) }
    }

    fun onEffectSound(effect: MascotEffect) {
        if (!state.value.soundsEnabled) return
        sounds.play(if (effect == MascotEffect.SCORE_UP) MascotSoundPlayer.SCORE_UP else MascotSoundPlayer.SCORE_DOWN)
    }

    private fun nextLimit(rows: List<TrackedAppRow>): NextLimit? {
        val enabled = rows.filter { it.limit.enabled }
        enabled.firstOrNull { it.ratio >= 1f }?.let { return NextLimit.Reached(it.label) }
        val closest = enabled.filter { !it.limit.fullBlock }.minByOrNull { it.remainingMinutes } ?: return null
        return NextLimit.Upcoming(closest.label, closest.remainingMinutes)
    }

    private fun greetingFor(hour: Int): Greeting =
        when {
            hour < MORNING_START || hour >= NIGHT_START -> Greeting.NIGHT
            hour < NOON_START -> Greeting.MORNING
            hour < EVENING_START -> Greeting.NOON
            else -> Greeting.EVENING
        }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val MORNING_START = 5
        private const val NOON_START = 12
        private const val EVENING_START = 17
        private const val NIGHT_START = 22
    }
}
