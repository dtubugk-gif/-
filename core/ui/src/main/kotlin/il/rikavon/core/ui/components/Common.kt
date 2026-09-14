package il.rikavon.core.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import il.rikavon.core.ui.R
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.LocalReducedMotion
import il.rikavon.core.ui.theme.ColorMath
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Radius
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing

/** Minimum touch target everywhere. */
val TouchTarget: Dp = Sizes.touch

/** Side margin of every screen. */
val ScreenPadding: Dp = Spacing.screen

// ---- Top bars ----------------------------------------------------------------------------------

/** Small top bar for pushed screens: back arrow (mirrors in RTL), title, up to two actions. */
@Composable
fun RikavonTopBar(
    title: String,
    onBack: (() -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = { if (onBack != null) BackButton(onBack) },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = topBarColors(),
    )
}

/**
 * Large title for top-level screens: 32sp at rest, collapsing to a 22sp bar on scroll. Pass the same
 * [scrollBehavior] to the scrolling content via `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
 */
@Composable
fun RikavonLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) BackButton(onBack) },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = topBarColors(),
    )
}

@Composable
fun rememberLargeTopBarBehavior(): TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

@Composable
fun rememberPinnedTopBarBehavior(): TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

@Composable
private fun topBarColors() =
    TopAppBarDefaults.largeTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        actionIconContentColor = MaterialTheme.colorScheme.onBackground,
    )

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.size(TouchTarget)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.core_ui_back))
    }
}

// ---- Lists -------------------------------------------------------------------------------------

/**
 * Standard list row: 56–72dp, leading slot at the start, title 18/500, secondary 14 at 60 %, trailing slot
 * or a chevron that mirrors in RTL.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = onClick != null && trailing == null,
    subtitleColor: Color = LocalExtraColors.current.onSurfaceMuted,
) {
    val clickModifier =
        if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick, role = Role.Button) else Modifier
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (subtitle == null) Sizes.row else Sizes.rowTwoLine)
                .then(clickModifier)
                .padding(horizontal = ScreenPadding, vertical = Spacing.md)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = LocalExtraColors.current.onSurfaceFaint,
            )
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = if (subtitle == null) Sizes.row else Sizes.rowTwoLine)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = ScreenPadding, vertical = Spacing.md)
                .alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.onSurfaceMuted,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SettingNavRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListRow(title = title, subtitle = subtitle, onClick = onClick, trailing = trailing, modifier = modifier)
}

/** Legacy section header kept for callers; prefer [SectionLabel]. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) = SectionLabel(text, modifier)

// ---- Controls ----------------------------------------------------------------------------------

@Composable
fun MinutesSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = stringResource(R.string.core_ui_slider_minutes)
    val steps = ((max - min) / step - 1).coerceAtLeast(0)
    Slider(
        value = value.toFloat(),
        onValueChange = { raw ->
            val snapped = ((raw - min) / step).toInt() * step + min
            onValueChange(snapped.coerceIn(min, max))
        },
        valueRange = min.toFloat()..max.toFloat(),
        steps = steps,
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .semantics { contentDescription = label },
    )
}

@Composable
fun AppIcon(drawable: Drawable?, label: String, size: Dp = Sizes.appIcon, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.core_ui_app_icon, label)
    val bitmap =
        remember(drawable) {
            drawable?.let { runCatching { it.toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull() }
        }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = description, modifier = modifier.size(size))
    } else {
        Box(
            modifier =
                modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Screen states -----------------------------------------------------------------------------

/**
 * Empty state = disguised onboarding: a large icon, one sentence, one action. [icon] is an ImageVector or
 * any composable via [illustration].
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    illustration: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (illustration != null) {
            illustration()
        } else if (icon != null) {
            Box(
                modifier =
                    Modifier
                        .size(EMPTY_ICON_BOX)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(EMPTY_ICON),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtraColors.current.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        if (action != null) {
            Box(modifier = Modifier.padding(top = Spacing.xl)) { action() }
        }
    }
}

/** Compact variant for callers that only have a sentence. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) = EmptyState(title = text, modifier = modifier)

/** Error: what happened, what to do, and a retry / fix action. Never a technical code. */
@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(R.string.core_ui_retry),
    onAction: (() -> Unit)? = null,
) {
    val extras = LocalExtraColors.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = Spacing.sm)
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = extras.danger,
            modifier = Modifier.size(Sizes.icon),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = MUTED_ALPHA),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            if (onAction != null) {
                TonalButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = Spacing.md))
            }
        }
    }
}

/** A pulsing placeholder block; a screen shows a few of these instead of a blank surface while loading. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = Sizes.row, radius: Dp = Radius.lg) {
    val reduced = LocalReducedMotion.current
    val alpha =
        if (reduced) {
            SKELETON_ALPHA_MAX
        } else {
            val transition = rememberInfiniteTransition(label = "skeleton")
            transition
                .animateFloat(
                    initialValue = SKELETON_ALPHA_MIN,
                    targetValue = SKELETON_ALPHA_MAX,
                    animationSpec =
                        infiniteRepeatable(
                            tween(AnimationSpecs.SKELETON_PULSE_MILLIS),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "skeletonAlpha",
                ).value
        }
    val label = stringResource(R.string.core_ui_loading)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(radius))
                .semantics { contentDescription = label },
    )
}

/** Three stacked skeleton rows with screen margins. */
@Composable
fun SkeletonList(rows: Int = SKELETON_ROWS, modifier: Modifier = Modifier, rowHeight: Dp = Sizes.rowTwoLine) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(rows) { SkeletonBlock(height = rowHeight) }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = ColorMath.onColor(color),
        modifier =
            modifier
                .background(color, CircleShape)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

/** Row helper: pushes trailing content to the end. */
@Composable
fun RowScope.Grow() = Spacer(Modifier.weight(1f))

/** Thin divider at 12 % outline, the only divider style. */
@Composable
fun SubtleDivider(modifier: Modifier = Modifier, inset: Dp = ScreenPadding) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = inset)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA)),
    )
}

private const val ICON_PX = 144
private const val DISABLED_ALPHA = 0.38f
private const val MUTED_ALPHA = 0.8f
private const val DIVIDER_ALPHA = 0.12f
private const val SKELETON_ALPHA_MIN = 0.35f
private const val SKELETON_ALPHA_MAX = 0.7f
private const val SKELETON_ROWS = 3
private val EMPTY_ICON_BOX = 96.dp
private val EMPTY_ICON = 44.dp
