package il.rikavon.screenshots

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import il.rikavon.R
import il.rikavon.core.data.model.BlockReason
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.ui.theme.RikavonColors
import il.rikavon.core.ui.theme.RikavonTheme
import il.rikavon.core.ui.theme.schemeFromAccent
import il.rikavon.feature.blocker.contact.CallPhase
import il.rikavon.feature.blocker.contact.CallTurn
import il.rikavon.feature.blocker.contact.PetCallContent
import il.rikavon.feature.blocker.contact.VoiceCallState
import il.rikavon.feature.blocker.engine.BlockDecision
import il.rikavon.feature.blocker.overlay.BlockOverlayContent
import il.rikavon.feature.mascot.model.HourBucket
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotRegistry
import il.rikavon.feature.mascot.registry.MascotTexts
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
import java.io.File
import javax.inject.Inject

/**
 * Generates the Google Play listing graphics from the real app, so they never drift from it: the 512 px
 * icon, the 1024×500 feature graphic in both languages, and framed 9:16 phone screenshots (1080×1920) with
 * a caption above each screen. Everything lands in `docs/play`. Run on demand:
 * `./gradlew :app:testDebugUnitTest --tests 'il.rikavon.screenshots.PlayAssetsTest' -Pscreenshots=true`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [ShotHarness.SDK], qualifiers = PHONE_EN)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayAssetsTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var limits: LimitsRepository

    @Inject lateinit var usage: UsageRepository

    @Inject lateinit var registry: MascotRegistry

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val harness by lazy { ShotHarness(compose, context, ShotHarness.playDir()) }
    private val seeds by lazy { ShotSeeds(context, settings, limits, usage) }
    private val texts = MascotTexts()

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

    /** The launcher icon as Play wants it: the full 108 dp adaptive canvas, square, no mask, 512×512. */
    @Test
    @Config(qualifiers = ICON)
    fun icon() {
        val background = Color(context.getColor(R.color.ic_launcher_background))
        val shot = standalone { LauncherIcon(background) }
        check(shot.width == ICON_PX && shot.height == ICON_PX) { "icon is ${shot.width}×${shot.height}" }
        harness.save(shot, ShotHarness.playDir(), "icon_512")
    }

    @Test
    @Config(qualifiers = FEATURE_EN)
    fun featureGraphicEnglish() = featureGraphic(Copy.ENGLISH)

    @Test
    @Config(qualifiers = FEATURE_HE)
    fun featureGraphicHebrew() = featureGraphic(Copy.HEBREW)

    @Test
    fun storeScreenshotsEnglish() = storeScreenshots(Copy.ENGLISH)

    @Test
    @Config(qualifiers = PHONE_HE)
    fun storeScreenshotsHebrew() = storeScreenshots(Copy.HEBREW)

    private fun featureGraphic(copy: Copy) {
        val skins = runBlocking { registry.load() }
        val lineup = FEATURE_LINEUP.mapNotNull { id -> skins.firstOrNull { it.id == id } }.ifEmpty { skins }
        val appName = string("app_name")
        val shot = standalone { FeatureGraphic(copy, appName, lineup) }
        check(shot.width == FEATURE_WIDTH_PX && shot.height == FEATURE_HEIGHT_PX) {
            "feature graphic is ${shot.width}×${shot.height}"
        }
        harness.save(shot, ShotHarness.playDir(), "feature_graphic_${copy.code}")
    }

    /** Walks the real app for the in-app screens, renders the three "moment" screens, then frames them all. */
    private fun storeScreenshots(copy: Copy) {
        seeds.base()
        seeds.limitsAndUsage()
        val shots = linkedMapOf<String, Bitmap>()
        harness.launch(MainActivity::class.java).use { main ->
            waitFor("Instagram")
            settle(ShotHarness.LONG_SETTLE)
            shots[HOME] = harness.draw(main)
            click("Instagram")
            waitFor(string("limit_save"))
            shots[LIMITS] = harness.draw(main)
            main.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            waitFor(string("nav_gallery"))
            click(string("nav_gallery"))
            waitFor(string("gallery_selected_now"))
            settle(ShotHarness.LONG_SETTLE)
            shots[GALLERY] = harness.draw(main)
            click(string("nav_stats"))
            waitFor(string("stats_per_app"))
            settle(ShotHarness.LONG_SETTLE)
            shots[STATS] = harness.draw(main)
            click(string("nav_settings"))
            waitFor(string("settings_pet"))
            harness.scrollTo(string("settings_sites"))
            click(string("settings_sites"))
            waitFor(string("sites_section_websites"))
            shots[SITES] = harness.draw(main)
        }
        val skins = runBlocking { registry.load() }
        shots[BLOCK] = standalone { BlockScreen(skins.byId("potato"), copy) }
        shots[CALL] = standalone { CallScreen(skins.byId("plant"), copy) }
        shots[TALK] = standalone { TalkScreen(skins.byId("brain"), copy) }
        val dir = File(ShotHarness.playDir(), "screenshots")
        shots.toSortedMap().forEach { (name, shot) ->
            check(shot.width == SHOT_WIDTH_PX && shot.height == SHOT_HEIGHT_PX) {
                "$name is ${shot.width}×${shot.height}"
            }
            val framed = standalone { StoreFrame(copy.captions.getValue(name), shot) }
            harness.save(framed, dir, "${copy.code}_$name")
        }
    }

    // ---- The three moments the app is about -----------------------------------------------------

    @Composable
    private fun BlockScreen(skin: MascotSkin, copy: Copy) {
        val message =
            skin.blockMessages
                .resolve(copy.code)
                ?.get(HourBucket.DAY)
                ?.firstOrNull()
                .orEmpty()
        BlockOverlayContent(
            decision =
                BlockDecision(
                    packageName = ShotSeeds.TIKTOK,
                    reason = BlockReason.LIMIT_REACHED,
                    retryAtMillis = System.currentTimeMillis() + RETRY_MILLIS,
                    scheduleId = null,
                ),
            skin = skin,
            appLabel = "TikTok",
            limitMinutes = ShotSeeds.TIKTOK_LIMIT,
            message = message,
            reducedMotion = false,
            onClose = {},
        )
    }

    @Composable
    private fun CallScreen(skin: MascotSkin, copy: Copy) {
        PetCallContent(
            skin = skin,
            petName = texts.name(skin, copy.code),
            appLabel = "Instagram",
            state =
                VoiceCallState(
                    phase = CallPhase.CONNECTED,
                    incoming = true,
                    turn = CallTurn.SPEAKING,
                    caption = copy.callCaption,
                    seconds = CALL_SECONDS,
                ),
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

    @Composable
    private fun TalkScreen(skin: MascotSkin, copy: Copy) {
        val state =
            TalkUiState(
                skin = skin,
                petName = texts.name(skin, copy.code),
                messages = copy.talk,
                listening = true,
                partial = copy.partial,
                language = copy.code,
            )
        val scheme = schemeFromAccent(Color(skin.themeColorArgb), skin.surfaceTintArgb?.let { Color(it) })
        RikavonTheme(scheme = scheme) {
            TalkContent(state = state, onBack = {}, onMic = {}, onSend = {}, onLanguage = {})
        }
    }

    // ---- Store graphics ---------------------------------------------------------------------------

    /** Background and foreground layers drawn the way the launcher would before masking: 108/72 oversize. */
    @Composable
    private fun LauncherIcon(background: Color) {
        Box(modifier = Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(ICON_LAYER),
            )
        }
    }

    @Composable
    private fun FeatureGraphic(copy: Copy, appName: String, skins: List<MascotSkin>) {
        RikavonTheme {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(FRAME_TOP, FRAME_BOTTOM)))
                        .padding(horizontal = FEATURE_GUTTER, vertical = FEATURE_VERTICAL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = RikavonColors.OnInk,
                    )
                    Spacer(modifier = Modifier.height(FEATURE_GAP))
                    Text(text = copy.tagline, style = MaterialTheme.typography.titleMedium, color = RikavonColors.OnInk)
                    Spacer(modifier = Modifier.height(FEATURE_GAP))
                    Text(
                        text = copy.privacy,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(FEATURE_GUTTER))
                Column(verticalArrangement = Arrangement.spacedBy(FEATURE_CELL_GAP)) {
                    skins.zip(MascotStage.entries).chunked(FEATURE_COLUMNS).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(FEATURE_CELL_GAP)) {
                            row.forEach { (skin, stage) ->
                                MascotView(
                                    skin = skin,
                                    stage = stage,
                                    interactive = false,
                                    modifier = Modifier.size(FEATURE_CELL),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** A caption above the screen, the screen itself running off the bottom edge: the usual store frame. */
    @Composable
    private fun StoreFrame(caption: String, shot: Bitmap) {
        RikavonTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(FRAME_TOP, FRAME_BOTTOM))),
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = RikavonColors.OnInk,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = FRAME_CAPTION_TOP, start = FRAME_GUTTER, end = FRAME_GUTTER)
                            .fillMaxWidth(),
                )
                Image(
                    bitmap = shot.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = FRAME_SHOT_TOP)
                            .requiredSize(FRAME_SHOT_WIDTH, FRAME_SHOT_HEIGHT)
                            .clip(RoundedCornerShape(FRAME_RADIUS))
                            .border(FRAME_BORDER, RikavonColors.Outline, RoundedCornerShape(FRAME_RADIUS)),
                )
            }
        }
    }

    // ---- Harness ------------------------------------------------------------------------------

    private fun standalone(content: @Composable () -> Unit): Bitmap =
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent(content = content) }
            settle(ShotHarness.LONG_SETTLE)
            harness.draw(scenario)
        }

    private fun List<MascotSkin>.byId(id: String): MascotSkin = firstOrNull { it.id == id } ?: first()

    private fun waitFor(text: String) = harness.waitFor(text)

    private fun click(text: String) = harness.click(text)

    private fun settle(millis: Long = ShotHarness.SETTLE_MILLIS) = harness.settle(millis)

    private fun string(name: String): String = harness.string(name)

    /** The words on the store graphics, per listing language. Marketing copy, so it lives with the assets. */
    private class Copy(
        val code: String,
        val tagline: String,
        val privacy: String,
        val callCaption: String,
        val partial: String,
        val talk: List<TalkMessage>,
        val captions: Map<String, String>,
    ) {
        companion object {
            val ENGLISH =
                Copy(
                    code = "en",
                    tagline = "An app blocker with a pet that rots when you scroll too much",
                    privacy = "No internet permission. Nothing leaves your phone.",
                    callCaption = "Instagram again. 3 minutes left, and we both know how this ends.",
                    partial = "ok fine, I'll",
                    talk =
                        listOf(
                            TalkMessage(fromPet = true, text = "Hi. Brain here. What do you want?"),
                            TalkMessage(fromPet = false, text = "Just five more minutes of Instagram, please"),
                            TalkMessage(
                                fromPet = true,
                                text = "Five minutes. Sure. That's what you said last time. No.",
                            ),
                        ),
                    captions =
                        mapOf(
                            HOME to "Your pet rots when you scroll too much",
                            BLOCK to "Cross a limit and it drops into the block screen",
                            CALL to "And it calls. A real call: it talks, listens, answers",
                            LIMITS to "Daily limit, opens cap, one-sitting cap, block now",
                            SITES to "Block websites, Shorts and Reels for as long as you choose",
                            GALLERY to "Six pets. Each one rots its own way",
                            STATS to "A transparent focus score and 30 days of stats",
                            TALK to "Talk to it. It answers, in character",
                        ),
                )
            val HEBREW =
                Copy(
                    code = "he",
                    tagline = "חוסם אפליקציות עם מחמד שנרקב כשגוללים יותר מדי",
                    privacy = "בלי הרשאת אינטרנט. כלום לא יוצא מהטלפון.",
                    callCaption = "שוב אינסטגרם. נשארו 3 דקות, ושנינו יודעים איך זה נגמר.",
                    partial = "טוב בסדר, אני",
                    talk =
                        listOf(
                            TalkMessage(fromPet = true, text = "היי. מוח כאן. מה אתם רוצים?"),
                            TalkMessage(fromPet = false, text = "רק עוד חמש דקות של אינסטגרם, בבקשה"),
                            TalkMessage(fromPet = true, text = "חמש דקות. בטח. זה מה שאמרתם בפעם הקודמת. לא."),
                        ),
                    captions =
                        mapOf(
                            HOME to "המחמד שלכם נרקב כשאתם גוללים יותר מדי",
                            BLOCK to "עברתם את הגבול? הוא נכנס למסך החסימה",
                            CALL to "והוא מתקשר. שיחה אמיתית: מדבר, מקשיב, עונה",
                            LIMITS to "גבול יומי, גבול פתיחות, גבול לרצף, וחסימה עכשיו",
                            SITES to "חוסמים אתרים, Shorts ו-Reels לזמן שתבחרו",
                            GALLERY to "שישה מחמדים. כל אחד נרקב בדרך שלו",
                            STATS to "ציון מיקוד שקוף ו-30 יום של סטטיסטיקה",
                            TALK to "אפשר גם לדבר איתו. הוא עונה, באופי שלו",
                        ),
                )
        }
    }

    companion object {
        private const val HOME = "01_home"
        private const val BLOCK = "02_block"
        private const val CALL = "03_call"
        private const val LIMITS = "04_limits"
        private const val SITES = "05_sites"
        private const val GALLERY = "06_gallery"
        private const val STATS = "07_stats"
        private const val TALK = "08_talk"
        private const val ICON_PX = 512
        private const val FEATURE_WIDTH_PX = 1024
        private const val FEATURE_HEIGHT_PX = 500
        private const val SHOT_WIDTH_PX = 1080
        private const val SHOT_HEIGHT_PX = 1920
        private const val FEATURE_COLUMNS = 3
        private const val RETRY_MILLIS = 14 * 60_000L + 32_000L
        private const val CALL_SECONDS = 42

        /** Healthiest to rottenest, one stage each: the whole story in one row. */
        private val FEATURE_LINEUP = listOf("brain", "plant", "goldfish", "cat", "robot", "potato")
    }
}

