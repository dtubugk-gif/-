package il.rikavon.core.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import il.rikavon.core.ui.anim.LocalReducedMotion

/** The accent colour of the currently selected mascot, available everywhere. */
val LocalMascotAccent = compositionLocalOf { RikavonColors.DefaultAccent }

val RikavonShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

private const val ACCENT_LIGHTNESS = 0.68f
private const val ACCENT_CONTAINER_LIGHTNESS = 0.24f
private const val SECONDARY_HUE_SHIFT = 40f
private const val TERTIARY_HUE_SHIFT = -60f
private const val ON_CONTAINER_LIGHTNESS = 0.9f

/** Builds a flat dark scheme whose accents derive from [accent]. */
fun schemeFromAccent(accent: Color): ColorScheme {
    val primary = ColorMath.withLightness(accent, ACCENT_LIGHTNESS)
    val primaryContainer = ColorMath.withLightness(accent, ACCENT_CONTAINER_LIGHTNESS)
    val secondary = ColorMath.withLightness(ColorMath.shiftHue(accent, SECONDARY_HUE_SHIFT), ACCENT_LIGHTNESS)
    val tertiary = ColorMath.withLightness(ColorMath.shiftHue(accent, TERTIARY_HUE_SHIFT), ACCENT_LIGHTNESS)
    return darkColorScheme(
        primary = primary,
        onPrimary = ColorMath.onColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = ColorMath.withLightness(accent, ON_CONTAINER_LIGHTNESS),
        secondary = secondary,
        onSecondary = ColorMath.onColor(secondary),
        secondaryContainer = ColorMath.withLightness(secondary, ACCENT_CONTAINER_LIGHTNESS),
        onSecondaryContainer = ColorMath.withLightness(secondary, ON_CONTAINER_LIGHTNESS),
        tertiary = tertiary,
        onTertiary = ColorMath.onColor(tertiary),
        background = RikavonColors.Ink,
        onBackground = RikavonColors.OnInk,
        surface = RikavonColors.Ink,
        onSurface = RikavonColors.OnInk,
        surfaceVariant = RikavonColors.SurfaceRaised,
        onSurfaceVariant = RikavonColors.OnInkMuted,
        surfaceContainer = RikavonColors.Surface,
        surfaceContainerLow = RikavonColors.Surface,
        surfaceContainerHigh = RikavonColors.SurfaceRaised,
        surfaceContainerHighest = RikavonColors.SurfaceHigh,
        outline = RikavonColors.Outline,
        outlineVariant = RikavonColors.SurfaceHigh,
        error = RikavonColors.Danger,
        onError = RikavonColors.Ink,
        errorContainer = ColorMath.withLightness(RikavonColors.Danger, ACCENT_CONTAINER_LIGHTNESS),
        onErrorContainer = ColorMath.withLightness(RikavonColors.Danger, ON_CONTAINER_LIGHTNESS),
    )
}

@Composable
fun RikavonTheme(
    accent: Color = RikavonColors.DefaultAccent,
    dynamicColor: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme =
        remember(accent, dynamicColor) {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context).copy(background = RikavonColors.Ink, surface = RikavonColors.Ink)
            } else {
                schemeFromAccent(accent)
            }
        }
    CompositionLocalProvider(
        LocalMascotAccent provides scheme.primary,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RikavonTypography,
            shapes = RikavonShapes,
            content = content,
        )
    }
}
