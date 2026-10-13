package com.dtubugk.shakelight

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.preference.PreferenceManager
import com.dtubugk.shakelight.core.TorchController

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        TorchController.init(this)

        val channel = NotificationChannel(
            CHANNEL_SHAKE,
            getString(R.string.channel_shake),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        val castChannel = NotificationChannel(
            CHANNEL_CAST,
            getString(R.string.channel_cast),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, castChannel))
    }

    companion object {
        const val CHANNEL_SHAKE = "shake_service"
        const val CHANNEL_CAST = "screen_cast"
    }
}
