package il.rikavon.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import il.rikavon.core.ui.R

/** Rubik (SIL OFL) ships in the APK, so Hebrew and Latin share one confident face with no network. */
val Rubik =
    FontFamily(
        Font(R.font.rubik_regular, FontWeight.Normal),
        Font(R.font.rubik_medium, FontWeight.Medium),
        Font(R.font.rubik_bold, FontWeight.Bold),
        Font(R.font.rubik_extrabold, FontWeight.ExtraBold),
        Font(R.font.rubik_black, FontWeight.Black),
    )

private fun style(size: Int, line: Int, weight: FontWeight): TextStyle =
    TextStyle(
        fontFamily = Rubik,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        // Hebrew is never letter-spaced.
        letterSpacing = 0.sp,
    )

/**
 * One family, hierarchy through size and weight only. Body is 16sp (Hebrew never below 16), secondary 14,
 * caption 12, everything in sp so 200 % font scaling works. Line height 1.5 for body, 1.2 for headings.
 *
 * Roles: displayLarge = the block screen headline, displayMedium = the home score, displaySmall = big
 * editor values, headlineMedium = expanded screen title, titleLarge = collapsed screen title / card title,
 * titleMedium = list row title, bodyMedium = body, bodySmall = secondary, labelLarge = buttons,
 * labelSmall = captions and bottom-bar labels.
 */
val RikavonTypography =
    Typography(
        displayLarge = style(size = 72, line = 76, weight = FontWeight.Black),
        displayMedium = style(size = 56, line = 60, weight = FontWeight.Black),
        displaySmall = style(size = 40, line = 44, weight = FontWeight.ExtraBold),
        headlineLarge = style(size = 34, line = 40, weight = FontWeight.Bold),
        headlineMedium = style(size = 32, line = 38, weight = FontWeight.Bold),
        headlineSmall = style(size = 26, line = 32, weight = FontWeight.Bold),
        titleLarge = style(size = 22, line = 28, weight = FontWeight.Bold),
        titleMedium = style(size = 18, line = 24, weight = FontWeight.Medium),
        titleSmall = style(size = 16, line = 22, weight = FontWeight.Medium),
        bodyLarge = style(size = 17, line = 26, weight = FontWeight.Normal),
        bodyMedium = style(size = 16, line = 24, weight = FontWeight.Normal),
        bodySmall = style(size = 14, line = 20, weight = FontWeight.Normal),
        labelLarge = style(size = 16, line = 20, weight = FontWeight.Medium),
        labelMedium = style(size = 14, line = 18, weight = FontWeight.Medium),
        labelSmall = style(size = 12, line = 16, weight = FontWeight.Medium),
    )
