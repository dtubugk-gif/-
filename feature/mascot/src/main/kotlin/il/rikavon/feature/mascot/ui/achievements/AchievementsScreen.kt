package il.rikavon.feature.mascot.ui.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.model.Achievement
import il.rikavon.core.data.repo.AchievementRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.anim.enterFromBelow
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.ListRow
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.StatTile
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing
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

/** Achievements: two streak tiles, then one row per achievement with a filled or locked badge. */
@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = rememberPinnedTopBarBehavior()
    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.achievements_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Spacing.xl,
                ),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    StatTile(
                        value = stringResource(R.string.achievements_streak_days, state.currentStreak),
                        caption = stringResource(R.string.achievements_streak_current),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = stringResource(R.string.achievements_streak_days, state.bestStreak),
                        caption = stringResource(R.string.achievements_streak_best),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.achievements.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.achievements_empty_title),
                        body = stringResource(R.string.achievements_empty_body),
                        icon = Icons.Filled.Star,
                    )
                }
            } else {
                item {
                    SectionLabel(
                        stringResource(R.string.achievements_section),
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                itemsIndexed(state.achievements, key = { _, item -> item.id.key }) { index, achievement ->
                    AchievementRow(achievement, modifier = Modifier.enterFromBelow(index))
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, modifier: Modifier = Modifier) {
    val title = stringResource(MascotStrings.achievementTitle(achievement.id))
    val description = stringResource(MascotStrings.achievementDescription(achievement.id))
    val stateLabel =
        stringResource(if (achievement.unlocked) R.string.achievements_unlocked else R.string.achievements_locked)
    val scheme = MaterialTheme.colorScheme
    val muted = LocalExtraColors.current.onSurfaceMuted
    ListRow(
        title = title,
        subtitle = description,
        chevron = false,
        modifier = modifier.semantics { contentDescription = "$title. $description. $stateLabel" },
        leading = {
            Box(
                modifier =
                    Modifier
                        .size(Sizes.touch)
                        .background(
                            if (achievement.unlocked) scheme.primaryContainer else scheme.surfaceContainerHigh,
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (achievement.unlocked) Icons.Filled.Check else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (achievement.unlocked) scheme.onPrimaryContainer else muted,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        },
    )
}
