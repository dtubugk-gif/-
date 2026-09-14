package il.rikavon.core.ui.anim

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True when motion should be minimal: no particles, no morphs, basic fades only. */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Android has no single "reduce motion" switch; the accepted signal is the animator duration scale
 * being turned off in accessibility / developer settings.
 */
fun systemReducedMotion(context: Context): Boolean {
    val scale =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            DEFAULT_ANIMATOR_SCALE,
        )
    return scale == 0f
}

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { systemReducedMotion(context) }
}

private const val DEFAULT_ANIMATOR_SCALE = 1f
