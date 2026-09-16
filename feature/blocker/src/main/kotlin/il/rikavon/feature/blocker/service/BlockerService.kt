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
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.repo.BreathingGateRepository
import il.rikavon.core.data.repo.DailySummaryRepository
import il.rikavon.core.data.repo.DayRolloverUseCase
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.ScheduleRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.feature.blocker.R
import il.rikavon.feature.blocker.contact.CallReason
import il.rikavon.feature.blocker.contact.PetContact
import il.rikavon.feature.blocker.contact.PetContactNotifier
import il.rikavon.feature.blocker.contact.PetContactPolicy
import il.rikavon.feature.blocker.engine.BlockDecision
import il.rikavon.feature.blocker.engine.BlockerEvent
import il.rikavon.feature.blocker.engine.BlockerEvents
import il.rikavon.feature.blocker.engine.EnforcementEngine
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.blocker.engine.InstantBlockBus
import il.rikavon.feature.blocker.engine.InstantRequest
import il.rikavon.feature.blocker.engine.PollingPolicy
import il.rikavon.feature.blocker.overlay.OverlayController
import il.rikavon.feature.blocker.profile.FocusProfileManager
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.MascotVoice
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The enforcement loop. Foreground so the system keeps it alive; polls usage adaptively while the screen is
 * on, sleeps completely when it is off, and raises the block overlay the moment a limit or schedule applies.
 *
 * A block is hard: the blocked app is sent home the moment the block screen is up, so it stops running
 * behind it, and the screen stays until the user closes it. With the optional accessibility service the
 * app is sent home the instant its window appears and the screen is requested through [InstantBlockBus].
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

    @Inject lateinit var voice: MascotVoice

    @Inject lateinit var events: BlockerEvents

    @Inject lateinit var midnightAlarm: MidnightAlarmScheduler

    @Inject lateinit var installed: InstalledAppsSource

    @Inject lateinit var instant: InstantBlockBus

    @Inject lateinit var profile: FocusProfileManager

    @Inject lateinit var gate: BreathingGateRepository

    @Inject lateinit var contact: PetContactNotifier

    @Inject lateinit var score: FocusScoreProvider

    private val engine = EnforcementEngine()
    private val policy = PollingPolicy()
    private val contactPolicy = PetContactPolicy()
    private val screenOn = MutableStateFlow(true)
    private var suppressedPackage: String? = null
    private var suppressedUntil = 0L
    private val lastCallAt = mutableMapOf<String, Long>()

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
        lifecycleScope.launch {
            instant.requests.collect { request ->
                when (request) {
                    is InstantRequest.Block -> blockNow(request.packageName)
                    is InstantRequest.Gate -> showBreathing(request.packageName)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        instant.publish(emptySet())
        profile.releaseAll()
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
                instant.publish(emptySet())
                withContext(Dispatchers.IO) { profile.releaseAll() }
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
            val gated = if (current.breathingGateEnabled) gate.current() else emptySet()
            instant.publish(blockable, gated)
            // Inside the focus profile a block is a suspension: the icon greys out and the app cannot open.
            withContext(Dispatchers.IO) { profile.applySuspension(tracked(limitList, scheduleList), blockable) }
            maybeGate(snapshot.foregroundPackage, decision, gated)
            if (current.petMessagesEnabled || current.petCallsEnabled) {
                contactPolicy.evaluate(snapshot, limitList).forEach { reachOut(it, current) }
            }
            val interval =
                policy.intervalMillis(
                    screenOn = screenOn.value && snapshot.screenOn,
                    foregroundTracked = limitList.any { it.enabled && it.packageName == snapshot.foregroundPackage },
                    anyBlockable = blockable.isNotEmpty(),
                    maxUsageRatio = engine.maxUsageRatio(snapshot, limitList),
                    instantPath = permissions.hasAccessibility(),
                ) ?: PollingPolicy.NORMAL_MILLIS
            delay(interval)
        }
    }

    private fun tracked(limitList: List<AppLimit>, scheduleList: List<Schedule>): Set<String> =
        (
            limitList.filter { it.enabled }.map { it.packageName } +
                scheduleList.filter { it.enabled }.flatMap { it.packages }
        ).toSet()

    /**
     * The breathing pause: shown when a package whose limit just changed is in front and not blocked; dropped
     * again when the user leaves that app before sitting through it (the pause stays armed).
     */
    private suspend fun maybeGate(foreground: String?, decision: BlockDecision?, gated: Set<String>) {
        if (decision == null && foreground != null && foreground in gated) {
            showBreathing(foreground)
        } else if (overlay.kind == OverlayController.Kind.BREATHE && overlay.shownPackageName != foreground) {
            overlay.hideIf(OverlayController.Kind.BREATHE)
        }
    }

    private suspend fun showBreathing(packageName: String) {
        if (overlay.isShowing(packageName) || !permissions.state().overlay) return
        if (packageName !in gate.current()) return
        val skin = selectedMascot.current() ?: return
        val prefs = settings.current()
        overlay.showBreathing(
            OverlayController.BreatheRequest(
                packageName = packageName,
                skin = skin,
                appLabel = installed.label(packageName),
                seconds = AnimationSpecs.BREATHING_GATE_SECONDS,
                appearance = overlay.appearance(prefs.reduceMotion),
                onEnter = {
                    lifecycleScope.launch { gate.clear(packageName) }
                    overlay.hide()
                },
                onLeave = {
                    overlay.hide()
                    goHome()
                },
            ),
        )
    }

    /** Delivers one of the pet's messages or calls, honouring the two switches. */
    private suspend fun reachOut(item: PetContact, prefs: Settings) {
        val skin = selectedMascot.current() ?: return
        val language = UiLanguage.fromLocale(resources.configuration.locales)
        val label = installed.label(item.packageName)
        val stage = score.currentStage()
        when (item) {
            is PetContact.Message -> if (prefs.petMessagesEnabled) contact.message(skin, stage, item, label, language)
            is PetContact.Call -> if (prefs.petCallsEnabled) contact.call(skin, stage, item, label, language)
        }
    }

    /**
     * With calls on, a block is a phone call: the pet rings (the call screen comes up over the blocked app)
     * and the app is sent home. The loop can meet the same block several times while the ring is still up,
     * so one ring per package per cooldown; the app still goes home every time.
     */
    private suspend fun ringForBlock(packageName: String, skin: MascotSkin, language: String) {
        val now = time.nowMillis()
        if (now - (lastCallAt[packageName] ?: 0L) < CALL_COOLDOWN_MILLIS) return
        lastCallAt[packageName] = now
        val call = PetContact.Call(packageName, CallReason.BLOCKED, minutesLeft = 0)
        contact.call(skin, score.currentStage(), call, installed.label(packageName), language)
    }

    /** The accessibility service already sent [packageName] home; put the block screen up right away. */
    private suspend fun blockNow(packageName: String) {
        if (!settings.current().trackingEnabled) return
        val decision =
            engine.evaluate(usage.refresh(), limits.all(), schedules.all(), time.now(), packageName = packageName)
        handleDecision(decision, ignoreSuppression = true)
    }

    /**
     * Shows the block screen for a decision. A null decision no longer hides it: the blocked app was sent
     * home when the screen came up, so the launcher is what usage stats report next, and the screen must
     * stay until the user closes it (or the screen turns off / tracking stops).
     */
    private suspend fun handleDecision(decision: BlockDecision?, ignoreSuppression: Boolean = false) {
        if (decision == null) return
        val suppressed =
            !ignoreSuppression && decision.packageName == suppressedPackage && time.nowMillis() < suppressedUntil
        if (suppressed || overlay.isShowing(decision.packageName) || !permissions.state().overlay) return

        val skin = selectedMascot.current() ?: return
        val prefs = settings.current()
        val language = UiLanguage.fromLocale(resources.configuration.locales)
        val message = texts.blockMessage(skin, time.localTime().hour, language)
        summaries.recordBlock(decision.packageName, decision.reason)
        events.emit(BlockerEvent.Blocked(decision.packageName))
        if (prefs.petCallsEnabled) {
            ringForBlock(decision.packageName, skin, language)
            goHome()
            return
        }
        if (prefs.soundsEnabled && prefs.voiceEnabled) voice.speak(message, skin.voice, language)
        overlay.show(
            OverlayController.Request(
                decision = decision,
                skin = skin,
                appLabel = installed.label(decision.packageName),
                limitMinutes = limits.byPackage(decision.packageName)?.limitMinutes ?: 0,
                message = message,
                appearance = overlay.appearance(prefs.reduceMotion),
                onClose = { dismissAndGoHome(decision.packageName) },
                // Hard block: the app leaves the foreground the moment the screen covers it, so nothing keeps
                // playing behind the overlay and reopening it starts the block again.
                onShown = ::goHome,
            ),
        )
    }

    private fun dismissAndGoHome(packageName: String) {
        suppressedPackage = packageName
        suppressedUntil = time.nowMillis() + SUPPRESS_AFTER_CLOSE_MILLIS
        overlay.hide()
        goHome()
    }

    /** Allowed from the service because the app holds SYSTEM_ALERT_WINDOW and has a window on screen. */
    private fun goHome() {
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
        private const val CALL_COOLDOWN_MILLIS = 15_000L

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
