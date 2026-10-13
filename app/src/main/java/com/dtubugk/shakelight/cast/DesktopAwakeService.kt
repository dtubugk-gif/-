package com.dtubugk.shakelight.cast

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.dtubugk.shakelight.App
import com.dtubugk.shakelight.R

/**
 * Keeps the phone awake while it runs the Computer-mode desktop. Android stops drawing every screen,
 * including the desktop one, when the phone sleeps (a locked phone sleeps after ~10 s).
 *
 * Started and stopped by ShakeLight Receiver over Wireless debugging; only the debugging shell can
 * reach it (android.permission.DUMP in the manifest). The notification shows while it runs.
 */
class DesktopAwakeService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (wakeLock?.isHeld != true) {
            // A dim screen lock keeps the phone awake; the computer can still switch the panel off.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "ShakeLight:desktop")
                .apply {
                    setReferenceCounted(false)
                    acquire(12 * 60 * 60 * 1000L)
                }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, App.CHANNEL_CAST)
            .setSmallIcon(R.drawable.ic_computer)
            .setContentTitle(getString(R.string.desktop_notif_title))
            .setContentText(getString(R.string.desktop_notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private companion object {
        const val NOTIFICATION_ID = 3
    }
}