private const val ICON = "en-rUS-w256dp-h256dp-xhdpi"
private const val FEATURE_EN = "en-rUS-w512dp-h250dp-xhdpi"
private const val FEATURE_HE = "iw-rIL-w512dp-h250dp-xhdpi"
private const val PHONE_EN = "en-rUS-w360dp-h640dp-xxhdpi"
private const val PHONE_HE = "iw-rIL-w360dp-h640dp-xxhdpi"
private val FRAME_TOP = Color(0xFF241C2B)
private val FRAME_BOTTOM = RikavonColors.Ink

/** 256 dp window × 108/72: the adaptive layers are authored with a 72 dp safe zone inside a 108 dp canvas. */
private val ICON_LAYER = 384.dp
private val FEATURE_GUTTER = 32.dp
private val FEATURE_VERTICAL = 20.dp
private val FEATURE_GAP = 8.dp
private val FEATURE_CELL = 80.dp
private val FEATURE_CELL_GAP = 6.dp
private val FRAME_GUTTER = 28.dp
private val FRAME_CAPTION_TOP = 40.dp
private val FRAME_SHOT_TOP = 156.dp
private val FRAME_SHOT_WIDTH = 304.dp
private val FRAME_SHOT_HEIGHT = 540.dp
private val FRAME_RADIUS = 28.dp
private val FRAME_BORDER = 1.dp
