package il.rikavon.feature.mascot.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.Pill
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.feature.mascot.R
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.ui.MASCOT_SHARED_KEY
import il.rikavon.feature.mascot.ui.MascotStrings
import il.rikavon.feature.mascot.ui.MascotView
import il.rikavon.feature.mascot.ui.UiLanguage

@Composable
fun MascotGalleryScreen(
    onBack: () -> Unit,
    viewModel: MascotGalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<GalleryItem?>(null) }

    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.gallery_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loaded && state.items.isEmpty()) {
            EmptyState(text = stringResource(R.string.gallery_empty), modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding =
                PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + ScreenPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.tier.allMascotsUnlocked.not()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.gallery_premium_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            items(state.items, key = { it.skin.id }) { item ->
                GalleryCard(
                    item = item,
                    stage = state.currentStage,
                    onClick = { preview = item },
                )
            }
            if (state.validationErrors.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = stringResource(R.string.gallery_errors_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.validationErrors.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    preview?.let { item ->
        PreviewSheet(
            item = item,
            initialStage = state.currentStage,
            onSelect = {
                viewModel.select(item.skin)
                preview = null
            },
            onReaction = { viewModel.playReaction(item.skin) },
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun GalleryCard(item: GalleryItem, stage: MascotStage, onClick: () -> Unit) {
    val language = UiLanguage.current()
    val name = item.skin.name.resolve(language) ?: item.skin.id
    val accent = Color(item.skin.themeColorArgb)
    val requirement = item.requiredAchievement?.let { stringResource(MascotStrings.achievementTitle(it)) }
    val lockedText = requirement?.let { stringResource(R.string.gallery_locked_by, it) }
    val personalityRes = MascotStrings.personality(item.skin.personality)
    val personality = personalityRes?.let { stringResource(it) } ?: item.skin.personality
    val description =
        if (item.unlocked) {
            stringResource(R.string.gallery_card_description, name, personality)
        } else {
            stringResource(R.string.gallery_locked_description, name, lockedText.orEmpty())
        }
    val borderColor = if (item.selected) accent else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .border(if (item.selected) 3.dp else 1.dp, borderColor, MaterialTheme.shapes.large)
                .clickable(onClick = onClick, role = Role.Button)
                .semantics { contentDescription = description }
                .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            MascotView(
                skin = item.skin,
                stage = if (item.unlocked) stage else MascotStage.WORN,
                interactive = false,
                sharedKey = if (item.selected) MASCOT_SHARED_KEY else null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            )
            if (!item.unlocked) {
                Box(
                    modifier =
                        Modifier
                            .size(TouchTarget)
                            .background(
                                MaterialTheme.colorScheme.background.copy(alpha = LOCK_BADGE_ALPHA),
                                MaterialTheme.shapes.medium,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = personality,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        when {
            item.selected -> Pill(stringResource(R.string.gallery_selected), accent)
            !item.unlocked && lockedText != null ->
                Text(
                    text = lockedText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
        }
    }
}

@Composable
private fun PreviewSheet(
    item: GalleryItem,
    initialStage: MascotStage,
    onSelect: () -> Unit,
    onReaction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val language = UiLanguage.current()
    val name = item.skin.name.resolve(language) ?: item.skin.id
    var stageIndex by remember { mutableStateOf(MascotStage.entries.indexOf(initialStage).toFloat()) }
    val stage = MascotStage.entries[stageIndex.toInt().coerceIn(0, MascotStage.entries.lastIndex)]
    val sliderLabel = stringResource(R.string.gallery_stage_slider)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding)
                    .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.gallery_preview_title, name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            MascotView(
                skin = item.skin,
                stage = stage,
                interactive = true,
                textForTap = {
                    item.skin
                        .stage(stage)
                        .texts
                        .resolve(language)
                        ?.randomOrNull()
                        .orEmpty()
                },
                onLongPress = onReaction,
                modifier =
                    Modifier
                        .fillMaxWidth(PREVIEW_WIDTH_FRACTION)
                        .aspectRatio(1f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.gallery_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = stageIndex,
                onValueChange = { stageIndex = it },
                valueRange = 0f..MascotStage.entries.lastIndex.toFloat(),
                steps = MascotStage.entries.size - 2,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget)
                        .semantics { contentDescription = sliderLabel },
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (item.unlocked) {
                    Button(onClick = onSelect, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Text(
                            if (item.selected) {
                                stringResource(
                                    R.string.gallery_selected,
                                )
                            } else {
                                stringResource(R.string.gallery_select)
                            },
                        )
                    }
                } else {
                    val requirement =
                        item.requiredAchievement
                            ?.let {
                                stringResource(MascotStrings.achievementTitle(it))
                            }.orEmpty()
                    Text(
                        text = stringResource(R.string.gallery_locked_by, requirement),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val LOCK_BADGE_ALPHA = 0.7f
private const val PREVIEW_WIDTH_FRACTION = 0.7f

/** Exposed for hosts that show a single mascot card outside the gallery. */
@Suppress("unused")
@Composable
fun MascotCardPreview(skin: MascotSkin, stage: MascotStage, modifier: Modifier = Modifier) {
    MascotView(skin = skin, stage = stage, interactive = false, modifier = modifier)
}
