package il.rikavon.feature.mascot.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.model.Achievement
import il.rikavon.core.data.repo.AchievementRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.feature.mascot.R
import il.rikavon.feature.mascot.ui.MascotStrings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AchievementsUiState(
    val achievements: List<Achievement> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(achievements: AchievementRepository, settings: SettingsRepository) :
    ViewModel() {
        val state: StateFlow<AchievementsUiState> =
            combine(achievements.achievements, settings.settings) { list, prefs ->
                AchievementsUiState(list, prefs.currentStreak, prefs.bestStreak)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AchievementsUiState())

        companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.achievements_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + ScreenPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StreakTile(
                        label = stringResource(R.string.achievements_streak_current),
                        value = state.currentStreak,
                        modifier = Modifier.weight(1f),
                    )
                    StreakTile(
                        label = stringResource(R.string.achievements_streak_best),
                        value = state.bestStreak,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            items(state.achievements, key = { it.id.key }) { achievement ->
                AchievementRow(achievement)
            }
        }
    }
}

@Composable
private fun StreakTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.achievements_streak_days, value)
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(16.dp)
                .semantics { contentDescription = "$label: $description" },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(description, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    val title = stringResource(MascotStrings.achievementTitle(achievement.id))
    val description = stringResource(MascotStrings.achievementDescription(achievement.id))
    val stateLabel =
        stringResource(
            if (achievement.unlocked) R.string.achievements_unlocked else R.string.achievements_locked,
        )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .padding(14.dp)
                .semantics { contentDescription = "$title. $description. $stateLabel" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val scheme = MaterialTheme.colorScheme
        val badgeColor = if (achievement.unlocked) scheme.primary else scheme.surfaceContainerHighest
        val iconTint = if (achievement.unlocked) scheme.onPrimary else scheme.onSurfaceVariant
        Box(
            modifier =
                Modifier
                    .size(TouchTarget)
                    .background(badgeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (achievement.unlocked) Icons.Filled.Check else Icons.Filled.Lock,
                contentDescription = null,
                tint = iconTint,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
