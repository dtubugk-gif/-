package il.rikavon.screenshots

import android.Manifest
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.PowerManager
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowUsageStatsManager

/** The fake phone every screenshot is taken on: granted permissions, a few installed apps, a day of usage. */
internal class ShotSeeds(
    private val context: Context,
    private val settings: SettingsRepository,
    private val limits: LimitsRepository,
    private val usage: UsageRepository,
) {
    fun grantEverything() {
        val app = context as Application
        ShadowSettings.setCanDrawOverlays(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .setIgnoringBatteryOptimizations(context.packageName, true)
    }

    fun installFakeApps() = FAKE_APPS.forEach { (pkg, label) -> installFakeApp(pkg, label) }

    /** Past onboarding, following the device language, with a streak to show. */
    fun base() =
        runBlocking {
            settings.setLanguage(AppLanguage.SYSTEM)
            settings.setOnboardingDone(true)
            settings.setStreak(current = STREAK, best = BEST_STREAK)
        }

    /** Three limited apps with a realistic day of sessions behind them. */
    fun limitsAndUsage() =
        runBlocking {
            limits.save(AppLimit(INSTAGRAM, INSTAGRAM_LIMIT, fullBlock = false, enabled = true, createdAt = 0L))
            limits.save(AppLimit(TIKTOK, TIKTOK_LIMIT, fullBlock = false, enabled = true, createdAt = 0L))
            limits.save(AppLimit(YOUTUBE, YOUTUBE_LIMIT, fullBlock = false, enabled = true, createdAt = 0L))
            val stats = shadowOf(context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
            val now = System.currentTimeMillis()

            fun session(pkg: String, startAgoMinutes: Long, minutes: Long) {
                val start = now - startAgoMinutes * MINUTE_MILLIS
                stats.addEvent(event(pkg, start, UsageEvents.Event.ACTIVITY_RESUMED))
                stats.addEvent(event(pkg, start + minutes * MINUTE_MILLIS, UsageEvents.Event.ACTIVITY_PAUSED))
            }
            session(INSTAGRAM, startAgoMinutes = 200, minutes = 30)
            session(INSTAGRAM, startAgoMinutes = 120, minutes = 18)
            session(TIKTOK, startAgoMinutes = 300, minutes = 40)
            session(TIKTOK, startAgoMinutes = 90, minutes = 30)
            session(YOUTUBE, startAgoMinutes = 60, minutes = 12)
            usage.refresh()
        }

    private fun event(pkg: String, timestamp: Long, type: Int): UsageEvents.Event =
        ShadowUsageStatsManager.EventBuilder
            .buildEvent()
            .setPackage(pkg)
            .setTimeStamp(timestamp)
            .setEventType(type)
            .build()

    private fun installFakeApp(pkg: String, label: String) {
        val pm = shadowOf(context.packageManager)
        val appInfo =
            ApplicationInfo().apply {
                packageName = pkg
                name = label
                nonLocalizedLabel = label
                flags = 0
            }
        pm.installPackage(
            PackageInfo().apply {
                packageName = pkg
                applicationInfo = appInfo
            },
        )
        val activity =
            ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
                applicationInfo = appInfo
                nonLocalizedLabel = label
            }
        val component = ComponentName(pkg, activity.name)
        pm.addOrUpdateActivity(activity)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val YOUTUBE = "com.google.android.youtube"
        const val INSTAGRAM_LIMIT = 60
        const val TIKTOK_LIMIT = 60
        const val YOUTUBE_LIMIT = 90
        const val STREAK = 4
        const val BEST_STREAK = 9
        private const val MINUTE_MILLIS = 60_000L
        private val FAKE_APPS =
            listOf(
                INSTAGRAM to "Instagram",
                TIKTOK to "TikTok",
                YOUTUBE to "YouTube",
                "com.whatsapp" to "WhatsApp",
                "com.twitter.android" to "X",
                "com.reddit.frontpage" to "Reddit",
            )
    }
}
