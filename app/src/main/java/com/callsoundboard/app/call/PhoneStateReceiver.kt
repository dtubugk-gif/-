package com.callsoundboard.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.callsoundboard.app.data.AppSettings
import com.callsoundboard.app.overlay.OverlayService

/**
 * Manifest-registered receiver for PHONE_STATE. ACTION_PHONE_STATE_CHANGED is on
 * the implicit-broadcast exception list, so this still fires on modern Android.
 *
 * Its ONLY job is to bootstrap the OverlayService when a call becomes active.
 * The service itself hosts a TelephonyCallback/PhoneStateListener to detect when
 * the call ends (IDLE) and stops itself. We never read the phone number, so we
 * do not request READ_CALL_LOG.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return

        if (state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
            if (!AppSettings.isAutoBubbleEnabled(context)) return
            // Foreground-service background start is only permitted while we hold
            // the overlay ("draw over other apps") permission — so gate on it.
            if (!Settings.canDrawOverlays(context)) return
            try {
                val svc = Intent(context, OverlayService::class.java)
                    .putExtra(OverlayService.EXTRA_FROM_CALL, true)
                ContextCompat.startForegroundService(context, svc)
            } catch (e: Exception) {
                Log.w("PhoneStateReceiver", "Could not start OverlayService", e)
            }
        }
    }
}
