package io.github.dtubugk.island.island

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import io.github.dtubugk.island.MainActivity
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
 * Keeps the island on screen. It is an accessibility service because that is the one way for an
 * app to draw above the status bar. It reads only the status bar, to find Samsung's own chip for
 * the song or call the island shows and swallow it.
 */
class IslandService : AccessibilityService(), IslandDirector.System {

    private var windowManager: WindowManager? = null
    private var island: IslandView? = null
    private var director: IslandDirector? = null
    private var params: WindowManager.LayoutParams? = null
    private var screen: ScreenSpec? = null
    private var scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    private var lastBatteryLevel = -1
    private var torchOn = false
    private var torchCameraId: String? = null
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchOn = enabled
                publishSystemState()
            }
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
                torchOn = false
                publishSystemState()
            }
        }
    }
    private val knownAudioDevices = HashSet<Int>()
    private var audioCallbackPrimed = false
    private var lastHeadphonesPeek = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatching { handle(context, intent) }
        }

        private fun handle(context: Context, intent: Intent) {
            val d = director ?: return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> updateVisibility(animate = false)
                Intent.ACTION_SCREEN_ON -> updateVisibility(animate = true)
                Intent.ACTION_USER_PRESENT -> {
                    // The island's Face ID moment: a short open-lock flash.
                    updateVisibility(animate = true)
                    d.post(Peek.Unlocked())
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val b = readBattery(intent)
                    d.battery = b
                    checkLowBattery(b)
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    val b = readBattery(context).copy(charging = true)
                    d.battery = b
                    // The phone estimates time to full once it has settled into charging.
                    handler.postDelayed({
                        val ms = runCatching { getSystemService(android.os.BatteryManager::class.java)?.computeChargeTimeRemaining() }.getOrNull() ?: -1L
                        director?.post(Peek.Charging(b.level, if (ms > 0) (ms / 60_000L).toInt() else -1))
                    }, 900)
                }
                Intent.ACTION_POWER_DISCONNECTED -> d.battery = readBattery(context).copy(charging = false)
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    // Sticky: the copy delivered on registration is old news, not a change.
                    if (isInitialStickyBroadcast) return
                    val mode = when (intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)) {
                        AudioManager.RINGER_MODE_SILENT -> Peek.RingerMode.SILENT
                        AudioManager.RINGER_MODE_VIBRATE -> Peek.RingerMode.VIBRATE
                        else -> Peek.RingerMode.SOUND
                    }
                    d.post(Peek.Ringer(mode))
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    publishSystemState()
                    d.post(Peek.DoNotDisturb(on = isDndOn()))
                }
                AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED, AudioManager.ACTION_MICROPHONE_MUTE_CHANGED -> publishSystemState()
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val on = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
                    d.post(Peek.PowerSave(on, d.battery.level))
                }
            }
        }
    }

    /** Headphones: needs no Bluetooth permission, since audio routing already knows the device. */
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            runCatching { onAdded(added) }
        }

        private fun onAdded(added: Array<out AudioDeviceInfo>) {
            val fresh = added.filter { it.isSink && it.type in HEADPHONE_TYPES && knownAudioDevices.add(it.id) }
            // The first callback lists what was already connected; only later ones are news.
            if (!audioCallbackPrimed) {
                audioCallbackPrimed = true
                return
            }
            val device = fresh.firstOrNull() ?: return
            // A Bluetooth headset appears as several routes (media + calls) at once: announce one.
            val now = SystemClock.elapsedRealtime()
            if (now - lastHeadphonesPeek < 3000) return
            lastHeadphonesPeek = now
            val name = device.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "אוזניות"
            director?.post(Peek.Headphones(name))
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            removed.forEach { knownAudioDevices.remove(it.id) }
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

        val view = IslandView(this, isOverlay = true)
        island = view
        val spec = readScreen()
        screen = spec
        view.setScreen(spec)
        val d = IslandDirector(view, this)
        director = d
        d.config = IslandSettings.config.value
        val battery = readBattery(this)
        d.battery = battery
        lastBatteryLevel = battery.level
        updateVisibility(animate = false)

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
            director?.release()
            director = null
            return
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED)
            addAction(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, null)
        getSystemService(AudioManager::class.java)?.registerAudioDeviceCallback(audioCallback, handler)
        getSystemService(CameraManager::class.java)?.let { cm ->
            runCatching {
                // The back camera's flash is the flashlight.
                torchCameraId = cm.cameraIdList.firstOrNull { id ->
                    val ch = cm.getCameraCharacteristics(id)
                    ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                }
                cm.registerTorchCallback(torchCallback, handler)
            }
        }
        publishSystemState()

        scope.launch { IslandSettings.config.collect { d.config = it } }
        scope.launch { IslandSettings.commands.collect { d.demo(it) } }
        scope.launch {
            LiveBus.media.collect {
                d.setMedia(it)
                scheduleChipScan()
            }
        }
        scope.launch {
            LiveBus.activities.collect {
                d.setActivities(it)
                scheduleChipScan()
            }
        }
        scope.launch { LiveBus.peeks.collect { d.post(it) } }
        scope.launch { LiveBus.removed.collect { d.onNotificationRemoved(it) } }
        scope.launch { LiveBus.hideCustom.collect { d.hideCustom(it) } }
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
        updateVisibility(animate = true)
    }

    private fun updateVisibility(animate: Boolean) {
        val visible = !isLandscape() && isInteractive()
        director?.active = visible
        island?.setShown(visible, animate)
        if (visible) scheduleChipScan()
    }

    private fun checkLowBattery(b: BatteryState) {
        val previous = lastBatteryLevel
        lastBatteryLevel = b.level
        if (b.charging && b.level == 100 && previous in 0..99) director?.post(Peek.BatteryFull())
        if (b.charging || previous < 0) return
        // Announce each threshold once, on the way down.
        if (LOW_THRESHOLDS.any { previous > it && b.level <= it }) director?.post(Peek.LowBattery(b.level))
    }

    // --- quick actions ------------------------------------------------------------------------

    private fun isDndOn(): Boolean {
        val filter = getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun publishSystemState() {
        val am = getSystemService(AudioManager::class.java)
        val speaker = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am?.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && am.mode != AudioManager.MODE_NORMAL
        } else {
            @Suppress("DEPRECATION")
            am?.isSpeakerphoneOn == true
        }
        director?.system = SystemState(
            flashlight = torchOn,
            doNotDisturb = isDndOn(),
            speaker = speaker,
            muted = am?.isMicrophoneMute == true,
        )
    }

    override fun act(action: IslandAction) {
        runCatching {
            when (action) {
                IslandAction.FLASHLIGHT -> {
                    val id = torchCameraId ?: return
                    getSystemService(CameraManager::class.java)?.setTorchMode(id, !torchOn)
                }
                IslandAction.DND -> LiveBus.setDoNotDisturb(!isDndOn())
                IslandAction.SCREENSHOT -> {
                    // Let the island fade first so it isn't in the picture.
                    island?.setShown(false, animate = true)
                    handler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                        handler.postDelayed({ updateVisibility(animate = true) }, 900)
                    }, 260)
                }
                IslandAction.LOCK -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                IslandAction.SPEAKER -> toggleSpeaker()
                IslandAction.MUTE -> {
                    val am = getSystemService(AudioManager::class.java) ?: return
                    if (am.mode != AudioManager.MODE_IN_CALL && am.mode != AudioManager.MODE_IN_COMMUNICATION) {
                        Toast.makeText(this, "ההשתקה זמינה רק בזמן שיחה", Toast.LENGTH_SHORT).show()
                        return
                    }
                    am.isMicrophoneMute = !am.isMicrophoneMute
                }
                IslandAction.AIRPLANE -> toggleAirplaneMode()
                IslandAction.SETTINGS -> openSettings()
            }
        }
        // Some toggles have no broadcast; read everything back shortly after.
        handler.postDelayed({ publishSystemState() }, 350)
    }

    /**
     * Speakerphone for the current call. Since Android 12 routing goes through
     * setCommunicationDevice; some dialers keep the routing to themselves, in which case the
     * phone simply ignores this and the button stays unlit.
     */
    private fun toggleSpeaker() {
        val am = getSystemService(AudioManager::class.java) ?: return
        // Outside a call this would only set a global flag that leaks into the next call.
        if (am.mode != AudioManager.MODE_IN_CALL && am.mode != AudioManager.MODE_IN_COMMUNICATION) {
            Toast.makeText(this, "הרמקול זמין רק בזמן שיחה", Toast.LENGTH_SHORT).show()
            return
        }
        val wantSpeaker = !(director?.system?.speaker ?: false)
        var ok = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ok = if (wantSpeaker) {
                val speaker = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                speaker != null && am.setCommunicationDevice(speaker)
            } else {
                am.clearCommunicationDevice()
                true
            }
        }
        @Suppress("DEPRECATION")
        if (!ok) am.isSpeakerphoneOn = wantSpeaker
        handler.postDelayed({
            publishSystemState()
            if (wantSpeaker && director?.system?.speaker != true && !ok) {
                Toast.makeText(this, "הטלפון לא נתן להעביר את השיחה לרמקול מכאן", Toast.LENGTH_SHORT).show()
            }
        }, 400)
    }

    /**
     * Apps cannot switch airplane mode themselves, so the island does what a finger would: opens
     * the quick panel, taps the airplane tile, and closes it. If the tile isn't on the first page
     * it opens the airplane-mode settings instead.
     */
    private fun toggleAirplaneMode() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        var attempts = 0
        lateinit var tryTap: Runnable
        tryTap = Runnable {
            attempts++
            val tile = runCatching { findAirplaneTile() }.getOrNull()
            when {
                tile != null -> {
                    tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    // One UI sometimes folds the panel away by itself; only close it if it is still up.
                    handler.postDelayed({ if (quickPanelOpen()) performGlobalAction(GLOBAL_ACTION_BACK) }, 700)
                }
                attempts < 5 -> handler.postDelayed(tryTap, 300)
                else -> {
                    if (quickPanelOpen()) performGlobalAction(GLOBAL_ACTION_BACK)
                    runCatching {
                        startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
        handler.postDelayed(tryTap, 450)
    }

    /** A SystemUI window taller than the status bar strip: the quick panel or the shade. */
    private fun quickPanelOpen(): Boolean {
        val maxBar = realSize().y * 0.12f
        return windows.any { w ->
            val b = android.graphics.Rect().also { w.getBoundsInScreen(it) }
            w.type == AccessibilityWindowInfo.TYPE_SYSTEM && b.height() > maxBar &&
                runCatching { w.root?.packageName?.toString() }.getOrNull() == SYSTEM_UI
        }
    }

    /**
     * The airplane-mode tile itself: a checkable or clickable node labelled with it. Plain text
     * carrying the words (a header, a settings row) is never tapped, so a wrong hit opens nothing.
     */
    private fun findAirplaneTile(): AccessibilityNodeInfo? {
        val maxBar = realSize().y * 0.12f
        val candidates = ArrayList<AccessibilityNodeInfo>()
        for (w in windows) {
            val b = android.graphics.Rect().also { w.getBoundsInScreen(it) }
            if (w.type != AccessibilityWindowInfo.TYPE_SYSTEM || b.height() <= maxBar) continue
            val root = runCatching { w.root }.getOrNull() ?: continue
            if (root.packageName?.toString() != SYSTEM_UI) continue
            collectNodes(root, 0, candidates) { node ->
                if (!node.isCheckable && !node.isClickable) return@collectNodes false
                val label = (node.contentDescription ?: node.text)?.toString()?.trim()?.lowercase().orEmpty()
                AIRPLANE_LABELS.any { label.startsWith(it) || label == it } && WRONG_LABELS.none { it in label }
            }
        }
        // The tile is the innermost match: prefer checkable, then the smallest bounds.
        return candidates.minWithOrNull(compareBy({ !it.isCheckable }, { android.graphics.Rect().also(it::getBoundsInScreen).let { r -> r.width() * r.height() } }))
    }

    private fun collectNodes(node: AccessibilityNodeInfo, depth: Int, out: MutableList<AccessibilityNodeInfo>, match: (AccessibilityNodeInfo) -> Boolean) {
        if (depth > 30) return
        if (node.isVisibleToUser && match(node)) out += node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            collectNodes(child, depth + 1, out, match)
        }
    }

    // --- IslandDirector.System ----------------------------------------------------------------

    override fun onWindowSizeNeeded(width: Int, height: Int) {
        val view = island ?: return
        val lp = params ?: return
        val spec = screen ?: return
        val x = anchorOffset(view, spec)
        if (lp.width == width && lp.height == height && lp.x == x) return
        val (oldW, oldH, oldX) = Triple(lp.width, lp.height, lp.x)
        lp.width = width
        lp.height = height
        lp.x = x
        if (!view.isAttachedToWindow) return
        // If the system refuses the change, keep the old values so the next request is not
        // short-circuited as "already applied".
        runCatching { windowManager?.updateViewLayout(view, lp) }.onFailure {
            lp.width = oldW
            lp.height = oldH
            lp.x = oldX
        }
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

    override fun openSettings() {
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun openNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    override fun launch(intent: PendingIntent): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ only lets a PendingIntent open an app from the background if the
                // sender opts in; the island is the user's own tap, so it does.
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                intent.send(this, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
        }.isSuccess
    }

    override fun isLocked(): Boolean = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false

    // --- lifecycle ----------------------------------------------------------------------------

    // --- Samsung's chip -----------------------------------------------------------------------

    /** Only status-bar events arrive here (the service config filters to SystemUI). */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        scheduleChipScan()
    }

    private var chipScanPending = false
    private var lastChipScan = 0L
    private val chipScan = Runnable {
        chipScanPending = false
        lastChipScan = SystemClock.uptimeMillis()
        director?.setChip(runCatching { findChip() }.getOrNull())
    }

    /** At most a few scans a second, however chatty the status bar is. */
    private fun scheduleChipScan() {
        if (chipScanPending || director == null) return
        chipScanPending = true
        val wait = (CHIP_SCAN_INTERVAL_MS - (SystemClock.uptimeMillis() - lastChipScan)).coerceAtLeast(0L)
        handler.postDelayed(chipScan, wait)
    }

    /**
     * Finds Samsung's chip by what it says: the status bar node showing the song title (or the
     * caller) that the island is already showing, grown to its whole capsule. Reading the text
     * instead of Samsung's view ids keeps this working across One UI versions.
     */
    private fun findChip(): RectF? {
        val d = director ?: return null
        val needles = d.chipNeedles()
        if (needles.isEmpty()) return null
        val size = realSize()
        val cameraX = screen?.hole?.centerX ?: (size.x / 2f)
        // Room for a heads-up notification sharing the status bar window on some One UI builds.
        val maxBarHeight = size.y * 0.12f
        val windows = runCatching { windows }.getOrNull().orEmpty()
        for (w in windows) {
            if (w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val bounds = android.graphics.Rect().also { w.getBoundsInScreen(it) }
            // The status bar strip itself, not the pulled-down shade or the lock screen.
            if (bounds.top > 0 || bounds.height() > maxBarHeight) continue
            val root = runCatching { w.root }.getOrNull() ?: continue
            if (root.packageName?.toString() != SYSTEM_UI) continue
            val hit = findText(root, needles, depth = 0) ?: continue
            return capsule(hit, bounds, cameraX, size.x)
        }
        return null
    }

    private fun findText(node: android.view.accessibility.AccessibilityNodeInfo, needles: List<String>, depth: Int): android.view.accessibility.AccessibilityNodeInfo? {
        if (depth > 24) return null
        val label = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
        if (label.isNotEmpty() && node.isVisibleToUser && needles.any { matches(label, it) }) return node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            findText(child, needles, depth + 1)?.let { return it }
        }
        return null
    }

    private fun matches(label: String, needle: String): Boolean {
        val n = needle.trim()
        return label.contains(n, ignoreCase = true) || (label.length >= 4 && n.contains(label, ignoreCase = true))
    }

    /**
     * The chip is the first tappable capsule around the song title. Stopping there, rather than at
     * the widest parent, keeps the clock and icons next to it untouched.
     */
    private fun capsule(node: android.view.accessibility.AccessibilityNodeInfo, bar: android.graphics.Rect, cameraX: Float, screenWidth: Int): RectF? {
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return null
        val text = RectF(r)
        var parent = node.parent
        var steps = 0
        while (parent != null && steps < 5) {
            parent.getBoundsInScreen(r)
            val fits = r.width() <= screenWidth * 0.45f && !(r.left < cameraX && r.right > cameraX) &&
                r.top >= bar.top && r.bottom <= bar.bottom
            if (!fits) break
            if (parent.isClickable) return RectF(r)
            parent = parent.parent
            steps++
        }
        // No tappable capsule found: cover the text with room for the app icon beside it.
        val pad = 24f * resources.displayMetrics.density
        return RectF(
            if (text.left - pad < cameraX && text.left > cameraX) cameraX else text.left - pad,
            text.top,
            if (text.right + pad > cameraX && text.right < cameraX) cameraX else text.right + pad,
            text.bottom,
        )
    }

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
        handler.removeCallbacks(chipScan)
        chipScanPending = false
        // A later reconnect must learn the already-connected headphones again, not announce them.
        audioCallbackPrimed = false
        knownAudioDevices.clear()
        scope.cancel()
        val view = island ?: return
        island = null
        director?.release()
        director = null
        runCatching { unregisterReceiver(receiver) }
        getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener)
        getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(audioCallback)
        runCatching { getSystemService(CameraManager::class.java)?.unregisterTorchCallback(torchCallback) }
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
                    @Suppress("DEPRECATION")
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

        private val LOW_THRESHOLDS = listOf(20, 10)
        private const val SYSTEM_UI = "com.android.systemui"
        private const val CHIP_SCAN_INTERVAL_MS = 400L
        private val AIRPLANE_LABELS = listOf("מצב טיסה", "airplane mode", "aeroplane mode", "flight mode")
        private val WRONG_LABELS = listOf("wi-fi", "wifi", "הגדרות", "settings", "שיחות", "calling")
        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )

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
