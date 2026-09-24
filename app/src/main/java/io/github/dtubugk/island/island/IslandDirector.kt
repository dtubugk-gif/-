package io.github.dtubugk.island.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
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
    private val sys: System,
    /** Off only for snapshot tests, where layoutlib runs delayed callbacks at once. */
    private val autoDismiss: Boolean = true,
) : IslandView.Host {

    interface System {
        fun openSettings()
        fun openNotifications()
        /** Opens [intent]; true only if the system accepted it. */
        fun launch(intent: PendingIntent): Boolean
        fun isLocked(): Boolean
        /** Runs a quick action on the phone; the director redraws from [SystemState] afterwards. */
        fun act(action: IslandAction) {}
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
    /** Flashlight, DND, speaker, mute: drawn as lit buttons on the cards. */
    var system = SystemState()
        set(value) {
            if (value == field) return
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
                // The chip's position belongs to the orientation it was found in; rescan later.
                chip = null
                // A hidden window may never get the finger's UP; don't let that freeze the timers.
                touching = false
                resolve(animate = false)
            }
        }

    private var media: MediaState? = null
    private var mediaPausedAt = 0L
    private var activities: List<LiveActivity> = emptyList()
    private var demoMedia: MediaState? = null
    private var demoActivity: LiveActivity? = null

    /** Where Samsung's status-bar chip for the current live activity is, if it is on screen. */
    private var chip: RectF? = null

    private var expanded = false
    /** The ringing call that opened the island by itself, if any. */
    private var autoExpandedCall: String? = null
    private var peek: Peek? = null
    private val queue = ArrayDeque<Peek>()
    private var touching = false

    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { onTimeout() }
    private val endDemo = Runnable {
        // A sample that opened the island (the incoming call) closes it when it ends.
        if (demoActivity != null) expanded = false
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
        // Real music beats the sample.
        if (state != null && state.playing && demoMedia != null) {
            demoMedia = null
            handler.removeCallbacks(endDemo)
        }
        if (state != null && !state.playing && (was == null || was.playing || was.packageName != state.packageName)) {
            mediaPausedAt = SystemClock.elapsedRealtime()
        }
        resolve()
    }

    fun setActivities(list: List<LiveActivity>) {
        val known = activities.mapTo(HashSet()) { it.key }
        activities = list
        // A ringing call opens the island by itself, answer and decline buttons ready, like the iPhone.
        val incoming = list.firstOrNull { it.key !in known && isRinging(it) }
        if (incoming != null && active && config.liveActivities) {
            expanded = true
            peek = null
            autoExpandedCall = incoming.key
        }
        // The call it opened for is gone (answered elsewhere, declined, missed): close with it.
        val opened = autoExpandedCall
        if (opened != null && list.none { it.key == opened }) {
            autoExpandedCall = null
            if (expanded) {
                expanded = false
                peek = queue.removeFirstOrNull()
            }
        }
        resolve()
    }

    /** Something demanding attention right now: a ringing call, or an alarm going off. */
    private fun isRinging(a: LiveActivity) = when (a.kind) {
        LiveKind.CALL -> a.actions.any { CallActions.isAccept(it.title) }
        LiveKind.ALARM -> true
        else -> false
    }

    /**
     * Text that identifies Samsung's chip for what the island is showing: the song or the
     * caller. The service looks for it in the status bar and reports back with [setChip].
     */
    fun chipNeedles(): List<String> {
        if (!config.absorbChip) return emptyList()
        val needles = ArrayList<String>()
        if (config.liveActivities) call()?.let { needles += it.title }
        if (config.music) currentMedia()?.let { needles += it.title }
        if (config.liveActivities) timer()?.let { needles += it.title }
        return needles.filter { it.trim().length >= 2 }
    }

    fun setChip(rect: RectF?) {
        // A scan made while hidden (landscape, screen off) describes another geometry.
        if (!active && rect != null) return
        val same = rect == chip || (rect != null && chip != null &&
            kotlin.math.abs(rect.left - chip!!.left) < 2f && kotlin.math.abs(rect.right - chip!!.right) < 2f)
        if (same) return
        chip = rect
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

    /** A notification left the shade: drop it from the island too, shown or queued. */
    fun onNotificationRemoved(key: String) {
        queue.removeAll { it is Peek.Message && it.key == key }
        val current = peek
        if (current is Peek.Message && current.key == key) nextPeek()
    }

    fun expand() {
        expanded = true
        peek = null
        resolve()
    }

    /** Sample content so each feature can be seen before it happens for real. */
    fun demo(command: IslandCommand) {
        when (command) {
            IslandCommand.EXPANDED -> expand()
            IslandCommand.MUSIC -> {
                demoMedia = sampleMedia(playing = true)
                demoActivity = null
                expanded = false
                resolve()
            }
            IslandCommand.CALL -> {
                // An incoming call: the island opens by itself with the call's own buttons.
                demoActivity = LiveActivity("demo-call", LiveKind.CALL, "טלפון", "דני", "שיחה נכנסת",
                    0L, countDown = false, openApp = null,
                    actions = listOf(LiveAction("דחייה", null), LiveAction("מענה", null)))
                demoMedia = null
                expanded = true
                peek = null
                resolve()
            }
            IslandCommand.TIMER -> {
                demoActivity = LiveActivity("demo-timer", LiveKind.TIMER, "שעון", "טיימר", "פסטה",
                    java.lang.System.currentTimeMillis() + 299_000L, countDown = true, openApp = null, actions = emptyList())
                demoMedia = null
                expanded = false
                resolve()
            }
            IslandCommand.NAVIGATION -> {
                val arrow = androidx.core.content.ContextCompat.getDrawable(view.context, R.drawable.ic_navigation)?.mutate()
                    ?.also { it.setTint(IslandPainter.BLUE) }
                demoActivity = LiveActivity("demo-nav", LiveKind.NAVIGATION, "מפות", "300 מ׳", "פנייה ימינה לרחוב הרצל",
                    0L, countDown = false, openApp = null, actions = emptyList(), icon = arrow)
                demoMedia = null
                expanded = false
                resolve()
            }
            IslandCommand.PROGRESS -> {
                demoActivity = LiveActivity("demo-progress", LiveKind.PROGRESS, "הורדות", "עדכון מערכת", "הורדו 1.2 מתוך 1.9 GB",
                    0L, countDown = false, openApp = null, actions = listOf(LiveAction("השהיה", null)), progress = 0.63f)
                demoMedia = null
                expanded = false
                resolve()
            }
            IslandCommand.ALARM -> {
                demoActivity = LiveActivity("demo-alarm", LiveKind.ALARM, "שעון", "07:00", "שעון מעורר",
                    0L, countDown = false, openApp = null,
                    actions = listOf(LiveAction("ביטול", null), LiveAction("נודניק", null)))
                demoMedia = null
                expanded = true
                peek = null
                resolve()
            }
            IslandCommand.MESSAGE -> post(Peek.Message("demo", "הודעות", null, "דני", "נפגשים ב-8? 🙂", null))
            IslandCommand.SILENT -> post(Peek.Ringer(Peek.RingerMode.SILENT))
            IslandCommand.CHARGING -> post(Peek.Charging(battery.level, minutesLeft = 42))
        }
        // Every sample ends on its own, whatever else is tapped meanwhile. A tap on another
        // sample used to cancel this timer without re-arming it, leaving the island stuck.
        handler.removeCallbacks(endDemo)
        if (autoDismiss && (demoMedia != null || demoActivity != null)) handler.postDelayed(endDemo, DEMO_MS)
    }

    // --- IslandView.Host ----------------------------------------------------------------------

    override fun onTap(tap: Tap) {
        when (tap) {
            Tap.Expand -> if (config.expandOnTap) expand()
            Tap.Collapse -> collapse()
            Tap.Dismiss -> nextPeek()
            Tap.Settings -> {
                sys.openSettings()
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
            is Tap.Act -> {
                if (tap.action == IslandAction.SETTINGS) {
                    sys.openSettings()
                    collapse()
                } else {
                    sys.act(tap.action)
                    // Keep the card up so the lit button confirms what happened.
                    schedule()
                }
            }
            is Tap.Launch -> {
                val launched = tap.intent?.let(sys::launch) == true
                val current = peek
                // Opening a message from the island clears it from the shade, like tapping it there;
                // but not if nothing opened, or it opened behind the lock screen.
                if (current is Peek.Message && current.autoCancel && launched && !sys.isLocked()) {
                    LiveBus.cancelNotification(current.key)
                }
                if (current != null) nextPeek() else collapse()
            }
        }
    }

    override fun onLongPress() {
        sys.openSettings()
        collapse()
    }

    override fun onPullDown() {
        sys.openNotifications()
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

    override fun onWindowSizeNeeded(width: Int, height: Int) = sys.onWindowSizeNeeded(width, height)

    override fun onShownChanged(shown: Boolean) = sys.onShownChanged(shown)

    // --- resolution ---------------------------------------------------------------------------

    private fun collapse() {
        expanded = false
        autoExpandedCall = null
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
        // Nothing showing but notices waiting (queued behind a card that closed another way).
        if (!expanded && peek == null && queue.isNotEmpty()) peek = queue.removeFirst()
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
            expanded -> when {
                view.scene is MediaCardScene -> MEDIA_CARD_MS
                call()?.let(::isRinging) == true -> RINGING_MS
                else -> CARD_MS
            }
            peek is Peek.Message -> MESSAGE_MS
            peek is Peek.Charging || peek is Peek.BatteryFull -> CHARGING_MS
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

    private fun live(kind: LiveKind) = (listOfNotNull(demoActivity) + activities).firstOrNull { it.kind == kind }
    private fun call() = live(LiveKind.CALL) ?: live(LiveKind.ALARM)
    /** Below music: guidance, then a running timer, then a download or delivery in progress. */
    private fun timer() = live(LiveKind.NAVIGATION) ?: live(LiveKind.RECORDING) ?: live(LiveKind.TIMER) ?: live(LiveKind.PROGRESS)

    private fun compactScene(): Scene? {
        val cover = chip.takeIf { config.absorbChip }
        if (config.liveActivities) call()?.let { return LiveCompactScene(it, cover) }
        if (config.music) {
            currentMedia()?.let { m ->
                val recent = SystemClock.elapsedRealtime() - mediaPausedAt < PAUSED_LINGER_MS
                // While Samsung still shows its chip, keep swallowing it, paused or not.
                if (m.playing || recent || m === demoMedia || cover != null) return MediaCompactScene(m, cover)
            }
        }
        if (config.liveActivities) timer()?.let { return LiveCompactScene(it, cover) }
        return null
    }

    private fun expandedScene(): Scene {
        if (config.liveActivities) call()?.let { return LiveCardScene(it, system) }
        if (config.music) {
            currentMedia()?.let { m ->
                // Paused long ago means it is not what the user wants; show the info card.
                val fresh = m.playing || SystemClock.elapsedRealtime() - mediaPausedAt < RESUMABLE_MS
                if (fresh) return MediaCardScene(m)
            }
        }
        if (config.liveActivities) timer()?.let { return LiveCardScene(it, system) }
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
            system = system,
        )
    }

    private fun peekScene(p: Peek): Scene = when (p) {
        is Peek.Message -> {
            // Message text never shows on the lock screen, and only if the user wants it at all.
            val private = sys.isLocked()
            MessageScene(
                appLabel = p.appLabel,
                icon = if (private) null else p.icon,
                title = if (private) p.appLabel else p.title,
                text = if (private || !config.notificationText) "הודעה חדשה" else p.text,
                open = p.open,
                key = p.key + p.text.hashCode(),
            )
        }
        is Peek.Charging -> NoticeScene(
            "charging",
            // The green bolt already says "charging"; the words go to what the user can't see.
            if (p.minutesLeft in 1..600) "עוד ${formatMinutes(p.minutesLeft)}" else "טוען",
            NoticeScene.Glyph.Battery(p.level, IslandPainter.GREEN, bolt = true), "${p.level}%", IslandPainter.GREEN,
        )
        is Peek.BatteryFull -> NoticeScene("full", "הסוללה מלאה", NoticeScene.Glyph.Battery(100, IslandPainter.GREEN, bolt = false), "100%", IslandPainter.GREEN)
        is Peek.PowerSave -> NoticeScene(
            "powersave",
            if (p.on) "חיסכון בסוללה" else "חיסכון בסוללה כבוי",
            NoticeScene.Glyph.Battery(p.level, if (p.on) IslandPainter.ORANGE else Color.WHITE, bolt = false), "${p.level}%",
            if (p.on) IslandPainter.ORANGE else Color.WHITE,
        )
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
        is Peek.Charging, is Peek.BatteryFull -> config.chargingAnimation
        is Peek.LowBattery, is Peek.Ringer, is Peek.DoNotDisturb, is Peek.Headphones, is Peek.PowerSave -> config.systemAlerts
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val CARD_MS = 6000L
        const val MEDIA_CARD_MS = 8000L
        const val RINGING_MS = 30_000L
        const val MESSAGE_MS = 4500L
        const val CHARGING_MS = 3200L
        const val NOTICE_MS = 2600L
        const val PAUSED_LINGER_MS = 30_000L
        const val RESUMABLE_MS = 30 * 60_000L
        const val DEMO_MS = 12_000L

        fun formatMinutes(m: Int): String = if (m < 60) "$m דק׳" else "${m / 60} שע׳ ${m % 60} דק׳".replace(" 0 דק׳", "")
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
