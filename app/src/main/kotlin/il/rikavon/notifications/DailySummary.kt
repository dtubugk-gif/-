package il.rikavon.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.MainActivity
import il.rikavon.R
import il.rikavon.core.data.di.ApplicationScope
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.time.TimeSource
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.render.MascotBitmapRenderer
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One optional evening notification in the selected mascot's voice. Never more than one a day,
 * never a "come back" nudge.
 */
@Singleton
class DailySummaryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {
    suspend fun reschedule() {
        val prefs = settings.current()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent()
        if (!prefs.dailySummaryEnabled) {
            alarmManager.cancel(pending)
            return
        }
        val now = time.now()
        var next = now.toLocalDate().atStartOfDay(time.zone).plusHours(prefs.dailySummaryHour.toLong())
        if (!next.isAfter(now)) next = next.plusDays(1)
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
        }
    }

    private fun pendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailySummaryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val REQUEST_CODE = 5151
    }
}

@Singleton
class DailySummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val permissions: PermissionChecker,
    private val selectedMascot: SelectedMascot,
    private val scoreProvider: FocusScoreProvider,
    private val texts: MascotTexts,
    private val renderer: MascotBitmapRenderer,
) {
    suspend fun notifyIfEnabled() {
        val prefs = settings.current()
        if (!prefs.dailySummaryEnabled || !permissions.hasNotifications()) return
        val skin = selectedMascot.current() ?: return
        val stage = MascotStage.fromScore(scoreProvider.score.value.total)
        val language = UiLanguage.fromLocale(context.resources.configuration.locales)
        val name = skin.name.resolve(language) ?: skin.id
        val body = texts.summary(skin, stage, language)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.summary_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.summary_channel_description)
            },
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(il.rikavon.feature.blocker.R.drawable.ic_stat_rikavon)
                .setLargeIcon(renderer.render(skin, stage, LARGE_ICON_PX))
                .setContentTitle(context.getString(R.string.summary_notification_title, name))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val CHANNEL_ID = "rikavon_summary"
        private const val NOTIFICATION_ID = 2002
        private const val LARGE_ICON_PX = 192
    }
}

@AndroidEntryPoint
class DailySummaryReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: DailySummaryNotifier

    @Inject lateinit var scheduler: DailySummaryScheduler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        scope.launch {
            try {
                notifier.notifyIfEnabled()
                scheduler.reschedule()
            } finally {
                result.finish()
            }
        }
    }
}
