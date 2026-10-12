package com.dtubugk.shakelight.shake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dtubugk.shakelight.core.Prefs

/** Restarts shake detection after the phone reboots or the app is updated. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = Prefs.get(context)
        if (prefs.getBoolean(Prefs.SHAKE_ENABLED, false) && prefs.getBoolean(Prefs.START_ON_BOOT, true)) {
            runCatching { ShakeService.start(context) }
        }
    }
}
