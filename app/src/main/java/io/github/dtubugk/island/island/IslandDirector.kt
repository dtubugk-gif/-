package io.github.dtubugk.island.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import io.github.dtubugk.island.R
import io.github.dtubugk.island.data.IslandCommand
import io.github.dtubugk.island.data.IslandConfig
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Decides what the island shows, in the iPhone's order of importance:
 * an opened card, then a short notice, then a live activity (call > music > timer), else idle.
 */
class IslandDirector(
    private val view: IslandView,
    private val system: System,
    /** Off only for snapshot tests, where layoutlib runs delayed callbacks at once. */
    private val autoDismiss: Boolean = true,
) : IslandView.Host {

    interface System {
        fun openSettings()
        fun openNotifications()
        fun launch(intent: PendingIntent)
        fun isLocked(): Boolean
        fun onWindowSizeNeeded(width: Int, height: Int) {}
        fun onShownChanged(shown: Boolean) {}
    }

    var clock: () -> LocalDateTime = { LocalDateTime.now() }

    var config = IslandConfig()
        set(value) {
            field = value
            view.setConfig(value)
            resolve()
        }
    var battery = BatteryState(100, false)
        set(value) {
            field = value
            if (expanded) resolve()
        }

    /** The island is on screen (screen on, portrait). Notices are dropped while it is not. */
    var active = true
        set(value) {
            field = value
            if (!value) {
                expanded = false
                peek = null
                queue.clear()
                resolve(animate = false)
            }
        }

    private var media: MediaState? = null
    private var mediaPausedAt = 0L
    private var activities: List<LiveActivity> = emptyList()
    private var demoMedia: MediaState? = null
    private var demoActivity: LiveActivity? = null

    private var expanded = false
    private var peek: Peek? = null
    private val queue = ArrayDeque<Peek>()
    private var touching = false

    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { onTimeout() }
    private val endDemo = Runnable {
        demoMedia = null
        demoActivity = null
        resolve()
    }

    init {
        view.host = this
        resolve(animate = false)
    }

    // --- inputs -------------------------------------------------------------------------------

    fun setMedia(state: MediaState?) {
        val was = media
        media = state
        if (state != null && !state.playing && (was == null || was.playing || was.packageName != state.packageName)) {
            mediaPausedAt = SystemClock.elapsedRealtime()
        }
        resolve()
    }

    fun setActivities(list: List<LiveActivity>) {
        activities = list
        resolve()
    }

    fun post(p: Peek) {
        if (!active || !allowed(p)) return
        // Charging is a brief notice: never interrupt a card the user opened on purpose.
        if (p is Peek.Charging && expanded) return
        // A newer message from the same chat replaces the waiting one instead of queueing twice.
        if (p is Peek.Message) queue.removeAll { it is Peek.Message && it.key == p.key }
        if (peek == null && !expanded) {
            peek = p
            resolve()
        } else {
            if (queue.size >= MAX_QUEUE) queue.removeFirst()
            queue.addLast(p)
        }
    }

    fun expand() {
        expanded = true
        peek = null
        resolve()
    }

    /** Sample content so each feature can be seen before it happens for real. */
    fun demo(command: IslandCommand) {
        handler.removeCallbacks(endDemo)
        when (command) {
            IslandCommand.EXPANDED -> expand()
            IslandCommand.MUSIC -> {
                demoMedia = sampleMedia(playing = true)
                expanded = false
                resolve()
                if (autoDismiss) handler.postDelayed(endDemo, DEMO_MS)
            }
            IslandCommand.CALL -> {
                demoActivity = LiveActivity("demo-call", LiveKind.CALL, "טלפון", "דני", "שיחה פעילה",
                    java.lang.System.currentTimeMillis() - 83_000L, countDown = false, openApp = null, actions = emptyList())
                expanded = false
                resolve()
                if (autoDismiss) handler.postDelayed(endDemo, DEMO_MS)
            }
            IslandCommand.TIMER -> {
                demoActivity = LiveActivity("demo-timer", LiveKind.TIMER, "שעון", "טיימר", "פסטה",
                    java.lang.System.currentTimeMillis() + 299_000L, countDown = true, openApp = null, actions = emptyList())
                expanded = false
                resolve()
                if (autoDismiss) handler.postDelayed(endDemo, DEMO_MS)
            }
            IslandCommand.MESSAGE -> post(Peek.Message("demo", "הודעות", null, "דני", "נפגשים ב-8? 🙂", null))
            IslandCommand.SILENT -> post(Peek.Ringer(Peek.RingerMode.SILENT))
            IslandCommand.CHARGING -> post(Peek.Charging(battery.level))
        }
    }

    // --- IslandView.Host ----------------------------------------------------------------------

    override fun onTap(tap: Tap) {
        when (tap) {
            Tap.Expand -> if (config.expandOnTap) expand()
            Tap.Collapse -> collapse()
            Tap.Dismiss -> nextPeek()
            Tap.Settings -> {
                system.openSettings()
                collapse()
            }
            Tap.PlayPause -> currentMedia()?.let { m ->
                m.controls?.playPause()
                // Flip at once; the session confirms a moment later.
                val flipped = m.copy(playing = !m.playing, positionMs = m.positionNow(), positionSampledAt = SystemClock.elapsedRealtime())
                if (demoMedia != null) demoMedia = flipped else media = flipped
                resolve()
            }
            Tap.Next -> {
                currentMedia()?.controls?.next()
                schedule()
            }
            Tap.Previous -> {
                currentMedia()?.controls?.previous()
                schedule()
            }
            is Tap.Launch -> {
                tap.intent?.let(system::launch)
                if (peek != null) nextPeek() else collapse()
            }
        }
    }

    override fun onLongPress() {
        system.openSettings()
        collapse()
    }

    override fun onPullDown() {
        system.openNotifications()
        expanded = false
        peek = null
        queue.clear()
        resolve()
    }

    override fun onOutsideTouch() {
        if (expanded) collapse()
    }

    override fun onTouching(active: Boolean) {
        touching = active
        if (active) handler.removeCallbacks(timeout) else schedule()
    }

    override fun onWindowSizeNeeded(width: Int, height: Int) = system.onWindowSizeNeeded(width, height)

    override fun onShownChanged(shown: Boolean) = system.onShownChanged(shown)

    // --- resolution ---------------------------------------------------------------------------

    private fun collapse() {
        expanded = false
        resolve()
    }

    private fun nextPeek() {
        peek = queue.removeFirstOrNull()
        resolve()
    }

    private fun onTimeout() {
        when {
            expanded -> {
                expanded = false
                peek = queue.removeFirstOrNull()
            }
            peek != null -> peek = queue.removeFirstOrNull()
        }
        resolve()
    }

    private fun resolve(animate: Boolean = true) {
        val scene = when {
            expanded -> expandedScene()
            peek != null -> peekScene(peek!!)
            else -> compactScene() ?: IdleScene(config)
        }
        view.show(scene, animate)
        schedule()
    }

    private fun schedule() {
        handler.removeCallbacks(timeout)
        if (touching || !autoDismiss) return
        val ms = when {
            expanded -> if (view.scene is MediaCardScene) MEDIA_CARD_MS else CARD_MS
            peek is Peek.Message -> MESSAGE_MS
            peek is Peek.Charging -> CHARGING_MS
            peek != null -> NOTICE_MS
            else -> {
                // Paused music leaves the island after a while; re-check when that time comes.
                val m = currentMedia()
                if (m != null && !m.playing && config.music) {
                    val left = PAUSED_LINGER_MS - (SystemClock.elapsedRealtime() - mediaPausedAt)
                    if (left > 0) handler.postDelayed({ resolve() }, left + 50)
                }
                return
            }
        }
        handler.postDelayed(timeout, ms)
    }

    private fun currentMedia(): MediaState? = demoMedia ?: media

    private fun call() = (listOfNotNull(demoActivity) + activities).firstOrNull { it.kind == LiveKind.CALL }
    private fun timer() = (listOfNotNull(demoActivity) + activities).firstOrNull { it.kind == LiveKind.TIMER }

    private fun compactScene(): Scene? {
        if (config.liveActivities) call()?.let { return LiveCompactScene(it) }
        if (config.music) {
            currentMedia()?.let { m ->
                val recent = SystemClock.elapsedRealtime() - mediaPausedAt < PAUSED_LINGER_MS
                if (m.playing || recent || m === demoMedia) return MediaCompactScene(m)
            }
        }
        if (config.liveActivities) timer()?.let { return LiveCompactScene(it) }
        return null
    }

    private fun expandedScene(): Scene {
        if (config.liveActivities) call()?.let { return LiveCardScene(it) }
        if (config.music) {
            currentMedia()?.let { m ->
                // Paused long ago means it is not what the user wants; show the info card.
                val fresh = m.playing || SystemClock.elapsedRealtime() - mediaPausedAt < RESUMABLE_MS
                if (fresh) return MediaCardScene(m)
            }
        }
        if (config.liveActivities) timer()?.let { return LiveCardScene(it) }
        val now = clock()
        return InfoScene(
            config = config,
            battery = battery,
            time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(view.context)) "HH:mm" else "h:mm", Locale.US)),
            greeting = when (now.hour) {
                in 5..11 -> "בוקר טוב"
                in 12..16 -> "צהריים טובים"
                in 17..20 -> "ערב טוב"
                else -> "לילה טוב"
            },
            date = now.format(DateTimeFormatter.ofPattern("EEEE, d 'ב'MMMM", HEBREW)),
        )
    }

    private fun peekScene(p: Peek): Scene = when (p) {
        is Peek.Message -> {
            // Message text never shows on the lock screen, and only if the user wants it at all.
            val private = system.isLocked()
            MessageScene(
                appLabel = p.appLabel,
                icon = if (private) null else p.icon,
                title = if (private) p.appLabel else p.title,
                text = if (private || !config.notificationText) "הודעה חדשה" else p.text,
                open = p.open,
                key = p.key + p.text.hashCode(),
            )
        }
        is Peek.Charging -> NoticeScene("charging", "טוען", NoticeScene.Glyph.Battery(p.level, IslandPainter.GREEN, bolt = true), "${p.level}%", IslandPainter.GREEN)
        is Peek.LowBattery -> NoticeScene("low", "סוללה חלשה", NoticeScene.Glyph.Battery(p.level, IslandPainter.RED, bolt = false), "${p.level}%", IslandPainter.RED)
        is Peek.Ringer -> when (p.mode) {
            Peek.RingerMode.SILENT -> NoticeScene("ringer", "מצב שקט", NoticeScene.Glyph.Icon(R.drawable.ic_bell_off, IslandPainter.RED))
            Peek.RingerMode.VIBRATE -> NoticeScene("ringer", "רטט", NoticeScene.Glyph.Icon(R.drawable.ic_vibrate, IslandPainter.ORANGE))
            Peek.RingerMode.SOUND -> NoticeScene("ringer", "צלצול", NoticeScene.Glyph.Icon(R.drawable.ic_bell, 0xFF3A3A44.toInt()))
        }
        is Peek.DoNotDisturb -> NoticeScene(
            "dnd",
            if (p.on) "נא לא להפריע" else "נא לא להפריע כבוי",
            NoticeScene.Glyph.Icon(R.drawable.ic_dnd, if (p.on) IslandPainter.PURPLE else 0xFF3A3A44.toInt()),
        )
        is Peek.Headphones -> NoticeScene("phones", p.name, NoticeScene.Glyph.Icon(R.drawable.ic_headphones, IslandPainter.BLUE), "מחוברות", IslandPainter.BLUE)
    }

    private fun allowed(p: Peek) = when (p) {
        is Peek.Message -> config.notifications
        is Peek.Charging -> config.chargingAnimation
        is Peek.LowBattery, is Peek.Ringer, is Peek.DoNotDisturb, is Peek.Headphones -> config.systemAlerts
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val CARD_MS = 6000L
        const val MEDIA_CARD_MS = 8000L
        const val MESSAGE_MS = 4500L
        const val CHARGING_MS = 3200L
        const val NOTICE_MS = 2600L
        const val PAUSED_LINGER_MS = 30_000L
        const val RESUMABLE_MS = 30 * 60_000L
        const val DEMO_MS = 12_000L
        private const val MAX_QUEUE = 3
        private val HEBREW: Locale = Locale.forLanguageTag("he-IL")

        /** A gradient "album cover" for demos and previews. */
        fun sampleArt(): Bitmap {
            val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 96f, 96f, 0xFFFF6FA3.toInt(), 0xFF6E8BFF.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, 96f, 96f, p)
            p.shader = null
            p.color = 0x55FFFFFF
            c.drawCircle(48f, 48f, 26f, p)
            p.color = 0xFF1B1B2A.toInt()
            c.drawCircle(48f, 48f, 7f, p)
            return b
        }

        fun sampleMedia(playing: Boolean) = MediaState(
            packageName = "demo",
            appLabel = "מוזיקה",
            title = "שיר לדוגמה",
            artist = "האמן האהוב עליך",
            art = sampleArt(),
            accent = 0xFFFF7EB0.toInt(),
            playing = playing,
            positionMs = 62_000L,
            positionSampledAt = SystemClock.elapsedRealtime(),
            speed = 1f,
            durationMs = 192_000L,
            controls = null,
            openApp = null,
        )
    }
}
