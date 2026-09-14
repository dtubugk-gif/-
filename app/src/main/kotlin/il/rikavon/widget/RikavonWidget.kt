package il.rikavon.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import il.rikavon.MainActivity
import il.rikavon.R
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.theme.ColorMath
import il.rikavon.core.ui.theme.RikavonColors
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.render.MascotBitmapRenderer
import il.rikavon.feature.mascot.ui.UiLanguage
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun selectedMascot(): SelectedMascot

    fun scoreProvider(): FocusScoreProvider

    fun renderer(): MascotBitmapRenderer

    fun settings(): SettingsRepository
}

/** Everything the widget needs, resolved once per update off the composition. */
private data class WidgetData(
    val score: Int,
    val streak: Int,
    val name: String,
    val quote: String,
    val surface: Color,
    val scoreColor: Color,
    val bitmap: Bitmap?,
)

/**
 * Home-screen widget in the two canvas sizes (1f): a compact row and a full card with the pet's line,
 * a five-segment bar and the streak. The mascot lives in a ViewFlipper so updates crossfade.
 */
class RikavonWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val flipIndex = prefs[KEY_FLIP] ?: 0
            val size = LocalSize.current
            val full = size.width >= FULL.width && size.height >= FULL.height
            val remoteViews =
                RemoteViews(context.packageName, R.layout.widget_mascot_flipper).apply {
                    val target = if (flipIndex == 0) R.id.widget_mascot_a else R.id.widget_mascot_b
                    data.bitmap?.let { setImageViewBitmap(target, it) }
                    setDisplayedChild(R.id.widget_flipper, flipIndex)
                    setContentDescription(
                        R.id.widget_flipper,
                        context.getString(R.string.widget_mascot_description, data.name),
                    )
                }
            GlanceTheme {
                WidgetBody(context = context, data = data, remoteViews = remoteViews, full = full)
            }
        }
    }

    private suspend fun loadData(context: Context): WidgetData {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val skin = entry.selectedMascot().current()
        val score =
            entry
                .scoreProvider()
                .score.value.total
        val stage = MascotStage.fromScore(score)
        val language = UiLanguage.fromLocale(context.resources.configuration.locales)
        val quote =
            skin
                ?.stage(stage)
                ?.texts
                ?.resolve(language)
                ?.takeIf { it.isNotEmpty() }
                ?.let { texts ->
                    texts[LocalDate.now().dayOfYear % texts.size]
                }.orEmpty()
        val accentBase = skin?.let { Color(it.themeColorArgb) } ?: RikavonColors.DefaultAccent
        val accent = ColorMath.withLightness(accentBase, ACCENT_LIGHTNESS)
        val tint = skin?.surfaceTintArgb?.let { Color(it) } ?: accentBase
        return WidgetData(
            score = score,
            streak = entry.settings().current().currentStreak,
            name = skin?.name?.resolve(language) ?: context.getString(R.string.app_name),
            quote = quote,
            surface = ColorMath.tintedSurface(tint, SURFACE_LIGHTNESS, SURFACE_SATURATION),
            scoreColor =
                when {
                    score >= SCORE_GREEN_MIN -> RikavonColors.Success
                    score < SCORE_RED_MAX -> RikavonColors.Danger
                    else -> accent
                },
            bitmap = skin?.let { entry.renderer().render(it, stage, BITMAP_PX) },
        )
    }

    companion object {
        val COMPACT = DpSize(180.dp, 80.dp)
        val FULL = DpSize(250.dp, 140.dp)
        private const val BITMAP_PX = 256
        private const val SCORE_GREEN_MIN = 90
        private const val SCORE_RED_MAX = 30
        private const val ACCENT_LIGHTNESS = 0.6f
        private const val SURFACE_LIGHTNESS = 0.095f
        private const val SURFACE_SATURATION = 0.12f
        val KEY_FLIP: Preferences.Key<Int> = intPreferencesKey("flip_index")
        val KEY_SIGNATURE: Preferences.Key<String> = stringPreferencesKey("signature")
    }
}

