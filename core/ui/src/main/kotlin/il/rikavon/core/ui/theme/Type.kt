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

/** Large, confident type. Everything is in sp so 200% font scaling works. */
val RikavonTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                fontSize = 88.sp,
                lineHeight = 88.sp,
                letterSpacing = (-2).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                fontSize = 64.sp,
                lineHeight = 64.sp,
                letterSpacing = (-1.5).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                fontSize = 44.sp,
                lineHeight = 44.sp,
                letterSpacing = (-1).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.5).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                lineHeight = 30.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp,
                lineHeight = 26.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
    )
