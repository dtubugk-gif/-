package il.rikavon.feature.mascot.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.core.ui.anim.FadeThrough
import il.rikavon.core.ui.anim.enterFromBelow
import il.rikavon.core.ui.components.EmptyState
import il.rikavon.core.ui.components.LockBadge
import il.rikavon.core.ui.components.PrimaryButton
import il.rikavon.core.ui.components.RikavonLargeTopBar
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SkeletonBlock
import il.rikavon.core.ui.components.pressScale
import il.rikavon.core.ui.components.rememberLargeTopBarBehavior
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Radius
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.feature.mascot.R
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.ui.MASCOT_SHARED_KEY
import il.rikavon.feature.mascot.ui.MascotStrings
import il.rikavon.feature.mascot.ui.MascotView
import il.rikavon.feature.mascot.ui.UiLanguage

/**
 * Gallery: large collapsing title, hero card for the selected pet (with the rot preview slider), then a
 * 3-column grid; locked cards carry a badge. Skeleton while the registry loads, empty state if no folder parsed.
 */
@Composable
fun MascotGalleryScreen(
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    viewModel: MascotGalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = UiLanguage.current()
    val snackbar = remember { SnackbarHostState() }
    val lockedFormat = stringResource(R.string.gallery_locked_by)
    val noticeText = state.notice?.let { stringResource(MascotStrings.achievementTitle(it)) }
    LaunchedEffect(noticeText) {
        if (noticeText != null) {
            snackbar.showSnackbar(lockedFormat.format(noticeText))
            viewModel.clearNotice()
        }
    }
    val scrollBehavior = if (onBack == null) rememberLargeTopBarBehavior() else rememberPinnedTopBarBehavior()

    Scaffold(
        topBar = {
            if (onBack == null) {
                RikavonLargeTopBar(
                    title = stringResource(R.string.gallery_title_design),
                    scrollBehavior = scrollBehavior,
                )
            } else {
                RikavonTopBar(
                    title = stringResource(R.string.gallery_title_design),
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loaded && state.items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.gallery_empty_title),
                body = stringResource(R.string.gallery_empty_body),
                icon = Icons.Filled.Face,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Spacing.xl,
                ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.gallery_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalExtraColors.current.onSurfaceMuted,
                )
            }
            if (!state.loaded) {
                item(span = { GridItemSpan(maxLineSpan) }) { SkeletonBlock(height = HERO_SKELETON, radius = Radius.xl) }
                items(SKELETON_CARDS) { SkeletonBlock(height = CARD_SKELETON) }
                return@LazyVerticalGrid
            }
            state.selected?.let { hero ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FadeThrough(targetState = hero.skin.id, label = "hero") {
                        HeroCard(
                            item = hero,
                            stage = state.heroStage,
                            quote = { viewModel.quote(hero.skin, state.heroStage, language) },
                            onSelect = { viewModel.choose(hero) },
                            onReaction = { viewModel.playReaction(hero.skin) },
                            onPreview = viewModel::preview,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.gallery_section_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalExtraColors.current.onSurfaceMuted,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            itemsIndexed(state.items, key = { _, item -> item.skin.id }) { index, item ->
                SmallCard(item = item, onClick = { viewModel.choose(item) }, modifier = Modifier.enterFromBelow(index))
            }
            if (state.tier.allMascotsUnlocked.not()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.gallery_premium_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalExtraColors.current.onSurfaceFaint,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
            if (state.validationErrors.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = Spacing.md)) {
                        Text(
                            text = stringResource(R.string.gallery_errors_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.validationErrors.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalExtraColors.current.onSurfaceMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    item: GalleryItem,
    stage: MascotStage,
    quote: () -> String,
    onSelect: () -> Unit,
    onReaction: () -> Unit,
    onPreview: (MascotStage?) -> Unit,
) {
    val language = UiLanguage.current()
    val name = item.skin.name.resolve(language) ?: item.skin.id
    val line = remember(item.skin.id, stage) { quote() }
    val sliderLabel = stringResource(R.string.gallery_stage_slider)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radius.xl))
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MascotView(
            skin = item.skin,
            stage = stage,
            interactive = true,
            textForTap = quote,
            onLongPress = onReaction,
            sharedKey = if (item.selected) MASCOT_SHARED_KEY else null,
            modifier = Modifier.size(HERO_MASCOT).aspectRatio(1f),
        )
        Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Spacing.sm))
        Text(
            text = "“$line”",
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.onSurfaceMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Spacer(Modifier.height(Spacing.lg))
        if (item.unlocked) {
            PrimaryButton(
                text = stringResource(if (item.selected) R.string.gallery_selected_now else R.string.gallery_select),
                onClick = onSelect,
                leading =
                    if (item.selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(CHECK_ICON)) }
                    } else {
                        null
                    },
            )
        } else {
            val requirement =
                item.requiredAchievement
                    ?.let {
                        stringResource(
                            MascotStrings.achievementTitle(it),
                        )
                    }.orEmpty()
            Text(
                text = stringResource(R.string.gallery_locked_by, requirement),
                style = MaterialTheme.typography.titleSmall,
                color = LocalExtraColors.current.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.gallery_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.onSurfaceFaint,
        )
        val index = MascotStage.entries.indexOf(stage).toFloat()
        Slider(
            value = index,
            onValueChange = { onPreview(MascotStage.entries[it.toInt().coerceIn(0, MascotStage.entries.lastIndex)]) },
            onValueChangeFinished = { },
            valueRange = 0f..MascotStage.entries.lastIndex.toFloat(),
            steps = MascotStage.entries.size - 2,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touch)
                    .semantics { contentDescription = sliderLabel },
        )
    }
}

@Composable
private fun SmallCard(item: GalleryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val language = UiLanguage.current()
    val name = item.skin.name.resolve(language) ?: item.skin.id
    val requirement = item.requiredAchievement?.let { stringResource(MascotStrings.achievementTitle(it)) }
    val description =
        if (item.unlocked) name else stringResource(R.string.gallery_locked_description, name, requirement.orEmpty())
    val scheme = MaterialTheme.colorScheme
    val background = if (item.unlocked) scheme.surfaceContainer else scheme.surfaceContainerLow
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(interaction)
                .background(background, RoundedCornerShape(Radius.lg))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick, role = Role.Button)
                .semantics { contentDescription = description }
                .padding(
                    top = if (item.unlocked) Spacing.md else Spacing.xl,
                    bottom = Spacing.md,
                    start = Spacing.sm,
                    end = Spacing.sm,
                ),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MascotView(
                skin = item.skin,
                stage = MascotStage.PRISTINE,
                interactive = false,
                modifier =
                    Modifier
                        .size(SMALL_MASCOT)
                        .alpha(if (item.unlocked) 1f else LOCKED_ALPHA),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = if (item.unlocked) scheme.onSurface else LocalExtraColors.current.onSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.unlocked && requirement != null) {
            LockBadge(text = requirement, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs))
        }
    }
}

private const val GRID_COLUMNS = 3
private const val SKELETON_CARDS = 6
private val HERO_MASCOT = Sizes.touch * 3
private val SMALL_MASCOT = Sizes.touch + Spacing.sm
private val HERO_SKELETON = Sizes.touch * 6
private val CARD_SKELETON = Sizes.touch * 2 + Spacing.lg
private val CHECK_ICON = Spacing.lg
private const val LOCKED_ALPHA = 0.3f
