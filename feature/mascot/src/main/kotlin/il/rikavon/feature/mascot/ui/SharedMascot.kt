package il.rikavon.feature.mascot.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import il.rikavon.feature.mascot.model.Localized
import java.util.Locale

/** Provided by the app's navigation host so the mascot can fly between the home screen and the gallery. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

const val MASCOT_SHARED_KEY = "mascot-hero"

@Composable
internal fun sharedElementModifier(key: String?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedVisibilityScope.current
    if (key == null || shared == null || visibility == null) return Modifier
    return with(shared) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
        )
    }
}

/** Current UI language tag ("he"/"en") as seen by the composition. */
object UiLanguage {
    @Composable
    fun current(): String = fromLocale(LocalConfiguration.current.locales)

    fun fromLocale(locales: android.os.LocaleList): String {
        val locale: Locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
        val language = locale.language
        return if (language == HEBREW_LEGACY) Localized.DEFAULT_LANGUAGE else language
    }

    private const val HEBREW_LEGACY = "iw"
}
