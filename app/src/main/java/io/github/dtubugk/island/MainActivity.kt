package io.github.dtubugk.island

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dtubugk.island.data.IslandCommand
import io.github.dtubugk.island.data.IslandConfig
import io.github.dtubugk.island.data.IslandSettings
import io.github.dtubugk.island.island.BatteryState
import io.github.dtubugk.island.island.IslandService
import io.github.dtubugk.island.ui.IslandActions
import io.github.dtubugk.island.ui.IslandScreen
import io.github.dtubugk.island.ui.IslandTheme

class MainActivity : ComponentActivity(), IslandActions {

    private var enabledInSettings by mutableStateOf(false)
    private var battery by mutableStateOf(BatteryState(100, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        IslandSettings.init(this)
        setContent {
            val config by IslandSettings.config.collectAsStateWithLifecycle()
            val running by IslandService.running.collectAsStateWithLifecycle()
            IslandTheme {
                IslandScreen(
                    config = config,
                    serviceOn = running || enabledInSettings,
                    battery = battery,
                    actions = this,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enabledInSettings = isServiceEnabled()
        battery = IslandService.readBattery(this)
    }

    private val serviceComponent get() = ComponentName(this, IslandService::class.java)

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == serviceComponent }
    }

    // --- IslandActions ------------------------------------------------------------------------

    override fun update(transform: (IslandConfig) -> IslandConfig) = IslandSettings.update(transform)

    override fun enableService() {
        val component = serviceComponent.flattenToString()
        // Straight to this app's switch where the system supports it, else the accessibility list
        // with the entry highlighted.
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, component)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, bundleOf(EXTRA_FRAGMENT_ARG_KEY to component))
        if (!tryStart(details) && !tryStart(list)) {
            Toast.makeText(this, "פתחו הגדרות ← נגישות ← אפליקציות מותקנות", Toast.LENGTH_LONG).show()
        }
    }

    override fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        if (!tryStart(intent)) tryStart(Intent(Settings.ACTION_SETTINGS))
    }

    override fun previewExpanded() = IslandSettings.send(IslandCommand.PREVIEW_EXPANDED)

    override fun previewCharging() = IslandSettings.send(IslandCommand.PREVIEW_CHARGING)

    private fun tryStart(intent: Intent): Boolean = runCatching { startActivity(intent) }.isSuccess

    private companion object {
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    }
}
