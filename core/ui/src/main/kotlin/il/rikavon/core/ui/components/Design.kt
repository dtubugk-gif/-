package il.rikavon.core.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import il.rikavon.core.ui.theme.ColorMath
import il.rikavon.core.ui.theme.LocalExtraColors

/** Big screen title with an optional muted subtitle (design: 30sp/800 + 13sp at 50%). */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = ScreenPadding)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.onSurfaceFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Muted uppercase-ish section label (design: 13sp/700 at 50% with letter spacing). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalExtraColors.current.onSurfaceFaint,
        modifier = modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
    )
}

/** A rounded surface card (design: #1d1a15, radius 16–24). */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    padding: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .background(color, RoundedCornerShape(radius))
                .padding(padding),
    ) {
        content()
    }
}

/** Filled pill button (design: accent background, dark text, min 48–52dp). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = ColorMath.onColor(color),
    minHeight: Dp = 52.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = minHeight)
                .background(color, CircleShape)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        leading?.invoke()
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/** Segmented pill selector (design: "7 ימים / 30 יום"). */
@Composable
fun <T> SegmentPills(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            val scheme = MaterialTheme.colorScheme
            val background = if (active) scheme.primary else scheme.surfaceContainerHigh
            Box(
                modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .background(background, CircleShape)
                        .selectable(selected = active, role = Role.Tab) { onSelect(option) }
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = if (active) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    color = if (active) scheme.onPrimary else LocalExtraColors.current.onSurfaceMuted,
                )
            }
        }
    }
}

/** Compact chip with a coloured dot (design: "טיקטוק · 48/60"). */
@Composable
fun DotChip(text: String, dot: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .heightIn(min = TouchTarget)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .clickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(9.dp).background(dot, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/** Big number + caption tile (design: "63 / פתיחות היום"). */
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
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
                .padding(14.dp)
                .semantics { contentDescription = "$caption: $value" },
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor, maxLines = 1)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.onSurfaceMuted,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 2,
        )
    }
}

/** Thin rounded progress bar (design: 4–5dp, track #26211b). */
@Composable
fun ThinBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp) {
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

/** Speech bubble for the mascot's line (design: surface pill, 13.5sp). */
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
                .background(color, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Square icon button (design: 48dp, radius 14, surface). */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(TouchTarget)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

data class BottomTab(val icon: ImageVector, val label: String)

/** Icon-only bottom bar with an active dot (design 1a). */
@Composable
fun RikavonBottomBar(tabs: List<BottomTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            Column(
                modifier =
                    Modifier
                        .size(width = 64.dp, height = TouchTarget)
                        .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                        .semantics { contentDescription = tab.label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.primary else LocalExtraColors.current.onSurfaceFaint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier =
                        Modifier
                            .size(4.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape,
                            ),
                )
            }
        }
    }
}

/** Row helper: pushes trailing content to the end. */
@Composable
fun RowScope.Grow() = Spacer(Modifier.weight(1f))

/** Small lock badge (design: 10sp on #262b33). */
@Composable
fun LockBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(
                        7.dp,
                    ).height(9.dp)
                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(1.5.dp)),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
            color = LocalExtraColors.current.onSurfaceMuted,
            maxLines = 1,
        )
    }
}
