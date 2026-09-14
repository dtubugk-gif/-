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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import il.rikavon.core.ui.anim.LocalReducedMotion

/** The accent colour of the currently selected mascot, available everywhere. */
val LocalMascotAccent = compositionLocalOf { RikavonColors.DefaultAccent }

/** Semantic colours that Material's scheme has no slot for. */
@Immutable
data class RikavonExtraColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val overLimit: Color,
    val track: Color,
    val onSurfaceMuted: Color,
    val onSurfaceFaint: Color,
)

val LocalExtraColors =
    compositionLocalOf {
        RikavonExtraColors(
            success = RikavonColors.Success,
            warning = RikavonColors.Warning,
            danger = RikavonColors.Danger,
            overLimit = RikavonColors.OverLimit,
            track = RikavonColors.Track,
            onSurfaceMuted = RikavonColors.OnInkMuted,
            onSurfaceFaint = RikavonColors.OnInkFaint,
        )
    }

/** Design radii: 14 for small controls, 16 for rows, 20/24 for cards, 28 for hero panels, pills elsewhere. */
val RikavonShapes =
    Shapes(
        extraSmall = RoundedCornerShape(11.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

private const val ACCENT_LIGHTNESS = 0.6f
private const val ACCENT_CONTAINER_LIGHTNESS = 0.2f
private const val ON_CONTAINER_LIGHTNESS = 0.9f
private const val SECONDARY_HUE_SHIFT = 40f
private const val TERTIARY_HUE_SHIFT = -60f
private const val BACKGROUND_LIGHTNESS = 0.075f
private const val SURFACE_LOW_LIGHTNESS = 0.095f
private const val SURFACE_LIGHTNESS = 0.115f
private const val SURFACE_HIGH_LIGHTNESS = 0.135f
private const val SURFACE_HIGHEST_LIGHTNESS = 0.17f
private const val SURFACE_TINT_SATURATION = 0.12f
private const val ON_SURFACE_LIGHTNESS = 0.93f
private const val ON_SURFACE_SATURATION = 0.25f

/**
 * Builds the warm dark scheme whose surfaces and accents derive from [accent] (design canvas rule).
 * [surfaceTint] overrides the hue used for backgrounds when a mascot wants cool surfaces with a warm accent.
 */
fun schemeFromAccent(accent: Color, surfaceTint: Color? = null): ColorScheme {
    val tint = surfaceTint ?: accent
    val primary = ColorMath.withLightness(accent, ACCENT_LIGHTNESS)
    val primaryContainer = ColorMath.withLightness(accent, ACCENT_CONTAINER_LIGHTNESS)
    val secondary = ColorMath.withLightness(ColorMath.shiftHue(accent, SECONDARY_HUE_SHIFT), ACCENT_LIGHTNESS)
    val tertiary = ColorMath.withLightness(ColorMath.shiftHue(accent, TERTIARY_HUE_SHIFT), ACCENT_LIGHTNESS)
    val background = ColorMath.tintedSurface(tint, BACKGROUND_LIGHTNESS, SURFACE_TINT_SATURATION)
    val onSurface = ColorMath.tintedSurface(tint, ON_SURFACE_LIGHTNESS, ON_SURFACE_SATURATION)
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
        background = background,
        onBackground = onSurface,
        surface = background,
        onSurface = onSurface,
        surfaceVariant = ColorMath.tintedSurface(tint, SURFACE_HIGH_LIGHTNESS, SURFACE_TINT_SATURATION),
        onSurfaceVariant = onSurface.copy(alpha = MUTED_ALPHA),
        surfaceContainerLowest = background,
        surfaceContainerLow = ColorMath.tintedSurface(tint, SURFACE_LOW_LIGHTNESS, SURFACE_TINT_SATURATION),
        surfaceContainer = ColorMath.tintedSurface(tint, SURFACE_LIGHTNESS, SURFACE_TINT_SATURATION),
        surfaceContainerHigh = ColorMath.tintedSurface(tint, SURFACE_HIGH_LIGHTNESS, SURFACE_TINT_SATURATION),
        surfaceContainerHighest = ColorMath.tintedSurface(tint, SURFACE_HIGHEST_LIGHTNESS, SURFACE_TINT_SATURATION),
        outline = RikavonColors.Outline,
        outlineVariant = ColorMath.tintedSurface(tint, SURFACE_HIGHEST_LIGHTNESS, SURFACE_TINT_SATURATION),
        error = RikavonColors.Danger,
        onError = RikavonColors.Ink,
        errorContainer = ColorMath.withLightness(RikavonColors.Danger, ACCENT_CONTAINER_LIGHTNESS),
        onErrorContainer = ColorMath.withLightness(RikavonColors.Danger, ON_CONTAINER_LIGHTNESS),
    )
}

private const val MUTED_ALPHA = 0.6f

/** The block screen palette: rot green on near-black green, independent of the selected mascot. */
fun rotScheme(): ColorScheme =
    schemeFromAccent(RikavonColors.RotAccent).copy(
        primary = RikavonColors.RotAccent,
        onPrimary = RikavonColors.RotOnAccent,
        background = RikavonColors.RotInk,
        surface = RikavonColors.RotInk,
        onBackground = RikavonColors.RotOnInk,
        onSurface = RikavonColors.RotOnInk,
        onSurfaceVariant = RikavonColors.RotOnInk.copy(alpha = MUTED_ALPHA),
        surfaceContainer = RikavonColors.RotSurface,
        surfaceContainerHigh = RikavonColors.RotSurface,
    )

@Composable
fun RikavonTheme(
    accent: Color = RikavonColors.DefaultAccent,
    surfaceTint: Color? = null,
    dynamicColor: Boolean = false,
    reducedMotion: Boolean = false,
    scheme: ColorScheme? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolved =
        scheme ?: remember(accent, surfaceTint, dynamicColor) {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context).copy(background = RikavonColors.Ink, surface = RikavonColors.Ink)
            } else {
                schemeFromAccent(accent, surfaceTint)
            }
        }
    val extras =
        remember(resolved) {
            RikavonExtraColors(
                success = RikavonColors.Success,
                warning = RikavonColors.Warning,
                danger = RikavonColors.Danger,
                overLimit = RikavonColors.OverLimit,
                track = resolved.surfaceContainerHighest,
                onSurfaceMuted = resolved.onSurface.copy(alpha = MUTED_ALPHA),
                onSurfaceFaint = resolved.onSurface.copy(alpha = FAINT_ALPHA),
            )
        }
    CompositionLocalProvider(
        LocalMascotAccent provides resolved.primary,
        LocalReducedMotion provides reducedMotion,
        LocalExtraColors provides extras,
    ) {
        MaterialTheme(
            colorScheme = resolved,
            typography = RikavonTypography,
            shapes = RikavonShapes,
            content = content,
        )
    }
}

private const val FAINT_ALPHA = 0.5f

/** Score colour rule from the canvas: pristine is green, rotten is red, everything else is the mascot accent. */
@Composable
fun scoreColor(score: Int): Color {
    val extras = LocalExtraColors.current
    return when {
        score >= SCORE_GREEN_MIN -> extras.success
        score < SCORE_RED_MAX -> extras.danger
        else -> MaterialTheme.colorScheme.primary
    }
}

private const val SCORE_GREEN_MIN = 90
private const val SCORE_RED_MAX = 30
