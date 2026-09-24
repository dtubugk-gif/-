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
import io.github.dtubugk.island.island.IslandView
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
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5600, softButtons = false),
        maxPercentDifference = 0.1,
        useDeviceResolution = true,
    )

    private val clock = { LocalDateTime.of(2026, 9, 24, 11, 30) }
    private val battery = BatteryState(level = 83, charging = false)

    private val noActions = object : IslandActions {
        override fun update(transform: (IslandConfig) -> IslandConfig) = Unit
        override fun enableService() = Unit
        override fun openAppInfo() = Unit
        override fun previewExpanded() = Unit
        override fun previewCharging() = Unit
    }

    @Composable
    private fun Screen(dark: Boolean, on: Boolean) {
        IslandTheme(dark = dark) {
            IslandScreen(IslandConfig(), serviceOn = on, battery = battery, actions = noActions, clock = clock)
        }
    }

    @Test
    fun screenSetupLight() = paparazzi.snapshot { Screen(dark = false, on = false) }

    @Test
    fun screenActiveDark() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenHeight = 5600, softButtons = false, nightMode = NightMode.NIGHT))
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
            IslandView.Mode.IDLE to IslandConfig(),
            IslandView.Mode.IDLE to IslandConfig(textSide = io.github.dtubugk.island.data.TextSide.LEFT, colorIndex = 6, widthDp = 150f),
            IslandView.Mode.CHARGING to IslandConfig(),
            IslandView.Mode.EXPANDED to IslandConfig(),
        )
        for ((mode, config) in states) {
            val view = IslandView(context, isOverlay = false).apply {
                drawLens = true
                this.clock = this@IslandSnapshotTest.clock
                this.battery = if (mode == IslandView.Mode.CHARGING) BatteryState(83, true) else this@IslandSnapshotTest.battery
                setConfig(config)
                snapTo(mode)
            }
            val height = (150 * context.resources.displayMetrics.density).toInt()
            column.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        }
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
        for (mode in listOf(IslandView.Mode.IDLE, IslandView.Mode.EXPANDED, IslandView.Mode.CHARGING)) {
            val frame = FrameLayout(context)
            frame.addView(ImageView(context).apply { setImageBitmap(bitmap) }, FrameLayout.LayoutParams(bitmap.width, bitmap.height))
            val island = IslandView(context, isOverlay = true).apply {
                this.clock = this@IslandSnapshotTest.clock
                this.battery = if (mode == IslandView.Mode.CHARGING) BatteryState(83, true) else this@IslandSnapshotTest.battery
                setScreen(spec)
                setConfig(IslandConfig())
                snapTo(mode)
            }
            frame.addView(island, FrameLayout.LayoutParams(bitmap.width, bitmap.height))
            column.addView(frame, ViewGroup.LayoutParams(bitmap.width, bitmap.height))
            column.addView(View(context), ViewGroup.LayoutParams(1, 20))
        }
        paparazzi.snapshot(column)
    }
}
