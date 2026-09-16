package il.rikavon.screenshots

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
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
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.ScheduleRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.ui.theme.RikavonTheme
import il.rikavon.core.ui.theme.schemeFromAccent
import il.rikavon.feature.blocker.contact.CallPhase
import il.rikavon.feature.blocker.contact.CallTurn
import il.rikavon.feature.blocker.contact.PetCallContent
import il.rikavon.feature.blocker.contact.VoiceCallState
import il.rikavon.feature.blocker.engine.BlockDecision
import il.rikavon.feature.blocker.overlay.BlockOverlayContent
import il.rikavon.feature.blocker.overlay.BreathingOverlayContent
import il.rikavon.feature.mascot.model.HourBucket
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotRegistry
import il.rikavon.feature.mascot.ui.MascotView
import il.rikavon.ui.talk.TalkContent
import il.rikavon.ui.talk.TalkMessage
import il.rikavon.ui.talk.TalkUiState
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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
        seeds.grantEverything()
        seeds.installFakeApps()
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
            scrollTo(string("settings_sites"))
            click(string("settings_sites"))
            waitFor(string("sites_section_websites"))
            capture("27_sites_and_feeds")
            it.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            waitFor(string("settings_pet"))
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

    @Test
    fun breathingGate() {
        val skin = runBlocking { registry.load() }.first { it.id == "potato" }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    BreathingOverlayContent(
                        skin = skin,
                        appLabel = "Instagram",
                        seconds = BREATHE_SECONDS,
                        reducedMotion = false,
                        onEnter = {},
                        onLeave = {},
                    )
                }
            }
            settle(LONG_SETTLE)
            captureFrom(scenario, "20_breathing_gate")
        }
    }

    @Test
    fun petCall() {
        val skin = runBlocking { registry.load() }.first { it.id == "potato" }
        val ringing = VoiceCallState(phase = CallPhase.RINGING, incoming = true)
        val talking =
            VoiceCallState(
                phase = CallPhase.CONNECTED,
                incoming = true,
                turn = CallTurn.SPEAKING,
                caption = "Instagram again. 3 minutes left, and we both know how this ends.",
                seconds = CALL_SECONDS,
            )
        val listening =
            talking.copy(
                turn = CallTurn.LISTENING,
                caption = "So. Talk to me.",
                heard = "ok fine, I'll",
                seconds =
                    CALL_SECONDS + 9,
            )
        val dialing = VoiceCallState(phase = CallPhase.DIALING, incoming = false)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            listOf(
                ringing to "21_pet_call_ringing",
                talking to "22_pet_call_talking",
                listening to "24_pet_call_listening",
                dialing to "25_pet_call_dialing",
            ).forEach { (state, name) ->
                scenario.onActivity { activity -> activity.setContent { callScreen(skin, state) } }
                settle(LONG_SETTLE)
                captureFrom(scenario, name)
            }
        }
    }

    @Composable
    private fun callScreen(skin: MascotSkin, state: VoiceCallState) {
        PetCallContent(
            skin = skin,
            petName = "Potato",
            appLabel = "Instagram",
            state = state,
            reducedMotion = true,
            onAnswer = {},
            onDecline = {},
            onHangUp = {},
            onMute = {},
            onSpeaker = {},
            onKeyboard = {},
            onPromise = {},
        )
    }

    @Test
    fun talk() {
        val skin = runBlocking { registry.load() }.first { it.id == "potato" }
        val state =
            TalkUiState(
                skin = skin,
                petName = "Potato",
                messages =
                    listOf(
                        TalkMessage(fromPet = true, text = "Hi. Potato here. What do you want?"),
                        TalkMessage(fromPet = false, text = "Just five more minutes of Instagram, please"),
                        TalkMessage(fromPet = true, text = "Five minutes. Sure. That's what you said last time. No."),
                    ),
                listening = true,
                partial = "ok fine, I'll",
                language = "en",
            )
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { talkScreen(state) } }
            settle(LONG_SETTLE)
            captureFrom(scenario, "23_talk")
        }
    }

    /** The talk screen lives inside the app's nav host, so it is themed here the way `RikavonRoot` themes it. */
    @Composable
    private fun talkScreen(state: TalkUiState) {
        val skin = checkNotNull(state.skin)
        val scheme = schemeFromAccent(Color(skin.themeColorArgb), skin.surfaceTintArgb?.let { Color(it) })
        RikavonTheme(scheme = scheme) {
            TalkContent(state = state, onBack = {}, onMic = {}, onSend = {}, onLanguage = {})
        }
    }

    /** Every mascot at every stage on one sheet: the proof that each one decays its own way. */
    @Test
    @Config(qualifiers = SHEET)
    fun rotSheet() {
        val skins = runBlocking { registry.load() }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { rotSheet(skins) } }
            settle(LONG_SETTLE)
            captureFrom(scenario, "26_rot_sheet")
        }
    }

    @Composable
    private fun rotSheet(skins: List<MascotSkin>) {
        RikavonTheme {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(SHEET_PADDING),
                verticalArrangement = Arrangement.spacedBy(SHEET_GAP),
            ) {
                skins.forEach { skin ->
                    Text(
                        text = skin.id.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MascotStage.entries.forEach { stage ->
                            MascotView(
                                skin = skin,
                                stage = stage,
                                interactive = false,
                                modifier = Modifier.size(SHEET_CELL),
                            )
                        }
                    }
                }
            }
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

    // ---- Harness ------------------------------------------------------------------------------

    private val harness by lazy { ShotHarness(compose, context, ShotHarness.screenshotsDir()) }
    private val seeds by lazy { ShotSeeds(context, settings, limits, usage) }

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun seedBase() = seeds.base()

    private fun seedLimitsAndUsage() = seeds.limitsAndUsage()

    private fun launchMain(waitText: String): ActivityScenario<MainActivity> {
        val launched = harness.launch(MainActivity::class.java)
        scenario = launched
        waitFor(waitText)
        settle(LONG_SETTLE)
        return launched
    }

    private fun waitFor(text: String) = harness.waitFor(text)

    private fun scrollTo(text: String) = harness.scrollTo(text)

    private fun click(text: String, substring: Boolean = false) = harness.click(text, substring)

    private fun clickDescribed(description: String) = harness.clickDescribed(description)

    private fun settle(millis: Long = ShotHarness.SETTLE_MILLIS) = harness.settle(millis)

    private fun capture(name: String) = captureFrom(checkNotNull(scenario), name)

    private fun <A : android.app.Activity> captureFrom(scenario: ActivityScenario<A>, name: String) =
        harness.save(harness.draw(scenario), ShotHarness.screenshotsDir(), name)

    private fun string(name: String, vararg args: Any): String = harness.string(name, *args)

    companion object {
        private const val INSTAGRAM = ShotSeeds.INSTAGRAM
        private const val TIKTOK = ShotSeeds.TIKTOK
        private const val TIKTOK_LIMIT = ShotSeeds.TIKTOK_LIMIT
        private const val STREAK = ShotSeeds.STREAK
        private const val LONG_SETTLE = ShotHarness.LONG_SETTLE
        private const val SLEEP_START = 23 * 60
        private const val SLEEP_END = 7 * 60
        private const val RETRY_MILLIS = 14 * 60_000L + 32_000L
        private const val BREATHE_SECONDS = 10
        private const val CALL_SECONDS = 42
    }
}

private const val SDK = ShotHarness.SDK
private const val PHONE_EN = "en-rUS-w360dp-h800dp-xhdpi"
private const val PHONE_HE = "iw-rIL-w360dp-h800dp-xhdpi"
private const val SHEET = "en-rUS-w640dp-h900dp-xhdpi"
private val SHEET_PADDING = 16.dp
private val SHEET_GAP = 6.dp
private val SHEET_CELL = 96.dp
