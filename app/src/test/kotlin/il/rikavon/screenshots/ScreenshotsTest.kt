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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import il.rikavon.MainActivity
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.ScheduleRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.feature.blocker.engine.BlockDecision
import il.rikavon.feature.blocker.overlay.BlockOverlayContent
import il.rikavon.feature.mascot.model.HourBucket
import il.rikavon.feature.mascot.registry.MascotRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowUsageStatsManager
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * Renders the real app (Hilt graph, Room, DataStore, Lottie) inside Robolectric's native graphics mode and
 * writes PNGs to `docs/screenshots`. Run on demand: `./gradlew :app:testDebugUnitTest -Pscreenshots=true`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [SDK], qualifiers = PHONE_EN)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var limits: LimitsRepository

    @Inject lateinit var schedules: ScheduleRepository

    @Inject lateinit var registry: MascotRegistry

    @Inject lateinit var usage: UsageRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        grantEverything()
        FAKE_APPS.forEach { (pkg, label) -> installFakeApp(pkg, label) }
        compose.mainClock.autoAdvance = false
    }

    // ---- English ------------------------------------------------------------------------------

    @Test
    fun onboarding() {
        runBlocking { settings.setLanguage(AppLanguage.SYSTEM) }
        launchMain(string("onboarding_next")).use {
            capture("01_onboarding_welcome")
            click(string("onboarding_next"))
            waitFor(string("onboarding_usage_title"))
            capture("02_onboarding_usage")
            click(string("onboarding_next"))
            waitFor(string("onboarding_overlay_title"))
            click(string("onboarding_next"))
            waitFor(string("onboarding_instant_title"))
            capture("19_onboarding_instant")
        }
    }

    @Test
    fun homeEmpty() {
        seedBase()
        launchMain(string("home_empty_title")).use {
            capture("03_home_empty")
            // The big plus circle is the obvious target on a fresh install; it must open the picker.
            clickDescribed(string("home_empty_title"))
            waitFor("WhatsApp")
        }
    }

    @Test
    fun mainFlow() {
        seedBase()
        seedLimitsAndUsage()
        launchMain("Instagram").use {
            capture("04_home")
            click("Instagram")
            waitFor(string("limit_save"))
            capture("05_limit_editor")
            it.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            waitFor(string("nav_gallery"))
            click(string("nav_gallery"))
            waitFor(string("gallery_selected_now"))
            settle(LONG_SETTLE)
            capture("06_gallery")
            click(string("nav_stats"))
            waitFor(string("stats_per_app"))
            settle(LONG_SETTLE)
            capture("07_stats")
            click(string("nav_settings"))
            waitFor(string("settings_pet"))
            capture("08_settings")
            scrollTo(string("settings_score"))
            click(string("settings_score"))
            waitFor(string("score_total_label"))
            capture("09_score_explainer")
            it.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            waitFor(string("settings_pet"))
            scrollTo(string("settings_premium"))
            click(string("settings_premium"))
            waitFor(string("premium_buy"))
            capture("10_premium")
        }
    }

    @Test
    fun pickerAndSchedules() {
        seedBase()
        seedLimitsAndUsage()
        runBlocking {
            schedules.save(
                Schedule(
                    id = Schedule.NEW_ID,
                    name = "Sleep",
                    type = ScheduleType.SLEEP,
                    days = DayOfWeek.entries.toSet(),
                    startMinute = SLEEP_START,
                    endMinute = SLEEP_END,
                    packages = setOf(INSTAGRAM, TIKTOK),
                    enabled = true,
                ),
            )
        }
        launchMain("Instagram").use {
            scrollTo(string("home_add_app"))
            click(string("home_add_app"))
            waitFor("WhatsApp")
            capture("11_app_picker")
            it.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            waitFor(string("home_schedules_row"))
            scrollTo(string("home_schedules_row"))
            click(string("home_schedules_row"), substring = true)
            waitFor("Sleep")
            capture("12_schedules")
            click("Sleep")
            waitFor(string("schedule_save"))
            capture("13_schedule_editor")
        }
    }

    @Test
    fun achievements() {
        seedBase()
        launchMain(string("home_empty_title")).use {
            click(string("home_streak_pill", STREAK))
            waitFor(string("achievements_streak_best"))
            capture("14_achievements")
        }
    }

    @Test
    fun blockScreen() {
        val skin = runBlocking { registry.load() }.first { it.id == "potato" }
        val message =
            skin.blockMessages
                .resolve("en")
                ?.get(HourBucket.DAY)
                ?.firstOrNull()
                .orEmpty()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    BlockOverlayContent(
                        decision =
                            BlockDecision(
                                packageName = TIKTOK,
                                reason = BlockReason.LIMIT_REACHED,
                                retryAtMillis = System.currentTimeMillis() + RETRY_MILLIS,
                                scheduleId = null,
                            ),
                        skin = skin,
                        appLabel = "TikTok",
                        limitMinutes = TIKTOK_LIMIT,
                        message = message,
                        reducedMotion = false,
                        onClose = {},
                    )
                }
            }
            settle(LONG_SETTLE)
            captureFrom(scenario, "15_block_screen")
        }
    }

    // ---- Hebrew -------------------------------------------------------------------------------

    @Test
    @Config(qualifiers = PHONE_HE)
    fun hebrewHome() {
        seedBase()
        seedLimitsAndUsage()
        launchMain("Instagram").use {
            capture("16_home_he")
            click(string("nav_settings"))
            waitFor(string("settings_pet"))
            capture("17_settings_he")
        }
    }

    @Test
    @Config(qualifiers = PHONE_HE)
    fun hebrewOnboarding() {
        runBlocking { settings.setLanguage(AppLanguage.SYSTEM) }
        launchMain(string("onboarding_next")).use { capture("18_onboarding_he") }
    }

    // ---- Seeding ------------------------------------------------------------------------------

    private fun seedBase() =
        runBlocking {
            settings.setLanguage(AppLanguage.SYSTEM)
            settings.setOnboardingDone(true)
            settings.setStreak(current = STREAK, best = BEST_STREAK)
        }

    private fun seedLimitsAndUsage() =
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

    private fun grantEverything() {
        val app = context as Application
        ShadowSettings.setCanDrawOverlays(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        shadowOf(context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .setIgnoringBatteryOptimizations(context.packageName, true)
    }

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

    // ---- Harness ------------------------------------------------------------------------------

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launchMain(waitText: String): ActivityScenario<MainActivity> {
        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        waitFor(waitText)
        settle(LONG_SETTLE)
        return launched
    }

    /** Polls until a node with [text] exists (background loads, navigation), then lets animations settle. */
    private fun waitFor(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MILLIS, substringOk: Boolean = true) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(REAL_SLEEP_MILLIS)
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
            if (exists(text, substringOk)) {
                settle()
                return
            }
        }
        scenario?.let {
            val ascii = text.filter { c -> c in 'a'..'z' || c in 'A'..'Z' || c.isDigit() }.take(TIMEOUT_NAME_CHARS)
            captureFrom(it, "debug_timeout_" + ascii.ifEmpty { text.hashCode().toUInt().toString(radix = 16) })
        }
        println("TIMEOUT waiting for '$text' codepoints=" + text.codePoints().toArray().joinToString())
        println("TIMEOUT tree: " + runCatching { compose.onRoot().printToString() }.getOrElse { it.toString() })
        error("timed out waiting for '$text'")
    }

    /**
     * Scrolls the screen's lazy list one item at a time until a node with [text] is composed. The clock is
     * paused, so each step gets a few frames for the list to lay out before the next look-up;
     * `performScrollToNode` would spin forever here because it never lets a frame through.
     */
    private fun scrollTo(text: String) {
        val list = compose.onNode(hasScrollAction())
        var index = 0
        while (!exists(text)) {
            check(index < MAX_SCROLL_ITEMS) { "could not scroll to '$text'" }
            val target = index++
            runCatching { list.performSemanticsAction(SemanticsActions.ScrollToIndex) { it(target) } }
            settle(SCROLL_STEP_MILLIS)
        }
        settle()
    }

    /** True when at least one node shows [text]; a substring may legitimately match several nodes. */
    private fun exists(text: String, substring: Boolean = true): Boolean =
        runCatching {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

    /**
     * Invokes the click action of the first clickable node showing [text]. Goes through semantics rather
     * than touch injection so rows that are composed just below the viewport (lazy-list prefetch) still work.
     */
    private fun click(text: String, substring: Boolean = false) {
        compose
            .onAllNodesWithText(text, substring = substring)
            .filter(hasClickAction())
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** Same as [click] for nodes identified by content description (icon-only buttons). */
    private fun clickDescribed(description: String) {
        compose
            .onAllNodesWithContentDescription(description)
            .filter(hasClickAction())
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** Lets background work land, idles the main looper and advances the Compose clock frame by frame. */
    private fun settle(millis: Long = SETTLE_MILLIS) {
        val frames = (millis / FRAME_MILLIS).toInt()
        repeat(frames) {
            Thread.sleep(REAL_SLEEP_MILLIS)
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun capture(name: String) = captureFrom(checkNotNull(scenario), name)

    private fun <A : android.app.Activity> captureFrom(scenario: ActivityScenario<A>, name: String) {
        settle()
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            check(view.width > 0 && view.height > 0) { "decor view has no size" }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File(System.getProperty("rikavon.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        }
    }

    private fun string(name: String, vararg args: Any): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        check(id != 0) { "missing string $name" }
        return context.getString(id, *args)
    }

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
        private const val TIKTOK = "com.zhiliaoapp.musically"
        private const val YOUTUBE = "com.google.android.youtube"
        private val FAKE_APPS =
            listOf(
                INSTAGRAM to "Instagram",
                TIKTOK to "TikTok",
                YOUTUBE to "YouTube",
                "com.whatsapp" to "WhatsApp",
                "com.twitter.android" to "X",
                "com.reddit.frontpage" to "Reddit",
            )
        private const val INSTAGRAM_LIMIT = 60
        private const val TIKTOK_LIMIT = 60
        private const val YOUTUBE_LIMIT = 90
        private const val STREAK = 4
        private const val BEST_STREAK = 9
        private const val SLEEP_START = 23 * 60
        private const val SLEEP_END = 7 * 60
        private const val RETRY_MILLIS = 14 * 60_000L + 32_000L
        private const val MINUTE_MILLIS = 60_000L
        private const val SETTLE_MILLIS = 1_600L
        private const val LONG_SETTLE = 3_200L
        private const val FRAME_MILLIS = 16L
        private const val REAL_SLEEP_MILLIS = 4L
        private const val PNG_QUALITY = 100
        private const val WAIT_TIMEOUT_MILLIS = 30_000L
        private const val TIMEOUT_NAME_CHARS = 12
        private const val MAX_SCROLL_ITEMS = 40
        private const val SCROLL_STEP_MILLIS = 400L
    }
}

private const val SDK = 34
private const val PHONE_EN = "en-rUS-w360dp-h800dp-xhdpi"
private const val PHONE_HE = "iw-rIL-w360dp-h800dp-xhdpi"
