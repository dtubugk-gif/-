package il.rikavon.feature.blocker.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
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
import il.rikavon.core.ui.components.SectionHeader
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

@Composable
fun ScoreExplainerScreen(onBack: () -> Unit, viewModel: ScoreExplainerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.score_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ScreenPadding),
        ) {
            Text(
                text = stringResource(R.string.score_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(ScreenPadding)
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                        .padding(18.dp),
            ) {
                Text(stringResource(R.string.score_today_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (!state.hasLimits) {
                    Text(stringResource(R.string.score_no_limits), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val s = state.score
                    Text(
                        stringResource(
                            R.string.score_today_line,
                            stringResource(R.string.score_today_minutes),
                            s.minutesPart,
                            FocusScoreCalculator.MINUTES_WEIGHT,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(
                            R.string.score_today_line,
                            stringResource(R.string.score_today_opens),
                            s.opensPart,
                            FocusScoreCalculator.OPENS_WEIGHT,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(
                            R.string.score_today_line,
                            stringResource(R.string.score_today_streak),
                            s.streakPart,
                            FocusScoreCalculator.STREAK_WEIGHT,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.score_streak_minutes, formatMinutes(s.longestCleanStreakMinutes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.score_today_total, s.total),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Part(R.string.score_part_minutes, R.string.score_part_minutes_body)
            Part(R.string.score_part_opens, R.string.score_part_opens_body)
            Part(R.string.score_part_streak, R.string.score_part_streak_body)
            Part(R.string.score_stages_title, R.string.score_stages_body)
        }
    }
}

@Composable
private fun Part(title: Int, body: Int) {
    SectionHeader(stringResource(title))
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
}
