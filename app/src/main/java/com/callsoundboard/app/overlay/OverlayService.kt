package com.callsoundboard.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.callsoundboard.app.R
import com.callsoundboard.app.audio.SoundPlayer
import com.callsoundboard.app.audio.SpeakerRouter
import com.callsoundboard.app.data.SoundRepository
import com.callsoundboard.app.databinding.OverlayLayoutBinding
import kotlin.math.abs

/**
 * Foreground service that shows a draggable floating bubble on top of the in-call
 * screen and plays clips into the call via the acoustic (loudspeaker) path.
 *
 * Lifecycle: the manifest PhoneStateReceiver bootstraps this service on OFFHOOK;
 * the service registers a TelephonyCallback/PhoneStateListener and stops itself
 * when the call goes IDLE. It can also be shown manually from MainActivity.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var binding: OverlayLayoutBinding? = null
    private var params: WindowManager.LayoutParams? = null
    private var viewAdded = false

    private lateinit var repository: SoundRepository

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null

    private var fromCall = false
    private var sawActiveCall = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = SoundRepository(this)
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (intent?.getBooleanExtra(EXTRA_FROM_CALL, false) == true) {
            fromCall = true
        }
        registerCallStateTracking()
        return START_NOT_STICKY
    }

    // ---- Foreground notification -------------------------------------------

    private fun startInForeground() {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_speaker)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    // ---- Overlay window -----------------------------------------------------

    private fun addOverlay() {
        if (viewAdded) return
        val themed = ContextThemeWrapper(this, R.style.Theme_CallSoundboard)
        val b = OverlayLayoutBinding.inflate(LayoutInflater.from(themed))
        binding = b

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }
        params = lp

        setupBubbleTouch(b)
        b.btnOverlayStop.setOnClickListener { SoundPlayer.stop() }
        b.btnOverlayClose.setOnClickListener { stopSelf() }

        try {
            windowManager.addView(b.root, lp)
            viewAdded = true
        } catch (_: Exception) {
            // Overlay permission may have been revoked; nothing to show.
            stopSelf()
        }
    }

    private fun setupBubbleTouch(b: OverlayLayoutBinding) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        b.bubbleButton.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) moved = true
                    lp.x = initialX + dx
                    lp.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(b.root, lp)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePanel() {
        val b = binding ?: return
        if (b.panel.visibility == View.VISIBLE) {
            b.panel.visibility = View.GONE
        } else {
            populateClips(b)
            b.panel.visibility = View.VISIBLE
        }
    }

    private fun populateClips(b: OverlayLayoutBinding) {
        val container = b.containerClips
        container.removeAllViews()
        val themed = ContextThemeWrapper(this, R.style.Theme_CallSoundboard)
        val clips = repository.getAll()

        if (clips.isEmpty()) {
            val empty = Button(themed).apply {
                text = getString(R.string.no_clips)
                isEnabled = false
            }
            container.addView(empty, rowParams())
            return
        }

        clips.forEach { clip ->
            val btn = Button(themed).apply {
                text = clip.label
                isAllCaps = false
                setOnClickListener {
                    SpeakerRouter.enableSpeaker(this@OverlayService)
                    SoundPlayer.play(this@OverlayService, Uri.parse(clip.uri))
                }
            }
            container.addView(btn, rowParams())
        }
    }

    private fun rowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    // ---- Call-state tracking ------------------------------------------------

    private fun registerCallStateTracking() {
        if (telephonyManager == null) {
            telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        }
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (telephonyCallback == null) {
                    val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) = handleCallState(state)
                    }
                    telephonyCallback = cb
                    tm.registerTelephonyCallback(mainExecutor, cb)
                }
            } else {
                if (phoneStateListener == null) {
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                            handleCallState(state)
                    }
                    phoneStateListener = listener
                    @Suppress("DEPRECATION")
                    tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                }
            }
        } catch (_: SecurityException) {
            // READ_PHONE_STATE not granted: no auto-dismiss; user closes manually.
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK,
            TelephonyManager.CALL_STATE_RINGING -> sawActiveCall = true
            TelephonyManager.CALL_STATE_IDLE -> {
                if (fromCall || sawActiveCall) stopSelf()
            }
        }
    }

    private fun unregisterCallStateTracking() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { tm.unregisterTelephonyCallback(it) }
            } else {
                phoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    tm.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (_: Exception) {
        }
    }

    // ---- Teardown -----------------------------------------------------------

    override fun onDestroy() {
        super.onDestroy()
        unregisterCallStateTracking()
        SoundPlayer.stop()
        SpeakerRouter.clearSpeaker(this)
        binding?.let {
            try {
                if (viewAdded) windowManager.removeView(it.root)
            } catch (_: Exception) {
            }
        }
        binding = null
        viewAdded = false
    }

    companion object {
        const val EXTRA_FROM_CALL = "from_call"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIF_ID = 1001
        private const val TOUCH_SLOP = 12
    }
}
