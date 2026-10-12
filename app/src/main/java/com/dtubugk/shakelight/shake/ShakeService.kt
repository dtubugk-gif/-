package com.dtubugk.shakelight.shake

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dtubugk.shakelight.App
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.core.Haptics
import com.dtubugk.shakelight.core.Prefs
import com.dtubugk.shakelight.core.TorchController
import com.dtubugk.shakelight.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** Keeps listening for shakes in the background (also with the screen off) and toggles the flash. */
class ShakeService : LifecycleService(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var sensorManager: SensorManager
    private val detector = ShakeDetector { toggleTorch(fromShake = true) }

    private var pocketGuard = true
    private var inPocket = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoOffJob: Job? = null

    private val torchListener: (Boolean) -> Unit = { on ->
        if (!on) autoOffJob?.cancel()
        updateNotification()
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        prefs = Prefs.get(this)
        sensorManager = getSystemService(SensorManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        detector.isSuppressed = { pocketGuard && inPocket }
        applySettings()
        prefs.registerOnSharedPreferenceChangeListener(this)
        TorchController.addListener(torchListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE_TORCH -> toggleTorch(fromShake = false)
            ACTION_STOP -> {
                prefs.edit { putBoolean(Prefs.SHAKE_ENABLED, false) }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        sensorManager.unregisterListener(this)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        TorchController.removeListener(torchListener)
        releaseWakeLock()
        super.onDestroy()
    }

    private fun applySettings() {
        detector.thresholdG = ShakeDetector.thresholdFor(prefs.getInt(Prefs.SENSITIVITY, 5))
        detector.requiredPeaks = prefs.getInt(Prefs.SHAKE_COUNT, 3)
        detector.reset()
        pocketGuard = prefs.getBoolean(Prefs.POCKET_GUARD, true)
        inPocket = false

        sensorManager.unregisterListener(this)
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (pocketGuard) {
            sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        // Most phones stop delivering accelerometer data when the CPU sleeps, so a partial
        // wake lock is what makes "shake with the screen off" actually work.
        if (prefs.getBoolean(Prefs.SCREEN_OFF, true)) acquireWakeLock() else releaseWakeLock()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> detector.onSample(
                event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000,
            )
            Sensor.TYPE_PROXIMITY -> {
                val nearLimit = min(5f, event.sensor.maximumRange)
                inPocket = event.values[0] < nearLimit
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key in Prefs.SHAKE_TUNING) applySettings()
    }

    private fun toggleTorch(fromShake: Boolean) {
        val turnOn = !TorchController.isOn
        if (!TorchController.set(turnOn, Prefs.torchLevel(this))) return
        if (fromShake && prefs.getBoolean(Prefs.VIBRATE, true)) Haptics.torchFeedback(this, turnOn)

        autoOffJob?.cancel()
        val minutes = Prefs.autoOffMinutes(this)
        if (turnOn && minutes > 0) {
            autoOffJob = lifecycleScope.launch {
                delay(minutes * 60_000L)
                TorchController.set(false)
            }
        }
    }

    @SuppressLint("WakelockTimeout") // Held on purpose for as long as the user keeps shake detection on.
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShakeLight:shake")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, ShakeService::class.java).setAction(ACTION_TOGGLE_TORCH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, ShakeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (TorchController.isOn) R.string.notif_torch_on else R.string.notif_torch_off

        return NotificationCompat.Builder(this, App.CHANNEL_SHAKE)
            .setSmallIcon(R.drawable.ic_flashlight)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_toggle_torch), toggle)
            .addAction(0, getString(R.string.action_stop), stop)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE_TORCH = "com.dtubugk.shakelight.TOGGLE_TORCH"
        private const val ACTION_STOP = "com.dtubugk.shakelight.STOP"

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ShakeService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShakeService::class.java))
        }
    }
}
