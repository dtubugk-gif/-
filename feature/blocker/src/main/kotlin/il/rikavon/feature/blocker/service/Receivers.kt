package il.rikavon.feature.blocker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.di.ApplicationScope
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.repo.DayRolloverUseCase
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.feature.blocker.engine.BlockerEvent
import il.rikavon.feature.blocker.engine.BlockerEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Decides whether the enforcement service should be running and starts it. */
@Singleton
class ServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val permissions: PermissionChecker,
) {
    suspend fun startIfConfigured(): Boolean {
        val prefs = settings.current()
        val shouldRun = prefs.onboardingDone && prefs.trackingEnabled && permissions.hasUsageAccess()
        if (shouldRun) BlockerService.start(context) else BlockerService.stop(context)
        return shouldRun
    }
}

/** Exact alarm at the next local midnight; correctness never depends on it, freshness does. */
@Singleton
class MidnightAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeSource,
    private val permissions: PermissionChecker,
) {
    fun schedule() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at =
            time
                .now()
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(time.zone)
                .toInstant()
                .toEpochMilli() + SLACK_MILLIS
        val pending = pendingIntent()
        runCatching {
            if (permissions.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, MidnightReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val REQUEST_CODE = 4242
        private const val SLACK_MILLIS = 1_500L
    }
}

@AndroidEntryPoint
class MidnightReceiver : BroadcastReceiver() {
    @Inject lateinit var rollover: DayRolloverUseCase

    @Inject lateinit var scheduler: MidnightAlarmScheduler

    @Inject lateinit var events: BlockerEvents

    @Inject lateinit var starter: ServiceStarter

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        scope.launch {
            try {
                val outcome = rollover.runIfDue()
                events.emit(BlockerEvent.DayRolledOver(outcome.newlyUnlocked))
                scheduler.schedule()
                starter.startIfConfigured()
            } finally {
                result.finish()
            }
        }
    }
}

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var starter: ServiceStarter

    @Inject lateinit var scheduler: MidnightAlarmScheduler

    @Inject lateinit var rollover: DayRolloverUseCase

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val accepted = intent.action in ACTIONS
        if (!accepted) return
        val result = goAsync()
        scope.launch {
            try {
                rollover.runIfDue()
                scheduler.schedule()
                starter.startIfConfigured()
                ServiceReviverWorker.enqueue(context)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        private val ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON",
            ) +
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.N
                ) {
                    setOf(Intent.ACTION_LOCKED_BOOT_COMPLETED)
                } else {
                    emptySet()
                }
    }
}
