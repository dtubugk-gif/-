package il.rikavon.feature.blocker.ui.score

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.domain.FocusScore
import il.rikavon.core.data.domain.FocusScoreCalculator
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SubtleDivider
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.core.ui.theme.scoreColor
import il.rikavon.core.ui.util.formatMinutes
import il.rikavon.feature.blocker.R
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ScoreUiState(val score: FocusScore = FocusScore.PERFECT, val hasLimits: Boolean = false)

@HiltViewModel
class ScoreExplainerViewModel @Inject constructor(provider: FocusScoreProvider, limits: LimitsRepository) :
    ViewModel() {
        val state: StateFlow<ScoreUiState> =
            combine(provider.score, limits.limits) { score, list ->
                ScoreUiState(score, list.any { it.enabled })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ScoreUiState())

        companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** Score explainer: today's breakdown in one card, then one card per component of the formula. */
@Composable
fun ScoreExplainerScreen(onBack: () -> Unit, viewModel: ScoreExplainerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = rememberPinnedTopBarBehavior()
    Scaffold(
        topBar = {
            RikavonTopBar(
                title = stringResource(R.string.score_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.score_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtraColors.current.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
            )
            SectionLabel(stringResource(R.string.score_section_today))
            TodayCard(state)
            Part(R.string.score_part_minutes, R.string.score_part_minutes_body)
            Part(R.string.score_part_opens, R.string.score_part_opens_body)
            Part(R.string.score_part_streak, R.string.score_part_streak_body)
            Part(R.string.score_stages_title, R.string.score_stages_body)
        }
    }
}

@Composable
private fun TodayCard(state: ScoreUiState) {
    SurfaceCard(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
        if (!state.hasLimits) {
            Text(
                stringResource(R.string.score_no_limits),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtraColors.current.onSurfaceMuted,
            )
            return@SurfaceCard
        }
        val s = state.score
        Column {
            PartLine(stringResource(R.string.score_today_minutes), s.minutesPart, FocusScoreCalculator.MINUTES_WEIGHT)
            PartLine(stringResource(R.string.score_today_opens), s.opensPart, FocusScoreCalculator.OPENS_WEIGHT)
            PartLine(stringResource(R.string.score_today_streak), s.streakPart, FocusScoreCalculator.STREAK_WEIGHT)
            Text(
                stringResource(R.string.score_streak_minutes, formatMinutes(s.longestCleanStreakMinutes)),
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.onSurfaceMuted,
            )
            Spacer(Modifier.height(Spacing.md))
            SubtleDivider(inset = 0.dp)
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.score_total_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    s.total.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = scoreColor(s.total),
                )
            }
        }
    }
}

@Composable
private fun PartLine(label: String, value: Int, weight: Int) {
    Row(modifier = Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.score_part_value, value, weight),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Part(title: Int, body: Int) {
    SectionLabel(stringResource(title), modifier = Modifier.padding(top = Spacing.sm))
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
}
