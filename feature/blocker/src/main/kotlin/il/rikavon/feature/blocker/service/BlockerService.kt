package il.rikavon.feature.blocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.repo.DailySummaryRepository
import il.rikavon.core.data.repo.DayRolloverUseCase
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.ScheduleRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.feature.blocker.R
import il.rikavon.feature.blocker.engine.BlockDecision
import il.rikavon.feature.blocker.engine.BlockerEvent
import il.rikavon.feature.blocker.engine.BlockerEvents
import il.rikavon.feature.blocker.engine.EnforcementEngine
import il.rikavon.feature.blocker.engine.PollingPolicy
import il.rikavon.feature.blocker.overlay.OverlayController
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The enforcement loop. Foreground so the system keeps it alive; polls usage adaptively while the screen is
 * on, sleeps completely when it is off, and raises the block overlay the moment a limit or schedule applies.
 */
@AndroidEntryPoint
class BlockerService : LifecycleService() {
    @Inject lateinit var usage: UsageRepository

    @Inject lateinit var limits: LimitsRepository

    @Inject lateinit var schedules: ScheduleRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var summaries: DailySummaryRepository

    @Inject lateinit var rollover: DayRolloverUseCase

    @Inject lateinit var permissions: PermissionChecker

    @Inject lateinit var time: TimeSource

    @Inject lateinit var overlay: OverlayController

    @Inject lateinit var selectedMascot: SelectedMascot

    @Inject lateinit var texts: MascotTexts

    @Inject lateinit var events: BlockerEvents

    @Inject lateinit var midnightAlarm: MidnightAlarmScheduler

    private val engine = EnforcementEngine()
    private val policy = PollingPolicy()
    private val screenOn = MutableStateFlow(true)
    private var suppressedPackage: String? = null
    private var suppressedUntil = 0L

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> screenOn.value = false
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        usage.markScreenOn()
                        screenOn.value = true
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        midnightAlarm.schedule()
        events.emit(BlockerEvent.ServiceStarted)
        lifecycleScope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        overlay.hide()
        super.onDestroy()
    }

    private suspend fun loop() {
        while (lifecycleScope.isActive) {
            if (!screenOn.value) {
                overlay.hide()
                usage.persistToday(force = true)
                screenOn.first { it }
            }
            val current = settings.current()
            if (!current.trackingEnabled || !permissions.hasUsageAccess()) {
                overlay.hide()
                delay(PollingPolicy.NORMAL_MILLIS)
                continue
            }
            rollover
                .runIfDue()
                .takeIf {
                    it.rolledOver
                }?.let { events.emit(BlockerEvent.DayRolledOver(it.newlyUnlocked)) }

            val snapshot = usage.refresh()
            val limitList = limits.all()
            val scheduleList = schedules.all()
            val now = time.now()
            val decision = engine.evaluate(snapshot, limitList, scheduleList, now)
            handleDecision(decision)
            usage.persistToday()
            events.emit(BlockerEvent.UsageRefreshed)

            val blockable = engine.blockedPackages(snapshot, limitList, scheduleList, now)
            val interval =
                policy.intervalMillis(
                    screenOn = screenOn.value && snapshot.screenOn,
                    foregroundTracked = limitList.any { it.enabled && it.packageName == snapshot.foregroundPackage },
                    anyBlockable = blockable.isNotEmpty(),
                    maxUsageRatio = engine.maxUsageRatio(snapshot, limitList),
                ) ?: PollingPolicy.NORMAL_MILLIS
            delay(interval)
        }
    }

    private suspend fun handleDecision(decision: BlockDecision?) {
        if (decision == null) {
            overlay.hide()
            return
        }
        val suppressed = decision.packageName == suppressedPackage && time.nowMillis() < suppressedUntil
        if (suppressed || overlay.isShowing(decision.packageName) || !permissions.state().overlay) return

        val skin = selectedMascot.current() ?: return
        val prefs = settings.current()
        val language = UiLanguage.fromLocale(resources.configuration.locales)
        val message = texts.blockMessage(skin, time.localTime().hour, language)
        summaries.recordBlock(decision.packageName, decision.reason)
        events.emit(BlockerEvent.Blocked(decision.packageName))
        overlay.show(
            decision = decision,
            skin = skin,
            message = message,
            appearance = overlay.appearance(prefs.reduceMotion, prefs.dynamicColor),
            onClose = { dismissAndGoHome(decision.packageName) },
        )
    }

    private fun dismissAndGoHome(packageName: String) {
        suppressedPackage = packageName
        suppressedUntil = time.nowMillis() + SUPPRESS_AFTER_CLOSE_MILLIS
        overlay.hide()
        val home =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.service_channel_description)
                setShowBadge(false)
            },
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending =
            launch?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        val notification: Notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_rikavon)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setContentIntent(pending)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val CHANNEL_ID = "rikavon_service"
        private const val NOTIFICATION_ID = 1001
        private const val SUPPRESS_AFTER_CLOSE_MILLIS = 4_000L

        fun start(context: Context) {
            val intent = Intent(context, BlockerService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockerService::class.java))
        }
    }
}
