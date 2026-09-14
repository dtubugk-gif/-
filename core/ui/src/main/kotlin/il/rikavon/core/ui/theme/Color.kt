package il.rikavon.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Warm, flat, dark palette from the design canvas. Surfaces are tinted by the selected mascot's accent
 * (see [schemeFromAccent]); the values here are the neutral base and the semantic colours.
 */
object RikavonColors {
    val Ink = Color(0xFF131110)
    val Surface = Color(0xFF161310)
    val SurfaceRaised = Color(0xFF1D1A15)
    val SurfaceHigh = Color(0xFF221E19)
    val Track = Color(0xFF26211B)
    val Outline = Color(0xFF3A3328)
    val OnInk = Color(0xFFF4EFE6)
    val OnInkMuted = Color(0x99F4EFE6)
    val OnInkFaint = Color(0x80F4EFE6)
    val Danger = Color(0xFFC9564E)
    val Success = Color(0xFF7DC9A6)
    val Warning = Color(0xFFE0B64F)
    val OverLimit = Color(0xFFE08B4F)
    val DefaultAccent = Color(0xFFE0B64F)

    /** The block screen is always rot-green, whichever mascot is selected. */
    val RotAccent = Color(0xFFA3B833)
    val RotInk = Color(0xFF0E120A)
    val RotSurface = Color(0xFF1A2113)
    val RotOnInk = Color(0xFFEEF2E2)
    val RotOnAccent = Color(0xFF141807)
}

/** Minimal HSL helpers so a mascot's single theme colour can seed a whole scheme. */
object ColorMath {
    private const val SIX = 6f
    private const val TWO = 2f
    private const val FOUR = 4f
    private const val HALF = 0.5f
    private const val THIRD = 1f / 3f
    private const val TWO_THIRDS = 2f / 3f
    private const val DEGREES = 360f
    private const val HUE_SECTOR = 60f

    data class Hsl(val h: Float, val s: Float, val l: Float)

    fun toHsl(color: Color): Hsl {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / TWO
        if (max == min) return Hsl(0f, 0f, l)
        val d = max - min
        val s = if (l > HALF) d / (TWO - max - min) else d / (max + min)
        val h =
            when (max) {
                r -> ((g - b) / d + (if (g < b) SIX else 0f)) * HUE_SECTOR
                g -> ((b - r) / d + TWO) * HUE_SECTOR
                else -> ((r - g) / d + FOUR) * HUE_SECTOR
            }
        return Hsl(h % DEGREES, s, l)
    }

    fun fromHsl(hsl: Hsl, alpha: Float = 1f): Color {
        val (h, s, l) = hsl
        if (s == 0f) return Color(l, l, l, alpha)
        val q = if (l < HALF) l * (1 + s) else l + s - l * s
        val p = TWO * l - q
        val hk = (h % DEGREES) / DEGREES
        return Color(hue(p, q, hk + THIRD), hue(p, q, hk), hue(p, q, hk - THIRD), alpha)
    }

    private fun hue(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / SIX -> p + (q - p) * SIX * t
            t < HALF -> q
            t < TWO_THIRDS -> p + (q - p) * (TWO_THIRDS - t) * SIX
            else -> p
        }
    }

    fun withLightness(color: Color, lightness: Float): Color =
        fromHsl(toHsl(color).copy(l = lightness.coerceIn(0f, 1f)))

    fun withSaturation(color: Color, saturation: Float): Color =
        fromHsl(toHsl(color).copy(s = saturation.coerceIn(0f, 1f)))

    fun shiftHue(color: Color, degrees: Float): Color {
        val hsl = toHsl(color)
        return fromHsl(hsl.copy(h = (hsl.h + degrees + DEGREES) % DEGREES))
    }

    /** A near-black surface that carries a hint of [accent]'s hue (design: #161310 for ochre, #171114 for pink). */
    fun tintedSurface(accent: Color, lightness: Float, saturation: Float): Color {
        val hsl = toHsl(accent)
        return fromHsl(Hsl(hsl.h, saturation, lightness))
    }

    /** WCAG relative luminance contrast ratio between two opaque colours. */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + LUMINANCE_OFFSET
        val lb = b.luminance() + LUMINANCE_OFFSET
        return if (la > lb) la / lb else lb / la
    }

    /** Picks black or white text for [background] to meet AA contrast. */
    fun onColor(background: Color): Color =
        if (abs(contrast(background, RikavonColors.Ink)) >= AA_CONTRAST) RikavonColors.Ink else RikavonColors.OnInk

    private const val LUMINANCE_OFFSET = 0.05f
    private const val AA_CONTRAST = 4.5f
    private const val RED_WEIGHT = 0.2126f
    private const val GREEN_WEIGHT = 0.7152f
    private const val BLUE_WEIGHT = 0.0722f
    private const val SRGB_THRESHOLD = 0.03928f
    private const val SRGB_DIVISOR = 12.92f
    private const val SRGB_OFFSET = 0.055f
    private const val SRGB_SCALE = 1.055f
    private const val SRGB_GAMMA = 2.4f

    private fun Color.luminance(): Float {
        fun channel(c: Float): Float =
            if (c <= SRGB_THRESHOLD) {
                c / SRGB_DIVISOR
            } else {
                Math.pow(((c + SRGB_OFFSET) / SRGB_SCALE).toDouble(), SRGB_GAMMA.toDouble()).toFloat()
            }
        return RED_WEIGHT * channel(red) + GREEN_WEIGHT * channel(green) + BLUE_WEIGHT * channel(blue)
    }
}
