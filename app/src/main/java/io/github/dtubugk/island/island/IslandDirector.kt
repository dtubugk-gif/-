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
        /** Opens an inline reply box for a message that offers one. */
        fun reply(message: Peek.Message) {}
        /** The status bar came or went (a fullscreen video or game hides it). */
        fun onStatusBarVisible(visible: Boolean) {}
        fun onWindowSizeNeeded(width: Int, height: Int) {}
        fun onShownChanged(shown: Boolean) {}
    }

    var clock: () -> LocalDateTime = { LocalDateTime.now() }

    var config = IslandConfig()
        set(value) {
            if (value == field) return
            val visibilityToggled = value.visible != field.visible
            field = value
            // Switching the island off and on in the app ends a swipe-up hide.
            if (visibilityToggled && snoozed) setSnoozed(false)
            view.setConfig(value)
            resolve()
        }
    var battery = BatteryState(100, false)
        set(value) {
            if (value == field) return
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
            if (value && snoozed) setSnoozed(false)
            if (!value) {
                expanded = false
                peek = null
                queue.clear()
                // The chip's position belongs to the orientation it was found in; rescan later.
                chip = null
                // A hidden window may never get the finger's UP; don't let that freeze the timers.
                touching = false
                // Samples don't survive the screen going off.
                demoMedia = null
                demoActivity = null
                handler.removeCallbacks(endDemo)
                resolve(animate = false)
            }
        }

    /**
     * Out of sight for a while (a fullscreen video, the lock screen) but not gone: messages and
     * API islands wait with their clocks stopped and show when the island returns. Momentary
     * notices (charging, ringer, unlock) are not replayed later.
     */
    var held = false
        set(value) {
            if (value == field) return
            field = value
            if (value) {
                handler.removeCallbacks(timeout)
                armedFor = null
            } else {
                // A fresh clock, and a fresh "+N" for whatever queued meanwhile.
                rearm()
                resolve()
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
    /** The activity the user picked (tab or swipe) among concurrent ones; null = by priority. */
    private var selectedKey: String? = null
    /** Swiped up: hidden for a while, or until something urgent (a call, an alarm) arrives. */
    private var snoozed = false
    /** Duration-0 API islands: live content (like a song), kept through screen-off and calls, until HIDE. */
    private val standing = LinkedHashMap<String, Peek.Custom>()
    private val unsnooze = Runnable { setSnoozed(false) }
    private var peek: Peek? = null
    private val queue = ArrayDeque<Peek>()
    private var touching = false

    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable {
        armedFor = null
        onTimeout()
    }
    private val linger = Runnable { resolve() }
    /** What the pending auto-close was armed for; the same state never restarts the clock. */
    private var armedFor: String? = null

    /** A deliberate change (a tap, a new notice) gets a fresh clock even in an equal-looking state. */
    private fun rearm() {
        armedFor = null
    }
    private val endDemo = Runnable {
        // A sample that opened the island (the incoming call) closes it when it ends, unless a
        // real ringing call has meanwhile opened it for its own reasons.
        if (demoActivity != null && autoExpandedCall == null) expanded = false
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
        // The same activities, only their numbers moved on: redraw, but leave the timers alone.
        val sameSet = list.size == activities.size && list.zip(activities).all { (a, b) -> a.key == b.key && a.kind == b.kind }
        activities = list
        if (sameSet && list.none { isRinging(it) && it.key !in known }) {
            resolve(keepTimers = true)
            return
        }
        // A ringing call opens the island by itself, answer and decline buttons ready, like the iPhone.
        val incoming = list.firstOrNull { it.key !in known && isRinging(it) }
        if (incoming != null && active && config.liveActivities) {
            if (snoozed) setSnoozed(false)
            expanded = true
            peek = null
            autoExpandedCall = incoming.key
            selectedKey = null
            rearm()
        }
        if (selectedKey != null && candidates().none { it.key == selectedKey }) selectedKey = null
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
        // Samples have no chip to find; only real content is looked for in the status bar.
        if (config.liveActivities) call()?.takeIf { it !== demoActivity }?.let { needles += it.title }
        if (config.music) media?.let { needles += it.title }
        if (config.liveActivities) timer()?.takeIf { it !== demoActivity }?.let { needles += it.title }
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
        if (!allowed(p)) return
        // A standing API island is state, not a notice: stored even while the screen is off.
        if (p is Peek.Custom && p.durationMs <= 0L) {
            queue.removeAll { it is Peek.Custom && it.id == p.id }
            if ((peek as? Peek.Custom)?.id == p.id) peek = null
            standing[p.id] = p
            rearm()
            resolve()
            return
        }
        if (p is Peek.Custom) standing.remove(p.id)
        if (!active) return
        // Hidden behind a fullscreen app: a moment's notice is stale by the time the island
        // returns; a message or an API island is not.
        if (held && p !is Peek.Message && p !is Peek.Custom) return
        // Charging is a brief notice: never interrupt a card the user opened on purpose.
        if (p is Peek.Charging && expanded) return
        // A newer message from the same chat replaces the waiting one instead of queueing twice.
        if (p is Peek.Message) queue.removeAll { it is Peek.Message && it.key == p.key }
        // An API island updated by its app replaces its previous version, shown or waiting.
        if (p is Peek.Custom) {
            queue.removeAll { it is Peek.Custom && it.id == p.id }
            val current = peek
            if (current is Peek.Custom && current.id == p.id) {
                peek = p
                rearm()
                resolve()
                return
            }
        }
        // The open-lock flash marks a moment: shown now or never, it is not replayed later.
        if (p is Peek.Unlocked && (expanded || peek != null)) return
        if (peek == null && !expanded) {
            peek = p
            rearm()
            resolve()
        } else {
            if (queue.size >= MAX_QUEUE) {
                // A full line drops an API post, never the phone's own notices.
                if (p is Peek.Custom) return
                val i = queue.indexOfFirst { it is Peek.Custom }
                queue.removeAt(if (i >= 0) i else 0)
            }
            queue.addLast(p)
            // The card on screen says how many wait behind it: keep that number live. Same
            // scene key, so the view redraws in place and the running clock is left alone.
            if (peek is Peek.Message) resolve(keepTimers = true)
        }
    }

    /** A notification left the shade: drop it from the island too, shown or queued. */
    fun onNotificationRemoved(key: String) {
        val dropped = queue.removeAll { it is Peek.Message && it.key == key }
        val current = peek
        when {
            current is Peek.Message && current.key == key -> nextPeek()
            // One fewer waiting: the "+N" on the shown card must not overcount.
            dropped && current is Peek.Message -> resolve(keepTimers = true)
        }
    }

    fun expand() {
        expanded = true
        peek = null
        rearm()
        resolve()
    }

    /** An app took back its island. */
    fun hideCustom(id: String) {
        if (standing.remove(id) != null) {
            if (selectedKey == "custom:$id") selectedKey = null
            rearm()
            resolve()
        }
        queue.removeAll { it is Peek.Custom && it.id == id }
        val current = peek
        if (current is Peek.Custom && current.id == id) nextPeek()
    }

    private fun setSnoozed(on: Boolean) {
        snoozed = on
        handler.removeCallbacks(unsnooze)
        if (on) {
            expanded = false
            autoExpandedCall = null
            handler.postDelayed(unsnooze, SNOOZE_MS)
        }
        view.setSnoozed(on)
        // The view must not keep the card that was swiped away: it would come back as a dead
        // card the director no longer owns. Re-resolve, so it returns collapsed with its clocks.
        rearm()
        resolve(animate = on)
    }

    // --- concurrent activities ----------------------------------------------------------------

    /** Everything live right now, in priority order: call/alarm, music, guidance, recording, timer, progress. */
    private fun candidates(): List<Candidate> {
        val out = ArrayList<Candidate>()
        val live = listOfNotNull(demoActivity) + activities
        fun add(kind: LiveKind) {
            if (!config.liveActivities) return
            live.filter { it.kind == kind }.forEach { out += Candidate.Live(it) }
        }
        add(LiveKind.CALL)
        add(LiveKind.ALARM)
        if (config.api) standing.values.forEach { out += Candidate.Custom(it) }
        if (config.music) {
            currentMedia()?.let { m ->
                val recent = SystemClock.elapsedRealtime() - mediaPausedAt < PAUSED_LINGER_MS
                if (m.playing || recent || m === demoMedia || chip != null) out += Candidate.Music(m)
            }
        }
        add(LiveKind.NAVIGATION)
        add(LiveKind.RECORDING)
        add(LiveKind.TIMER)
        add(LiveKind.PROGRESS)
        return out
    }

    private sealed class Candidate(val key: String) {
        class Live(val activity: LiveActivity) : Candidate(activity.key)
        class Music(val media: MediaState) : Candidate("music:" + media.packageName)
        class Custom(val p: Peek.Custom) : Candidate("custom:" + p.id)
    }

    /** The one the island shows: the user's pick if still live, else the most important. */
    private fun current(list: List<Candidate> = candidates()): Candidate? =
        list.firstOrNull { it.key == selectedKey } ?: list.firstOrNull()

    private fun tabsFor(list: List<Candidate>, current: Candidate?): List<IslandTab> {
        if (list.size < 2) return emptyList()
        return list.map { c ->
            val (icon, accent) = when (c) {
                is Candidate.Music -> R.drawable.ic_music to c.media.accent
                is Candidate.Live -> c.activity.tabIcon() to c.activity.tabAccent()
                is Candidate.Custom -> R.drawable.ic_bolt to c.p.color
            }
            IslandTab(c.key, icon, accent, c === current)
        }
    }

    /** Sideways swipe: next song on music, else the next concurrent activity, else dismiss a notice. */
    private fun cycle(step: Int) {
        val list = candidates()
        val cur = current(list) ?: return
        if (list.size < 2) return
        val i = list.indexOf(cur)
        selectedKey = list[Math.floorMod(i + step, list.size)].key
        rearm()
        resolve()
    }

    /** Sample content so each feature can be seen before it happens for real. */
    fun demo(command: IslandCommand) {
        when (command) {
            // Opening the app always brings a hidden island back.
            IslandCommand.WAKE -> return setSnoozed(false)
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
            // Notices asked for by a tap show at once, over whatever is up, and don't touch the
            // timer of a sample that may be running underneath.
            IslandCommand.MESSAGE -> return showSample(Peek.Message("demo", "הודעות", null, "דני", "נפגשים ב-8? 🙂", null,
                actions = listOf(LiveAction("סמן כנקרא", null), LiveAction("לייק", null))))
            IslandCommand.SILENT -> return showSample(Peek.Ringer(Peek.RingerMode.SILENT))
            IslandCommand.CHARGING -> return showSample(Peek.Charging(battery.level, minutesLeft = 42))
        }
        // Every sample ends on its own, whatever else is tapped meanwhile. A tap on another
        // sample used to cancel this timer without re-arming it, leaving the island stuck.
        handler.removeCallbacks(endDemo)
        if (autoDismiss && (demoMedia != null || demoActivity != null)) handler.postDelayed(endDemo, DEMO_MS)
    }

    /** Unlike [post], a sample notice is never dropped or queued behind an open card. */
    private fun showSample(p: Peek) {
        if (!allowed(p)) return
        queue.clear()
        expanded = false
        peek = p
        rearm()
        resolve()
    }

    // --- IslandView.Host ----------------------------------------------------------------------

    override fun onTap(tap: Tap) {
        when (tap) {
            Tap.Expand -> if (config.expandOnTap) expand()
            Tap.Collapse -> collapse()
            Tap.Dismiss -> if (peek != null) nextPeek() else dismissStanding()
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
            is Tap.Select -> {
                selectedKey = tap.key
                rearm()
                resolve()
            }
            is Tap.Reply -> {
                val current = peek
                if (current is Peek.Message && current.reply != null && !sys.isLocked()) {
                    sys.reply(current)
                    // The island steps aside while the reply box is up; the message stays available.
                    nextPeek()
                }
            }
            is Tap.Act -> {
                if (tap.action == IslandAction.SETTINGS) {
                    sys.openSettings()
                    collapse()
                } else {
                    sys.act(tap.action)
                    // Keep the card up so the lit button confirms what happened.
                    rearm()
                    schedule()
                }
            }
            is Tap.Action -> {
                tap.intent?.let(sys::launch)
                // Unlike a tap on the message itself, a button never clears it from the shade:
                // the app cancels it (mark as read) or keeps it (like, snooze), as in the shade.
                if (peek != null) nextPeek() else collapse()
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
        // A notice on top is what the finger is on; holding it dismisses it, like a swipe.
        if (peek != null) {
            nextPeek()
            return
        }
        // On a timer, stopwatch or recording: its own pause/stop button, gentlest first, whole
        // words only ("end" must not match "Send"). Rides and deliveries are never touched here.
        val cur = current()
        if (cur is Candidate.Live && cur.activity.kind in STOPPABLE) {
            val stop = cur.activity.actions
                .map { a -> a to a.title.lowercase().split(WORD_SPLIT).filter { it.isNotEmpty() } }
                .filter { (_, words) -> words.any { it in STOP_WORDS } }
                .minByOrNull { (_, words) -> if (words.any { it in PAUSE_WORDS }) 0 else 1 }?.first
            if (stop?.intent != null) {
                sys.launch(stop.intent)
                collapse()
                return
            }
            if (!expanded) {
                expand()
                return
            }
        }
        sys.openSettings()
        collapse()
    }

    private fun dismissStanding() {
        val cur = current() as? Candidate.Custom ?: return
        standing.remove(cur.p.id)
        selectedKey = null
        rearm()
        resolve()
    }

    override fun onSwipe(direction: Int) {
        val cur = current()
        when {
            peek != null -> nextPeek()
            cur is Candidate.Custom && candidates().size < 2 -> dismissStanding()
            cur is Candidate.Music && (view.scene is MediaCompactScene || view.scene is MediaCardScene) && candidates().size < 2 ->
                onTap(if (direction < 0) Tap.Next else Tap.Previous)
            candidates().size >= 2 -> cycle(if (direction < 0) 1 else -1)
            cur is Candidate.Music -> onTap(if (direction < 0) Tap.Next else Tap.Previous)
        }
    }

    override fun onSwipeUp() {
        if (peek != null) {
            nextPeek()
            return
        }
        setSnoozed(true)
        android.widget.Toast.makeText(view.context, "האי מוסתר לדקה. פתיחת האפליקציה מחזירה אותו מיד", android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onPullDown() {
        // First pull opens the island's own pop-up; a pull on an open card reaches the shade.
        if (!expanded && peek == null) {
            expand()
            return
        }
        sys.openNotifications()
        expanded = false
        peek = null
        queue.clear()
        rearm()
        resolve()
    }

    override fun onOutsideTouch() {
        if (expanded) collapse()
    }

    override fun onTouching(active: Boolean) {
        touching = active
        if (active) {
            // The clock stops under the finger and starts afresh when it lifts, whatever it did.
            handler.removeCallbacks(timeout)
            armedFor = null
        } else {
            schedule()
        }
    }

    override fun onWindowSizeNeeded(width: Int, height: Int) = sys.onWindowSizeNeeded(width, height)

    override fun onStatusBarVisible(visible: Boolean) = sys.onStatusBarVisible(visible)

    override fun onShownChanged(shown: Boolean) = sys.onShownChanged(shown)

    // --- resolution ---------------------------------------------------------------------------

    private fun collapse() {
        expanded = false
        autoExpandedCall = null
        rearm()
        resolve()
    }

    private fun nextPeek() {
        peek = queue.removeFirstOrNull()
        rearm()
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

    private fun resolve(animate: Boolean = true, keepTimers: Boolean = false) {
        // Nothing showing but notices waiting (queued behind a card that closed another way).
        if (!expanded && peek == null && queue.isNotEmpty()) peek = queue.removeFirst()
        val scene = when {
            expanded -> expandedScene()
            peek != null -> peekScene(peek!!)
            else -> compactScene() ?: IdleScene(config)
        }
        view.show(scene, animate)
        if (!keepTimers) schedule()
    }

    private fun schedule() {
        handler.removeCallbacks(linger)
        if (touching || held || !autoDismiss) {
            handler.removeCallbacks(timeout)
            armedFor = null
            return
        }
        // Redraws for a new battery level, chip position or song position must not push the
        // card's closing time further away each time; only a new state arms a new clock.
        val state = "$expanded|${peek?.let { it::class.simpleName + it.hashCode() }}|${view.scene.key}"
        if (state == armedFor) return
        handler.removeCallbacks(timeout)
        armedFor = null
        val ms = when {
            expanded -> when {
                view.scene is MediaCardScene -> MEDIA_CARD_MS
                call()?.let(::isRinging) == true -> RINGING_MS
                else -> CARD_MS
            }
            peek is Peek.Message -> config.messageSeconds.coerceIn(IslandConfig.MIN_MESSAGE_SECONDS, IslandConfig.MAX_MESSAGE_SECONDS) * 1000L
            peek is Peek.Charging || peek is Peek.BatteryFull -> CHARGING_MS
            peek is Peek.Unlocked -> UNLOCK_MS
            peek is Peek.Custom -> (peek as Peek.Custom).durationMs.coerceIn(800L, MAX_CUSTOM_MS)
            peek != null -> NOTICE_MS
            else -> {
                // Paused music leaves the island after a while; re-check when that time comes.
                val m = currentMedia()
                if (m != null && !m.playing && config.music) {
                    val left = PAUSED_LINGER_MS - (SystemClock.elapsedRealtime() - mediaPausedAt)
                    if (left > 0) handler.postDelayed(linger, left + 50)
                }
                return
            }
        }
        armedFor = state
        handler.postDelayed(timeout, ms)
    }

    private fun currentMedia(): MediaState? = demoMedia ?: media

    private fun live(kind: LiveKind) = (listOfNotNull(demoActivity) + activities).firstOrNull { it.kind == kind }
    private fun call() = live(LiveKind.CALL) ?: live(LiveKind.ALARM)
    private fun timer() = live(LiveKind.NAVIGATION) ?: live(LiveKind.RECORDING) ?: live(LiveKind.TIMER) ?: live(LiveKind.PROGRESS)

    private fun compactScene(): Scene? {
        val cover = chip.takeIf { config.absorbChip }
        return when (val c = current()) {
            is Candidate.Live -> LiveCompactScene(c.activity, cover)
            is Candidate.Music -> MediaCompactScene(c.media, cover)
            is Candidate.Custom -> peekScene(c.p)
            null -> null
        }
    }

    private fun expandedScene(): Scene {
        val list = candidates()
        val cur = current(list)
        val tabs = tabsFor(list, cur)
        when (cur) {
            is Candidate.Live -> return LiveCardScene(cur.activity, system, tabs)
            is Candidate.Custom -> return CustomCardScene(cur.p, tabs)
            is Candidate.Music -> {
                // Paused long ago means it is not what the user wants; show the info card.
                val fresh = cur.media.playing || SystemClock.elapsedRealtime() - mediaPausedAt < RESUMABLE_MS || cur.media === demoMedia
                if (fresh) return MediaCardScene(cur.media, tabs)
            }
            null -> Unit
        }
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
            actions = config.quickActions,
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
                actions = if (private) emptyList() else p.actions,
                canReply = !private && p.reply != null,
                // How many more are waiting behind this one.
                more = queue.count { it is Peek.Message },
            )
        }
        is Peek.Screenshot -> NoticeScene("shot", "צילום מסך נשמר", NoticeScene.Glyph.Icon(R.drawable.ic_screenshot, 0xFF3A3A44.toInt()), open = p.open)
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
        is Peek.Hotspot -> NoticeScene("hotspot", if (p.on) "נקודה חמה פעילה" else "נקודה חמה כבויה", NoticeScene.Glyph.Icon(R.drawable.ic_hotspot, if (p.on) IslandPainter.BLUE else 0xFF3A3A44.toInt()))
        is Peek.Vpn -> NoticeScene("vpn", if (p.on) "VPN מחובר" else "VPN מנותק", NoticeScene.Glyph.Icon(R.drawable.ic_vpn, if (p.on) IslandPainter.GREEN else 0xFF3A3A44.toInt()))
        is Peek.Unlocked -> NoticeScene("unlock", "נפתח", NoticeScene.Glyph.Icon(R.drawable.ic_lock_open, IslandPainter.GREEN))
        is Peek.Custom -> if (p.card) {
            CustomCardScene(p)
        } else {
            NoticeScene("custom:" + p.id, p.title, NoticeScene.Glyph.Icon(R.drawable.ic_bolt, p.color), p.text, Color.WHITE)
        }
    }

    private fun allowed(p: Peek) = when (p) {
        is Peek.Message -> config.notifications
        is Peek.Charging, is Peek.BatteryFull -> config.chargingAnimation
        is Peek.LowBattery, is Peek.Ringer, is Peek.DoNotDisturb, is Peek.Headphones, is Peek.PowerSave, is Peek.Hotspot, is Peek.Vpn, is Peek.Unlocked, is Peek.Screenshot -> config.systemAlerts
        is Peek.Custom -> config.api
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun LiveActivity.tabIcon() = when (kind) {
        LiveKind.CALL -> R.drawable.ic_call
        LiveKind.ALARM -> R.drawable.ic_alarm
        LiveKind.NAVIGATION -> R.drawable.ic_navigation
        LiveKind.RECORDING -> R.drawable.ic_timer
        LiveKind.TIMER -> R.drawable.ic_timer
        LiveKind.PROGRESS -> R.drawable.ic_download
    }

    private fun LiveActivity.tabAccent() = when (kind) {
        LiveKind.CALL -> IslandPainter.GREEN
        LiveKind.ALARM -> IslandPainter.RED
        LiveKind.NAVIGATION -> IslandPainter.BLUE
        LiveKind.RECORDING -> IslandPainter.RED
        LiveKind.TIMER -> IslandPainter.ORANGE
        LiveKind.PROGRESS -> IslandPainter.BLUE
    }

    companion object {
        const val CARD_MS = 6000L
        const val MEDIA_CARD_MS = 8000L
        const val RINGING_MS = 30_000L
        const val MESSAGE_MS = 4500L
        const val CHARGING_MS = 3200L
        const val NOTICE_MS = 2600L
        const val UNLOCK_MS = 1300L
        const val MAX_CUSTOM_MS = 120_000L
        const val SNOOZE_MS = 60_000L
        private val STOPPABLE = setOf(LiveKind.TIMER, LiveKind.RECORDING)
        private val PAUSE_WORDS = setOf("השהה", "השהיה", "pause")
        private val STOP_WORDS = PAUSE_WORDS + setOf("עצור", "עצירה", "סיים", "סיום", "בטל", "ביטול", "stop", "cancel", "end")
        private val WORD_SPLIT = Regex("[^\\p{L}]+")
        const val PAUSED_LINGER_MS = 30_000L
        const val RESUMABLE_MS = 30 * 60_000L
        const val DEMO_MS = 12_000L

        fun formatMinutes(m: Int): String = if (m < 60) "$m דק׳" else "${m / 60} שע׳ ${m % 60} דק׳".replace(" 0 דק׳", "")
        private const val MAX_QUEUE = 5
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
