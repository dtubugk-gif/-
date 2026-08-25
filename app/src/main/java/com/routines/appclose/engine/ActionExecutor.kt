package com.routines.appclose.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.routines.appclose.MainActivity
import com.routines.appclose.R
import com.routines.appclose.data.ActionType
import com.routines.appclose.data.RuleAction

/** מריץ את הפעולות של כלל. כל פעולה עטופה כך שכשל באחת לא עוצר את השאר. */
class ActionExecutor(private val context: Context) {

    fun execute(actions: List<RuleAction>) {
        for (action in actions) {
            try {
                executeOne(action)
            } catch (t: Throwable) {
                Log.e(TAG, "Action ${action.type} failed", t)
            }
        }
    }

    private fun executeOne(action: RuleAction) {
        when (action.type) {
            ActionType.SOUND_MODE -> setSoundMode(action.intValue ?: 2)
            ActionType.MEDIA_VOLUME -> setMediaVolume(action.intValue ?: 50)
            ActionType.DND -> setDnd(action.intValue == 1)
            ActionType.OPEN_APP -> openApp(action.stringValue)
            ActionType.NOTIFY -> notify(action.stringValue ?: "")
            ActionType.BRIGHTNESS -> setBrightness(action.intValue ?: 50)
            ActionType.WIFI_PANEL -> openWifiPanel()
            ActionType.VIBRATE -> vibrate()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
    }

    private fun setSoundMode(mode: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // מעבר למצב שקט דורש גישת "נא לא להפריע"; בלעדיה אנדרואיד זורק SecurityException.
        audio.ringerMode = when (mode) {
            0 -> AudioManager.RINGER_MODE_SILENT
            1 -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
    }

    private fun setMediaVolume(percent: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = (max * percent.coerceIn(0, 100)) / 100
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    private fun setDnd(enable: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND requested but policy access not granted")
            return
        }
        nm.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    private fun openApp(pkg: String?) {
        if (pkg.isNullOrBlank()) return
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: run {
            Log.w(TAG, "No launch intent for $pkg")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun notify(message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notification_channel_desc) }
        nm.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // אייקון מתוך האפליקציה עצמה — משאב מערכת עלול להקריס את התהליך בחלק מהמכשירים.
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_routine)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(message.hashCode(), notification)
    }

    private fun setBrightness(percent: Int) {
        if (!Settings.System.canWrite(context)) {
            Log.w(TAG, "Brightness requested but WRITE_SETTINGS not granted")
            return
        }
        val resolver = context.contentResolver
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (255 * percent.coerceIn(0, 100)) / 100,
        )
    }

    private fun openWifiPanel() {
        // מאנדרואיד 10 אי-אפשר להדליק/לכבות Wi-Fi ישירות — פותחים את הפאנל של המערכת.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        private const val TAG = "ActionExecutor"
        private const val CHANNEL_ID = "routine_reminders"
    }
}
