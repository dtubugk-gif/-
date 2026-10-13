package com.dtubugk.shakelight.cast

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.dtubugk.shakelight.App
import com.dtubugk.shakelight.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps screen projection to the computer running while the user uses other apps. */
class ScreenCastService : Service() {

    sealed interface State {
        data object Idle : State
        data class Connecting(val target: String) : State
        data class Casting(val target: String) : State
        data class Failed(val message: Int) : State
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var session: CastSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        val host = intent?.getStringExtra(EXTRA_HOST)
        if (data == null || host == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val name = intent.getStringExtra(EXTRA_NAME) ?: host

        // Android requires the foreground notification before the projection is created.
        val notification = buildNotification(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        endCurrent()
        val mp = try {
            getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, 0), data)
        } catch (e: Exception) {
            null
        }
        if (mp == null) {
            finish(State.Failed(R.string.cast_error_permission))
            return START_NOT_STICKY
        }
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            // The user stopped sharing from the system (status bar chip, lock screen…).
            override fun onStop() {
                if (projection === mp) finish(State.Idle)
            }
        }, main)

        _state.value = State.Connecting(name)
        lateinit var newSession: CastSession
        newSession = CastSession(
            context = this,
            projection = mp,
            host = host,
            port = intent.getIntExtra(EXTRA_PORT, CastProtocol.STREAM_PORT),
            sendAudio = intent.getBooleanExtra(EXTRA_AUDIO, true),
            onConnected = { main.post { _state.value = State.Casting(name) } },
            onEnded = { error ->
                main.post {
                    // Ignore a session that was already replaced or stopped.
                    if (session === newSession) finish(if (error == null) State.Idle else State.Failed(error))
                }
            },
        )
        session = newSession
        newSession.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        endCurrent()
        if (_state.value !is State.Failed) _state.value = State.Idle
        super.onDestroy()
    }

    private fun endCurrent() {
        session?.stop()
        session = null
        val mp = projection
        projection = null
        runCatching { mp?.stop() }
    }

    private fun finish(newState: State) {
        endCurrent()
        _state.value = newState
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(target: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, CastToPcActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenCastService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, App.CHANNEL_CAST)
            .setSmallIcon(R.drawable.ic_computer)
            .setContentTitle(getString(R.string.cast_notif_title, target))
            .setContentText(getString(R.string.cast_notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.cast_stop), stop)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP = "com.dtubugk.shakelight.STOP_CAST"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_AUDIO = "audio"

        private val _state = MutableStateFlow<State>(State.Idle)
        val state: StateFlow<State> = _state.asStateFlow()

        fun start(context: Context, resultCode: Int, resultData: Intent, receiver: ReceiverDiscovery.Receiver, audio: Boolean) {
            val intent = Intent(context, ScreenCastService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
                .putExtra(EXTRA_HOST, receiver.host)
                .putExtra(EXTRA_PORT, receiver.port)
                .putExtra(EXTRA_NAME, receiver.name)
                .putExtra(EXTRA_AUDIO, audio)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCastService::class.java))
        }
    }
}
