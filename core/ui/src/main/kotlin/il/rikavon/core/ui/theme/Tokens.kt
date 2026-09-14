package il.rikavon.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single source of truth for sizes. Screens never hard-code a dp value that has a token here.
 * 8dp grid with 4dp subdivisions (allowed spacing: 4, 8, 12, 16, 24, 32, 48).
 */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 48.dp

    /** Side margin of every screen on phones. */
    val screen: Dp = lg

    /** Gap between adjacent touch targets. */
    val targetGap: Dp = sm
}

/** Corner radii: 8 small controls, 12 medium, 16 cards / rows, 20 hero cards, 28 bottom sheets. */
object Radius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val sheet: Dp = 28.dp
}

/** Component sizes. */
object Sizes {
    /** Minimum touch target (Material). */
    val touch: Dp = 48.dp

    /** Primary button height. */
    val button: Dp = 52.dp

    /** Text field height. */
    val input: Dp = 56.dp

    /** Single-line list row. */
    val row: Dp = 56.dp

    /** Two-line list row. */
    val rowTwoLine: Dp = 72.dp

    /** Chips and segmented buttons. */
    val chip: Dp = 40.dp

    /** Standard icon. */
    val icon: Dp = 24.dp

    /** App icon inside a list row. */
    val appIcon: Dp = 40.dp

    /** Bottom sheet drag handle. */
    val handleWidth: Dp = 32.dp
    val handleHeight: Dp = 4.dp
}
