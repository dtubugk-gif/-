package il.rikavon.feature.mascot.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.repo.AchievementRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotRegistry
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.MascotUnlocks
import il.rikavon.feature.mascot.sound.MascotSoundPlayer
import il.rikavon.feature.mascot.sound.MascotVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryItem(
    val skin: MascotSkin,
    val unlocked: Boolean,
    val selected: Boolean,
    val requiredAchievement: AchievementId?,
)

data class GalleryUiState(
    val items: List<GalleryItem> = emptyList(),
    val selected: GalleryItem? = null,
    val currentStage: MascotStage = MascotStage.PRISTINE,
    /** Stage shown in the hero card; null follows the live stage. */
    val previewStage: MascotStage? = null,
    val tier: Tier = Tier.FREE,
    val validationErrors: List<String> = emptyList(),
    val loaded: Boolean = false,
    val notice: AchievementId? = null,
) {
    val heroStage: MascotStage get() = previewStage ?: currentStage
}

@HiltViewModel
class MascotGalleryViewModel @Inject constructor(
    private val registry: MascotRegistry,
    private val settings: SettingsRepository,
    achievements: AchievementRepository,
    private val sounds: MascotSoundPlayer,
    private val voice: MascotVoice,
    private val texts: MascotTexts,
    currentStageSource: CurrentStageSource,
) : ViewModel() {
    private val previewStage = MutableStateFlow<MascotStage?>(null)
    private val notice = MutableStateFlow<AchievementId?>(null)

    private val latestSettings: StateFlow<Settings?> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val state: StateFlow<GalleryUiState> =
        combine(
            registry.skins.onStart { registry.load() },
            settings.settings,
            achievements.unlockedIds,
            currentStageSource.stage,
            combine(registry.errors, previewStage, notice) { e, p, n -> Triple(e, p, n) },
        ) { skins, prefs, unlocked, stage, extras ->
            val (errors, preview, currentNotice) = extras
            val tier = Tier.of(prefs.premium)
            val items =
                skins.map { skin ->
                    GalleryItem(
                        skin = skin,
                        unlocked = MascotUnlocks.isUnlocked(skin, tier, unlocked),
                        selected = skin.id == prefs.selectedMascotId,
                        requiredAchievement = MascotUnlocks.requiredAchievement(skin),
                    )
                }
            GalleryUiState(
                items = items,
                selected = items.firstOrNull { it.selected } ?: items.firstOrNull(),
                currentStage = stage,
                previewStage = preview,
                tier = tier,
                validationErrors = errors,
                loaded = true,
                notice = currentNotice,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GalleryUiState())

    /** Selects an unlocked mascot; a locked one just surfaces its requirement. */
    fun choose(item: GalleryItem) {
        if (!item.unlocked) {
            notice.value = item.requiredAchievement
            return
        }
        viewModelScope.launch {
            settings.setMascot(item.skin.id)
            previewStage.value = null
            item.skin.soundAsset?.let { sounds.play(it) }
        }
    }

    fun preview(stage: MascotStage?) {
        previewStage.value = stage
    }

    fun quote(skin: MascotSkin, stage: MascotStage, language: String): String = texts.stageText(skin, stage, language)

    /** A tapped hero says a fresh line out loud (the gallery is the place to hear the voices). */
    fun say(skin: MascotSkin, stage: MascotStage, language: String): String =
        quote(skin, stage, language).also { line ->
            val prefs = latestSettings.value
            if (prefs == null || (prefs.soundsEnabled && prefs.voiceEnabled)) voice.speak(line, skin.voice, language)
        }

    fun playReaction(skin: MascotSkin) {
        skin.soundAsset?.let { sounds.play(it) }
    }

    fun clearNotice() {
        notice.value = null
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
