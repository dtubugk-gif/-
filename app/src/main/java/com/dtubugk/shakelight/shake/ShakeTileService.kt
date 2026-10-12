package com.dtubugk.shakelight.shake

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.edit
import com.dtubugk.shakelight.core.Prefs
import com.dtubugk.shakelight.ui.MainActivity

/** Quick Settings tile: turn shake detection on/off from the notification shade. */
class ShakeTileService : TileService() {

    override fun onStartListening() {
        render(Prefs.get(this).getBoolean(Prefs.SHAKE_ENABLED, false))
    }

    override fun onClick() {
        val enable = !Prefs.get(this).getBoolean(Prefs.SHAKE_ENABLED, false)
        Prefs.get(this).edit { putBoolean(Prefs.SHAKE_ENABLED, enable) }
        if (enable) {
            try {
                ShakeService.start(this)
            } catch (e: Exception) {
                // Some Android versions block starting background work from a tile; open the app instead.
                openApp()
            }
        } else {
            ShakeService.stop(this)
        }
        render(enable)
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
