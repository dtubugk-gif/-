package io.github.dtubugk.island

import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.github.dtubugk.island.data.IslandConfig
import io.github.dtubugk.island.island.BatteryState
import io.github.dtubugk.island.island.Hole
import io.github.dtubugk.island.island.IslandDirector
import io.github.dtubugk.island.island.IslandView
import io.github.dtubugk.island.data.IslandCommand
import io.github.dtubugk.island.data.TextSide
import android.app.PendingIntent
import io.github.dtubugk.island.island.ScreenSpec
import io.github.dtubugk.island.ui.IslandActions
import io.github.dtubugk.island.ui.IslandScreen
import io.github.dtubugk.island.ui.IslandTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Renders the real drawing code, so layout or text regressions show up as image diffs. */
class IslandSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 7000, softButtons = false),
        maxPercentDifference = 0.1,
        useDeviceResolution = true,
    )

    private val clock = { LocalDateTime.of(2026, 9, 24, 11, 30) }
    private val battery = BatteryState(level = 83, charging = false)

    private val noActions = object : IslandActions {
        override fun update(transform: (IslandConfig) -> IslandConfig) = Unit
        override fun enableService() = Unit
        override fun enableNotificationAccess() = Unit
        override fun openAppInfo() = Unit
        override fun openSettings() = Unit
        override fun demo(command: IslandCommand) = Unit
    }

    private val noSystem = object : IslandDirector.System {
        override fun openSettings() = Unit
        override fun openNotifications() = Unit
        override fun launch(intent: PendingIntent) = false
        override fun isLocked() = false
    }

    /** A preview island showing [command]'s sample, or the idle island for null. */
    private fun island(config: IslandConfig, command: IslandCommand?): IslandView {
        val view = IslandView(paparazzi.context, isOverlay = false).apply { drawLens = true }
        IslandDirector(view, noSystem, autoDismiss = false).apply {
            clock = this@IslandSnapshotTest.clock
            battery = this@IslandSnapshotTest.battery
            this.config = config
            command?.let(::demo)
        }
        return view
    }

    @Composable
    private fun Screen(dark: Boolean, on: Boolean) {
        IslandTheme(dark = dark) {
            IslandScreen(IslandConfig(), serviceOn = on, notificationAccess = on, battery = battery, actions = noActions, clock = clock)
        }
    }

    @Test
    fun screenSetupLight() = paparazzi.snapshot { Screen(dark = false, on = false) }

    @Test
    fun screenActiveDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 7000, softButtons = false, nightMode = NightMode.NIGHT))
        paparazzi.snapshot { Screen(dark = true, on = true) }
    }

    @Test
    fun islandStates() {
        val context = paparazzi.context
        val column = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFF2F5BA6.toInt())
        }
        val states = listOf(
            IslandConfig() to null,
            IslandConfig(textSide = TextSide.LEFT, colorIndex = 6, widthDp = 150f) to null,
            IslandConfig() to IslandCommand.MUSIC,
            IslandConfig() to IslandCommand.CALL,
            IslandConfig() to IslandCommand.NAVIGATION,
            IslandConfig() to IslandCommand.TIMER,
            IslandConfig() to IslandCommand.PROGRESS,
            IslandConfig() to IslandCommand.SILENT,
            IslandConfig() to IslandCommand.CHARGING,
        )
        val row = (80 * context.resources.displayMetrics.density).toInt()
        for ((config, command) in states) {
            val view = island(config, command)
            // Ringing samples open the island by themselves; this row shows them at rest.
            if (command == IslandCommand.CALL) (view.host as IslandDirector).onTap(io.github.dtubugk.island.island.Tap.Collapse)
            column.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, row))
        }
        paparazzi.snapshot(column)
    }

    @Test
    fun islandCards() {
        val context = paparazzi.context
        val column = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFF2F5BA6.toInt())
        }
        val tall = (270 * context.resources.displayMetrics.density).toInt()
        val short = (200 * context.resources.displayMetrics.density).toInt()
        column.addView(island(IslandConfig(), IslandCommand.EXPANDED), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, short))
        column.addView(island(IslandConfig(), IslandCommand.MESSAGE), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, short))
        // Open the music card: start the music sample, then "tap" the island.
        val music = island(IslandConfig(), IslandCommand.MUSIC)
        column.addView(music, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tall))
        (music.host as IslandDirector).expand()
        column.addView(island(IslandConfig(), IslandCommand.CALL), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tall))
        column.addView(island(IslandConfig(), IslandCommand.ALARM), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, short))
        val nav = island(IslandConfig(), IslandCommand.NAVIGATION)
        column.addView(nav, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, short))
        (nav.host as IslandDirector).expand()
        val progress = island(IslandConfig(), IslandCommand.PROGRESS)
        column.addView(progress, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, short))
        (progress.host as IslandDirector).expand()
        paparazzi.snapshot(column)
    }

    /**
     * Local-only mockup on a real home-screen screenshot (never committed): pass
     * -PmockupBackground=/path/to/screenshot.jpg. Assumes a 1080 px wide Galaxy at 450 dpi.
     */
    @Test
    fun onUserScreenshot() {
        val path = System.getProperty("island.mockupBackground").orEmpty()
        assumeTrue(path.isNotEmpty() && File(path).exists())
        val bitmap = BitmapFactory.decodeFile(path)
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_5.copy(screenWidth = bitmap.width, screenHeight = bitmap.height * 3 + 40, softButtons = false),
        )
        val context = paparazzi.context
        val scale = bitmap.width / 1080f
        val density = 2.8125f * scale
        val spec = ScreenSpec(bitmap.width.toFloat(), 105f * scale, density, Hole(bitmap.width / 2f, 51f * scale, 52f * scale))
        val column = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
        for (command in listOf(null, IslandCommand.MUSIC, IslandCommand.MESSAGE)) {
            val frame = FrameLayout(context)
            frame.addView(ImageView(context).apply { setImageBitmap(bitmap) }, FrameLayout.LayoutParams(bitmap.width, bitmap.height))
            val island = IslandView(context, isOverlay = true).apply { setScreen(spec) }
            IslandDirector(island, noSystem, autoDismiss = false).apply {
                clock = this@IslandSnapshotTest.clock
                battery = this@IslandSnapshotTest.battery
                config = IslandConfig()
                command?.let(::demo)
                // Where Samsung's music chip sits on the reference screenshot (1080 px wide).
                if (command == IslandCommand.MUSIC) setChip(android.graphics.RectF(665f * scale, 26f * scale, 911f * scale, 77f * scale))
            }
            frame.addView(island, FrameLayout.LayoutParams(bitmap.width, bitmap.height))
            column.addView(frame, ViewGroup.LayoutParams(bitmap.width, bitmap.height))
            column.addView(View(context), ViewGroup.LayoutParams(1, 20))
        }
        paparazzi.snapshot(column)
    }
}
