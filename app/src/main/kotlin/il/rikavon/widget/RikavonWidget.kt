package il.rikavon.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
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
import il.rikavon.core.ui.theme.RikavonColors
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotRegistry
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.render.MascotBitmapRenderer
import il.rikavon.feature.mascot.ui.UiLanguage
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun registry(): MascotRegistry

    fun selectedMascot(): SelectedMascot

    fun scoreProvider(): FocusScoreProvider

    fun renderer(): MascotBitmapRenderer
}

/**
 * Home-screen widget in two sizes. The mascot lives in a ViewFlipper (classic RemoteViews) so that every
 * update crossfades instead of snapping; the score and layout are Glance.
 */
class RikavonWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val skin = entry.selectedMascot().current()
        val score =
            entry
                .scoreProvider()
                .score.value.total
        val stage = MascotStage.fromScore(score)
        val language = UiLanguage.fromLocale(context.resources.configuration.locales)
        val name = skin?.name?.resolve(language) ?: context.getString(R.string.app_name)
        val accent = skin?.let { Color(it.themeColorArgb) } ?: RikavonColors.DefaultAccent
        val bitmap = skin?.let { entry.renderer().render(it, stage, BITMAP_PX) }

        provideContent {
            val prefs = currentState<Preferences>()
            val flipIndex = prefs[KEY_FLIP] ?: 0
            val size = LocalSize.current
            val remoteViews =
                RemoteViews(context.packageName, R.layout.widget_mascot_flipper).apply {
                    val target = if (flipIndex == 0) R.id.widget_mascot_a else R.id.widget_mascot_b
                    bitmap?.let { setImageViewBitmap(target, it) }
                    setDisplayedChild(R.id.widget_flipper, flipIndex)
                    setContentDescription(
                        R.id.widget_flipper,
                        context.getString(R.string.widget_mascot_description, name),
                    )
                }
            GlanceTheme {
                Box(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(RikavonColors.Surface))
                            .cornerRadius(CORNER)
                            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                            .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (size.width >= WIDE.width) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxSize()) {
                            AndroidRemoteViews(remoteViews = remoteViews, modifier = GlanceModifier.size(MASCOT_WIDE))
                            Column(modifier = GlanceModifier.padding(start = 10.dp)) {
                                Text(
                                    text = name,
                                    style =
                                        TextStyle(
                                            color = ColorProvider(RikavonColors.OnInkMuted),
                                            fontSize = 14.sp,
                                        ),
                                )
                                Text(
                                    text = score.toString(),
                                    style =
                                        TextStyle(
                                            color = ColorProvider(accent),
                                            fontSize = 40.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = GlanceModifier.fillMaxSize(),
                        ) {
                            AndroidRemoteViews(remoteViews = remoteViews, modifier = GlanceModifier.size(MASCOT_SMALL))
                            Text(
                                text = score.toString(),
                                style =
                                    TextStyle(
                                        color = ColorProvider(accent),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        private val MASCOT_SMALL = 64.dp
        private val MASCOT_WIDE = 90.dp
        private val CORNER = 20.dp
        private const val BITMAP_PX = 256
        val KEY_FLIP: Preferences.Key<Int> = intPreferencesKey("flip_index")
        val KEY_SIGNATURE: Preferences.Key<String> = stringPreferencesKey("signature")
    }
}

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
        val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
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
