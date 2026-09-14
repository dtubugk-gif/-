package il.rikavon.feature.mascot.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.repo.AchievementRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotRegistry
import il.rikavon.feature.mascot.registry.MascotUnlocks
import il.rikavon.feature.mascot.sound.MascotSoundPlayer
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
    val currentStage: MascotStage = MascotStage.PRISTINE,
    val tier: Tier = Tier.FREE,
    val validationErrors: List<String> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class MascotGalleryViewModel @Inject constructor(
    private val registry: MascotRegistry,
    private val settings: SettingsRepository,
    achievements: AchievementRepository,
    private val sounds: MascotSoundPlayer,
    currentStageSource: CurrentStageSource,
) : ViewModel() {
    val state: StateFlow<GalleryUiState> =
        combine(
            registry.skins.onStart { registry.load() },
            settings.settings,
            achievements.unlockedIds,
            currentStageSource.stage,
            registry.errors,
        ) { skins, prefs, unlocked, stage, errors ->
            val tier = Tier.of(prefs.premium)
            GalleryUiState(
                items =
                    skins.map { skin ->
                        GalleryItem(
                            skin = skin,
                            unlocked = MascotUnlocks.isUnlocked(skin, tier, unlocked),
                            selected = skin.id == prefs.selectedMascotId,
                            requiredAchievement = MascotUnlocks.requiredAchievement(skin),
                        )
                    },
                currentStage = stage,
                tier = tier,
                validationErrors = errors,
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GalleryUiState())

    fun select(skin: MascotSkin) {
        viewModelScope.launch {
            settings.setMascot(skin.id)
            skin.soundAsset?.let { sounds.play(it) }
        }
    }

    fun playReaction(skin: MascotSkin) {
        skin.soundAsset?.let { sounds.play(it) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
