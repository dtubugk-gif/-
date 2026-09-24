package io.github.dtubugk.island.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.dtubugk.island.R

val Rubik = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
)

/** Brand accents, shared with the island's gradient and the launcher icon. */
object Brand {
    val Blue = Color(0xFF3E63F5)
    val Pink = Color(0xFFFF5C9D)
    val GradientStart = Color(0xFF6E8BFF)
    val GradientEnd = Color(0xFFFF6FA3)
    val Success = Color(0xFF1FA35B)
    val SuccessDark = Color(0xFF4CD787)
}

private val Light = lightColorScheme(
    primary = Color(0xFF3552F2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E7FF),
    onPrimaryContainer = Color(0xFF0F1C6B),
    secondaryContainer = Color(0xFFE8E8F4),
    onSecondaryContainer = Color(0xFF1C1C2A),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF14141C),
    surface = Color(0xFFF6F6FB),
    onSurface = Color(0xFF14141C),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEFF7),
    surfaceContainerHighest = Color(0xFFE6E6F0),
    onSurfaceVariant = Color(0x9914141C),
    outline = Color(0x3314141C),
    outlineVariant = Color(0x1F14141C),
    error = Color(0xFFD92D3A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9AABFF),
    onPrimary = Color(0xFF0B1250),
    primaryContainer = Color(0xFF28317A),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondaryContainer = Color(0xFF2B2B38),
    onSecondaryContainer = Color(0xFFE6E6F2),
    background = Color(0xFF0F0F14),
    onBackground = Color(0xDEFFFFFF),
    surface = Color(0xFF0F0F14),
    onSurface = Color(0xDEFFFFFF),
    surfaceContainer = Color(0xFF1A1A22),
    surfaceContainerHigh = Color(0xFF22222C),
    surfaceContainerHighest = Color(0xFF2B2B36),
    onSurfaceVariant = Color(0x99FFFFFF),
    outline = Color(0x40FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),
    error = Color(0xFFFF6B73),
)

private val Base = Typography()

private fun TextStyle.rubik(size: Int, weight: FontWeight, line: Float) =
    copy(fontFamily = Rubik, fontSize = size.sp, fontWeight = weight, lineHeight = (size * line).sp, letterSpacing = 0.sp)

// Hebrew: body never below 16sp, generous line height, no letter spacing anywhere.
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.rubik(34, FontWeight.Bold, 1.2f),
    headlineSmall = Base.headlineSmall.rubik(22, FontWeight.SemiBold, 1.25f),
    titleLarge = Base.titleLarge.rubik(20, FontWeight.SemiBold, 1.3f),
    titleMedium = Base.titleMedium.rubik(17, FontWeight.SemiBold, 1.35f),
    titleSmall = Base.titleSmall.rubik(15, FontWeight.Medium, 1.35f),
    bodyLarge = Base.bodyLarge.rubik(16, FontWeight.Normal, 1.55f),
    bodyMedium = Base.bodyMedium.rubik(14, FontWeight.Normal, 1.5f),
    bodySmall = Base.bodySmall.rubik(12, FontWeight.Normal, 1.45f),
    labelLarge = Base.labelLarge.rubik(16, FontWeight.Medium, 1.25f),
    labelMedium = Base.labelMedium.rubik(13, FontWeight.Medium, 1.3f),
    labelSmall = Base.labelSmall.rubik(12, FontWeight.Medium, 1.3f),
)

@Composable
fun IslandTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography) {
        // The whole app is Hebrew, so it is right-to-left even on a phone set to English.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