@Composable
private fun WidgetBody(context: Context, data: WidgetData, remoteViews: RemoteViews, full: Boolean) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(data.surface))
                .cornerRadius(if (full) CORNER_FULL else CORNER_COMPACT)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(horizontal = if (full) 20.dp else 18.dp, vertical = if (full) 18.dp else 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxSize()) {
            AndroidRemoteViews(
                remoteViews = remoteViews,
                modifier = GlanceModifier.size(if (full) MASCOT_FULL else MASCOT_COMPACT),
            )
            Spacer(GlanceModifier.width(if (full) 16.dp else 14.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                ScoreRow(context = context, data = data, full = full)
                if (full) FullDetails(context = context, data = data) else CompactBar(data = data)
            }
        }
    }
}

@Composable
private fun ScoreRow(context: Context, data: WidgetData, full: Boolean) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = data.score.toString(),
            style =
                TextStyle(
                    color = ColorProvider(data.scoreColor),
                    fontSize = if (full) 44.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = context.getString(if (full) R.string.home_score_label else R.string.widget_focus_short),
            style = TextStyle(color = ColorProvider(RikavonColors.OnInkMuted), fontSize = if (full) 13.sp else 12.sp),
            modifier = GlanceModifier.padding(bottom = 6.dp),
        )
    }
}

/** Full card only: the pet's line, a five-segment bar and the streak. */
@Composable
private fun FullDetails(context: Context, data: WidgetData) {
    if (data.quote.isNotBlank()) {
        Text(
            text = "“${data.quote}”",
            style = TextStyle(color = ColorProvider(RikavonColors.OnInk), fontSize = 13.sp),
            maxLines = 2,
            modifier = GlanceModifier.padding(top = 4.dp),
        )
    }
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
        val filled = (data.score * SEGMENTS + MAX_SCORE / 2) / MAX_SCORE
        repeat(SEGMENTS) { index ->
            Box(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .height(5.dp)
                        .padding(horizontal = 3.dp)
                        .background(ColorProvider(if (index < filled) data.scoreColor else RikavonColors.Track))
                        .cornerRadius(3.dp),
            ) {}
        }
    }
    Text(
        text = context.getString(R.string.widget_streak, data.streak),
        style = TextStyle(color = ColorProvider(RikavonColors.OnInkFaint), fontSize = 11.sp),
        modifier = GlanceModifier.padding(top = 5.dp),
    )
}

/** Compact row only: a single thin bar whose length follows the score. */
@Composable
private fun CompactBar(data: WidgetData) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp).height(4.dp)) {
        val fill = data.score.coerceIn(1, MAX_SCORE).toFloat()
        Box(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .fillMaxSize()
                    .background(ColorProvider(data.scoreColor))
                    .cornerRadius(2.dp),
        ) {}
        if (fill < MAX_SCORE) {
            Spacer(GlanceModifier.width(((MAX_SCORE - fill) * BAR_WIDTH_DP / MAX_SCORE).dp))
        }
    }
}

private val MASCOT_COMPACT = 56.dp
private val MASCOT_FULL = 86.dp
private val CORNER_COMPACT = 22.dp
private val CORNER_FULL = 26.dp
private const val SEGMENTS = 5
private const val MAX_SCORE = 100
private const val BAR_WIDTH_DP = 120f

class RikavonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RikavonWidget()
}

/** Flips the ViewFlipper child on every real change so the host animates the swap. */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selectedMascot: SelectedMascot,
    private val scoreProvider: FocusScoreProvider,
) {
    suspend fun update() {
        val widget = RikavonWidget()
        val manager = GlanceAppWidgetManager(context)
        val ids = runCatching { manager.getGlanceIds(RikavonWidget::class.java) }.getOrDefault(emptyList())
        if (ids.isEmpty()) return
        val skinId = selectedMascot.current()?.id.orEmpty()
        val signature = "$skinId:${MascotStage.fromScore(scoreProvider.score.value.total).key}"
        ids.forEach { id ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                val mutable = prefs.toMutablePreferences()
                if (mutable[RikavonWidget.KEY_SIGNATURE] != signature) {
                    mutable[RikavonWidget.KEY_FLIP] = 1 - (mutable[RikavonWidget.KEY_FLIP] ?: 0)
                    mutable[RikavonWidget.KEY_SIGNATURE] = signature
                }
                mutable
            }
        }
        runCatching { widget.updateAll(context) }
    }
}
