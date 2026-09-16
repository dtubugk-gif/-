package il.rikavon.feature.blocker.ui.sites

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.model.FeedFeature
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.repo.BlockedSitesRepository
import il.rikavon.core.data.repo.FeedBlock
import il.rikavon.core.data.repo.FeedBlockRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.core.ui.components.ChoiceOption
import il.rikavon.core.ui.components.ChoiceSheet
import il.rikavon.core.ui.components.GroupCard
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SettingNavRow
import il.rikavon.core.ui.components.SquareIconButton
import il.rikavon.core.ui.components.SurfaceCard
import il.rikavon.core.ui.components.TonalButton
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.feature.blocker.R
import il.rikavon.feature.blocker.engine.FeedRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** How long a feed stays blocked; null minutes means until switched back on. */
enum class FeedDuration(val minutes: Int?) {
    HOUR(60),
    THREE_HOURS(180),
    TOMORROW(null),
    FOREVER(null),
    OFF(null),
}

data class SitesUiState(
    val sites: List<String> = emptyList(),
    val feeds: Map<FeedFeature, FeedBlock> = emptyMap(),
    val accessibilityOn: Boolean = true,
    val invalidInput: Boolean = false,
)

@HiltViewModel
class BlockedSitesViewModel @Inject constructor(
    private val sites: BlockedSitesRepository,
    private val feeds: FeedBlockRepository,
    private val permissions: PermissionChecker,
    private val time: TimeSource,
) : ViewModel() {
    private val accessibility = MutableStateFlow(permissions.hasAccessibility())
    private val invalid = MutableStateFlow(false)

    val state: StateFlow<SitesUiState> =
        combine(sites.sites, feeds.blocks, accessibility, invalid) { s, f, a, i ->
            SitesUiState(sites = s.sorted(), feeds = f, accessibilityOn = a, invalidInput = i)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SitesUiState())

    fun refresh() {
        accessibility.value = permissions.hasAccessibility()
    }

    fun add(input: String) {
        viewModelScope.launch { invalid.value = sites.add(input) == null }
    }

    fun clearInvalid() {
        invalid.value = false
    }

    fun remove(domain: String) = viewModelScope.launch { sites.remove(domain) }

    fun setFeed(feature: FeedFeature, duration: FeedDuration) {
        viewModelScope.launch {
            val now = time.now()
            when (duration) {
                FeedDuration.OFF -> feeds.unblock(feature)
                FeedDuration.FOREVER -> feeds.block(feature, FeedBlock.FOREVER)
                FeedDuration.TOMORROW ->
                    feeds.block(
                        feature,
                        now
                            .toLocalDate()
                            .plusDays(1)
                            .atStartOfDay(now.zone)
                            .toInstant()
                            .toEpochMilli(),
                    )
                else -> feeds.block(feature, time.nowMillis() + checkNotNull(duration.minutes) * MILLIS_PER_MINUTE)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** Websites to keep out of the browser, and the short-video feeds to keep out of their apps. */
@Composable
fun BlockedSitesScreen(onBack: () -> Unit, viewModel: BlockedSitesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var draft by rememberSaveable { mutableStateOf("") }
    var choosing by rememberSaveable { mutableStateOf<FeedFeature?>(null) }
    val submit = {
        viewModel.add(draft)
        draft = ""
    }
    Scaffold(topBar = { RikavonTopBar(title = stringResource(R.string.sites_title), onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(bottom = Spacing.xl),
        ) {
            if (!state.accessibilityOn) {
                item {
                    SurfaceCard(modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(
                                stringResource(R.string.sites_needs_accessibility),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TonalButton(
                                text = stringResource(R.string.sites_open_accessibility),
                                onClick = {
                                    viewModel.refresh()
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_ACCESSIBILITY_SETTINGS,
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.sites_section_feeds),
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                GroupCard {
                    FeedFeature.entries.forEach { feature ->
                        SettingNavRow(
                            title = stringResource(FeedRules.labelRes(feature)),
                            subtitle = feedStatus(state.feeds[feature]),
                            onClick = { choosing = feature },
                        )
                    }
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.sites_section_websites),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            viewModel.clearInvalid()
                        },
                        placeholder = { Text(stringResource(R.string.sites_add_hint)) },
                        isError = state.invalidInput,
                        supportingText =
                            if (state.invalidInput) {
                                (
                                    {
                                        Text(
                                            stringResource(R.string.sites_invalid),
                                        )
                                    }
                                )
                            } else {
                                null
                            },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    )
                    TonalButton(
                        text = stringResource(R.string.sites_add),
                        onClick = submit,
                        enabled = draft.isNotBlank(),
                    )
                }
            }
            if (state.sites.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalExtraColors.current.onSurfaceMuted,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                    )
                }
            }
            items(state.sites, key = { it }) { domain ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = domain, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SquareIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.sites_remove, domain),
                        onClick = { viewModel.remove(domain) },
                    )
                }
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }
    choosing?.let { feature ->
        val name = stringResource(FeedRules.labelRes(feature))
        ChoiceSheet(
            title = stringResource(R.string.sites_duration_title, name),
            options =
                listOf(
                    ChoiceOption(FeedDuration.HOUR, stringResource(R.string.sites_duration_1h)),
                    ChoiceOption(FeedDuration.THREE_HOURS, stringResource(R.string.sites_duration_3h)),
                    ChoiceOption(FeedDuration.TOMORROW, stringResource(R.string.sites_duration_tomorrow)),
                    ChoiceOption(FeedDuration.FOREVER, stringResource(R.string.sites_duration_forever)),
                    ChoiceOption(FeedDuration.OFF, stringResource(R.string.sites_duration_off)),
                ),
            selected = if (state.feeds[feature] == null) FeedDuration.OFF else FeedDuration.FOREVER,
            onSelect = { duration ->
                viewModel.setFeed(feature, duration)
                choosing = null
            },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
private fun feedStatus(block: FeedBlock?): String =
    when {
        block == null || block.untilMillis <= System.currentTimeMillis() -> stringResource(R.string.sites_feed_off)
        block.forever -> stringResource(R.string.sites_feed_forever)
        else -> stringResource(R.string.sites_feed_until, untilLabel(block.untilMillis))
    }

/** "18:30" today, "17 Sep 09:00" otherwise. */
fun untilLabel(untilMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(untilMillis).atZone(zone)
    val pattern = if (at.toLocalDate() == LocalDate.now(zone)) "HH:mm" else "d MMM HH:mm"
    return at.format(DateTimeFormatter.ofPattern(pattern))
}
