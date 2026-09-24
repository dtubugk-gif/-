package io.github.dtubugk.island.island

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import io.github.dtubugk.island.MainActivity
import io.github.dtubugk.island.data.IslandCommand
import io.github.dtubugk.island.data.IslandSettings
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Keeps the island on screen. It is an accessibility service only because that is the one way
 * for an app to draw above the status bar; it reads no screen content and handles no events.
 */
class IslandService : AccessibilityService(), IslandView.Host {

    private var windowManager: WindowManager? = null
    private var island: IslandView? = null
    private var params: WindowManager.LayoutParams? = null
    private var screen: ScreenSpec? = null
    private var scope = MainScope()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val view = island ?: return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> view.setShown(false, animate = false)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> view.setShown(!isLandscape(), animate = true)
                Intent.ACTION_BATTERY_CHANGED -> view.battery = readBattery(intent)
                Intent.ACTION_POWER_CONNECTED -> {
                    view.battery = readBattery(context).copy(charging = true)
                    if (IslandSettings.config.value.chargingAnimation && isInteractive()) view.showCharging()
                }
                Intent.ACTION_POWER_DISCONNECTED -> view.battery = readBattery(context).copy(charging = false)
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) refreshScreen()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (island != null) return
        IslandSettings.init(this)
        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm
        scope.cancel()
        scope = MainScope()

        val view = IslandView(this, isOverlay = true).also { it.host = this }
        island = view
        val spec = readScreen()
        screen = spec
        view.setScreen(spec)
        view.setConfig(IslandSettings.config.value)
        view.battery = readBattery(this)
        view.setShown(!isLandscape() && isInteractive(), animate = false)

        val (w, h) = view.restingWindowSize()
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = anchorOffset(view, spec)
            y = 0
            title = "DynamicIsland"
            windowAnimations = 0
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setCanPlayMoveAnimation(false)
            if (!view.isShownTarget) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        params = lp
        runCatching { wm.addView(view, lp) }.onFailure {
            island = null
            return
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, null)

        scope.launch { IslandSettings.config.collect { view.setConfig(it) } }
        scope.launch {
            IslandSettings.commands.collect {
                when (it) {
                    IslandCommand.PREVIEW_EXPANDED -> view.expand()
                    IslandCommand.PREVIEW_CHARGING -> {
                        view.battery = readBattery(this@IslandService)
                        view.showCharging()
                    }
                }
            }
        }
        _running.value = true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshScreen()
    }

    private fun refreshScreen() {
        val view = island ?: return
        val spec = readScreen()
        if (spec != screen) {
            screen = spec
            view.setScreen(spec)
        }
        view.setShown(!isLandscape() && isInteractive(), animate = true)
    }

    // --- IslandView.Host ----------------------------------------------------------------------

    override fun onWindowSizeNeeded(width: Int, height: Int) {
        val view = island ?: return
        val lp = params ?: return
        val spec = screen ?: return
        val x = anchorOffset(view, spec)
        if (lp.width == width && lp.height == height && lp.x == x) return
        lp.width = width
        lp.height = height
        lp.x = x
        if (view.isAttachedToWindow) runCatching { windowManager?.updateViewLayout(view, lp) }
    }

    override fun onShownChanged(shown: Boolean) {
        val view = island ?: return
        val lp = params ?: return
        val flags = if (shown) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (flags == lp.flags) return
        lp.flags = flags
        if (view.isAttachedToWindow) runCatching { windowManager?.updateViewLayout(view, lp) }
    }

    override fun onOpenSettings() {
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onOpenNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    // --- lifecycle ----------------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    private fun tearDown() {
        _running.value = false
        scope.cancel()
        val view = island ?: return
        island = null
        runCatching { unregisterReceiver(receiver) }
        getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    // --- screen facts -------------------------------------------------------------------------

    /** The window is centered on the island, so its x is the island's distance from screen center. */
    private fun anchorOffset(view: IslandView, spec: ScreenSpec): Int =
        (view.anchorX - spec.width / 2f).roundToInt()

    @Suppress("DEPRECATION")
    private fun realSize(): Point {
        val point = Point()
        getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(point)
        if (point.x == 0) {
            val dm = resources.displayMetrics
            point.set(dm.widthPixels, dm.heightPixels)
        }
        return point
    }

    private fun isLandscape(): Boolean {
        val size = realSize()
        return size.x > size.y
    }

    private fun isInteractive(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun readScreen(): ScreenSpec {
        val size = realSize()
        val density = resources.displayMetrics.density
        val statusId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusId > 0) resources.getDimensionPixelSize(statusId).toFloat() else 24f * density
        return ScreenSpec(size.x.toFloat(), statusBar, density, findHole(size.x))
    }

    /**
     * Locates the front camera. The cutout path (Android 12+) traces the real hole; older
     * versions only give a bounding box, which is still centered on it.
     */
    private fun findHole(screenWidth: Int): Hole? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val cutout = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.cutout ?: return null
        val top = cutout.boundingRectTop
        if (top.isEmpty) return null
        val box = RectF(top)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cutout.cutoutPath?.let { path ->
                val clip = Path().apply { addRect(RectF(top), Path.Direction.CW) }
                val hole = Path(path)
                if (hole.op(clip, Path.Op.INTERSECT)) {
                    val traced = RectF()
                    hole.computeBounds(traced, true)
                    if (!traced.isEmpty) box.set(traced)
                }
            }
        }
        // A notch wider than a third of the screen is not a punch hole; hug its center anyway.
        val diameter = if (box.width() > screenWidth / 3f) box.height() else min(box.width(), box.height())
        return Hole(box.centerX(), box.centerY(), diameter)
    }

    companion object {
        private val _running = MutableStateFlow(false)
        /** True while the system has the service bound, i.e. the island is live. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun readBattery(context: Context): BatteryState {
            val intent = runCatching {
                ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            }.getOrNull() ?: return BatteryState(level = 100, charging = false)
            return readBattery(intent)
        }

        private fun readBattery(intent: Intent): BatteryState {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
            val percent = if (level < 0) 100 else (level * 100f / scale).roundToInt()
            return BatteryState(percent.coerceIn(0, 100), charging)
        }
    }
}
