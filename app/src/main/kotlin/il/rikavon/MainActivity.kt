package il.rikavon

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import il.rikavon.app.RikavonRoot
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.feature.blocker.contact.DeepLinks
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settings: SettingsRepository

    /** A screen the launch intent asked for (the call screen's "Talk back"); consumed once opened. */
    private var pendingOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The app is dark-only, so both bars always get light icons over transparent backgrounds.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        pendingOpen = intent.getStringExtra(DeepLinks.EXTRA_OPEN)
        setContent { RikavonRoot(pendingOpen = pendingOpen, onOpened = { pendingOpen = null }) }
        lifecycleScope.launch {
            settings.settings
                .map { it.language }
                .distinctUntilChanged()
                .collect { applyLanguage(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(DeepLinks.EXTRA_OPEN)?.let { pendingOpen = it }
    }

    /** Per-app language; must run on the main thread because AppCompat recreates the activity. */
    private fun applyLanguage(language: AppLanguage) {
        val desired =
            if (language == AppLanguage.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
        if (AppCompatDelegate.getApplicationLocales() != desired) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }
}
