package il.rikavon.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import il.rikavon.core.ui.R
import il.rikavon.core.ui.theme.ColorMath
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Radius
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing

// ---- Text --------------------------------------------------------------------------------------

/** Section label: 14/500 at 60 %, screen margins. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalExtraColors.current.onSurfaceMuted,
        modifier = modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
    )
}

/** Large in-content title (used where a screen has no app bar, like the home header). */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = ScreenPadding)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.onSurfaceMuted,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

// ---- Surfaces ----------------------------------------------------------------------------------

/** Card: radius 16, padding 16, surface tint instead of shadow. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.lg,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    padding: Dp = Spacing.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val clickModifier =
        if (onClick != null) {
            Modifier
                .pressScale(
                    interaction,
                ).clickable(interactionSource = interaction, indication = null, onClick = onClick, role = Role.Button)
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .then(clickModifier)
                .background(color, RoundedCornerShape(radius))
                .padding(padding),
    ) {
        content()
    }
}

/** A group of rows in one rounded surface (settings style). Rows separate with spacing, no dividers. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radius.lg))
                .padding(vertical = Spacing.xs),
    ) {
        content()
    }
}

/** Big number + caption tile. */
@Composable
fun StatTile(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Radius.lg))
                .padding(Spacing.lg)
                .semantics { contentDescription = "$caption: $value" },
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor, maxLines = 1)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.onSurfaceMuted,
            modifier = Modifier.padding(top = Spacing.xs),
            maxLines = 2,
        )
    }
}

/** Thin rounded progress bar. */
@Composable
fun ThinBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = Spacing.xs) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(LocalExtraColors.current.track, CircleShape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(height)
                    .background(color, CircleShape),
        )
    }
}

/** Speech bubble for the mascot's line. */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = ColorMath.onColor(color),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(color, RoundedCornerShape(Radius.lg))
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    )
}

/** Small lock badge on locked cards. */
@Composable
fun LockBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(Radius.sm))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = stringResource(R.string.core_ui_locked),
            tint = LocalExtraColors.current.onSurfaceMuted,
            modifier = Modifier.size(LOCK_ICON),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalExtraColors.current.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Selection ---------------------------------------------------------------------------------

/** Segmented single-choice control (Material 3), 40dp pills. */
@Composable
fun <T> SegmentPills(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.heightIn(min = Sizes.chip)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        inactiveContentColor = LocalExtraColors.current.onSurfaceMuted,
                    ),
                icon = {},
                label = { Text(label(option), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
            )
        }
    }
}

/** Compact chip with a coloured dot, 40dp. */
@Composable
fun DotChip(text: String, dot: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .heightIn(min = Sizes.chip)
                .pressScale(interaction)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick, role = Role.Button)
                .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(modifier = Modifier.size(DOT).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// ---- Navigation --------------------------------------------------------------------------------

data class BottomTab(val icon: ImageVector, val label: String)

/** Material 3 navigation bar: icon + 12sp label, pill indicator on the active item, 80dp + insets. */
@Composable
fun RikavonBottomBar(tabs: List<BottomTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                alwaysShowLabel = true,
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = LocalExtraColors.current.onSurfaceMuted,
                        unselectedTextColor = LocalExtraColors.current.onSurfaceMuted,
                    ),
            )
        }
    }
}

private val DOT = 8.dp
private val LOCK_ICON = 12.dp
