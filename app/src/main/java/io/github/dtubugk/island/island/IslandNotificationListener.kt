package io.github.dtubugk.island.island

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.palette.graphics.Palette
import kotlin.math.max

/**
 * Feeds the island with what the iPhone island shows: now playing, ongoing calls and timers,
 * and incoming messages. Needs "Notification access"; nothing leaves the phone.
 */
class IslandNotificationListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessions: MediaSessionManager? = null
    /** Every active session is watched, so whichever app starts playing takes the island. */
    private var controllers: List<MediaController> = emptyList()
    private var controller: MediaController? = null
    /** Per notification key: its last `when` and a hash of its title and text. */
    private val seen = HashMap<String, Pair<Long, Int>>()
    private val labels = HashMap<String, String>()
    /** Keys currently shown as calls/timers, so an update that stops being one still refreshes them. */
    private var liveKeys: Set<String> = emptySet()
    private var artCache: Triple<Long, Bitmap, Int>? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { runCatching { watch(it.orEmpty()) } }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            runCatching { pickController() }
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            runCatching { pickController() }
        }
        override fun onSessionDestroyed() {
            runCatching { refreshSessions() }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        LiveBus.setListenerConnected(true)
        LiveBus.canceller = { key -> runCatching { cancelNotification(key) } }
        val msm = getSystemService(MediaSessionManager::class.java)
        sessions = msm
        runCatching {
            msm?.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, javaClass), handler)
        }
        runCatching { refreshSessions() }
        runCatching { activeNotifications }.getOrNull()?.forEach { seen[it.key] = it.notification.`when` to contentHash(it.notification) }
        runCatching { publishActivities() }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controllers = emptyList()
        controller = null
        LiveBus.canceller = null
        LiveBus.setListenerConnected(false)
        super.onListenerDisconnected()
    }

    // Every system callback is guarded: one odd notification must never take the island down,
    // since this listener shares its process with the island itself.
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        runCatching { handlePosted(sbn) }
    }

    private fun handlePosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        // A ringing call can turn into a missed-call notice under the same key: refresh then too.
        if (isLiveCandidate(sbn) || sbn.key in liveKeys) publishActivities()
        val previous = seen.put(sbn.key, n.`when` to contentHash(n))
        if (!shouldPeek(sbn, previous)) return
        val extras = n.extras
        // EXTRA_TITLE keeps the sender in group chats ("Family: Dana"); the bare group name does not.
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        LiveBus.peek(
            Peek.Message(
                key = sbn.key,
                appLabel = appLabel(sbn.packageName),
                icon = largeIcon(n) ?: appIcon(sbn.packageName),
                title = title.ifBlank { appLabel(sbn.packageName) },
                text = text.lineSequence().firstOrNull().orEmpty(),
                open = n.contentIntent,
                autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runCatching {
            seen.remove(sbn.key)
            // Read or dismissed elsewhere: the island must not show it later from its queue.
            LiveBus.notificationRemoved(sbn.key)
            if (isLiveCandidate(sbn) || sbn.key in liveKeys) publishActivities()
        }
    }

    // --- messages -----------------------------------------------------------------------------

    /** Only real, alerting, new messages: no ongoing, silent, summary, media or repeat updates. */
    private fun shouldPeek(sbn: StatusBarNotification, previous: Pair<Long, Int>?): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName) return false
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        if (n.category in QUIET_CATEGORIES) return false
        // Same timestamp: a quiet refresh (read state, typing). "Alert once" with unchanged text: the
        // same message re-posted. New text with a new timestamp is a new message in the same chat.
        if (previous != null) {
            val (previousWhen, previousContent) = previous
            if (previousWhen == n.`when`) return false
            if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && previousContent == contentHash(n)) return false
        }
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) == true) {
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT) return false
            if (!ranking.matchesInterruptionFilter()) return false
        }
        return true
    }

    private fun contentHash(n: Notification): Int {
        val e = n.extras
        return (e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty() + '\u0000' +
            e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()).hashCode()
    }

    // --- calls and timers ---------------------------------------------------------------------

    private fun isLiveCandidate(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        return n.category == Notification.CATEGORY_CALL ||
            (sbn.isOngoing && n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
    }

    private fun publishActivities() {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.packageName != packageName && isLiveCandidate(it) && !it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }
            .map { sbn ->
                val n = sbn.notification
                val extras = n.extras
                val chrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
                LiveActivity(
                    key = sbn.key,
                    kind = if (n.category == Notification.CATEGORY_CALL) LiveKind.CALL else LiveKind.TIMER,
                    appLabel = appLabel(sbn.packageName),
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    chronometerBase = if (chrono) n.`when` else 0L,
                    countDown = chrono && extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
                    openApp = n.contentIntent,
                    actions = n.actions.orEmpty().mapNotNull { a ->
                        val intent = a.actionIntent ?: return@mapNotNull null
                        val label = a.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        LiveAction(label, intent)
                    },
                )
            }
            // Ringing or ongoing calls first, then the most recent timer.
            .sortedWith(compareBy<LiveActivity> { it.kind != LiveKind.CALL })
        liveKeys = list.mapTo(HashSet()) { it.key }
        LiveBus.setActivities(list)
    }

    // --- media --------------------------------------------------------------------------------

    private fun refreshSessions() {
        val list = runCatching { sessions?.getActiveSessions(ComponentName(this, javaClass)) }.getOrNull().orEmpty()
        watch(list)
    }

    /** Listens to every active session: the list itself doesn't change when another app starts playing. */
    private fun watch(list: List<MediaController>) {
        val keep = list.map { it.sessionToken }.toSet()
        controllers.filter { it.sessionToken !in keep }.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        val known = controllers.map { it.sessionToken }.toSet()
        list.filter { it.sessionToken !in known }.forEach { runCatching { it.registerCallback(controllerCallback, handler) } }
        controllers = list
        pickController()
    }

    /** Prefer whatever is playing; otherwise the most recent session with a track loaded. */
    private fun pickController() {
        controller = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_BUFFERING }
            ?: controllers.firstOrNull { it.metadata != null }
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        val meta = c?.metadata
        if (c == null || meta == null) {
            LiveBus.setMedia(null)
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) {
            LiveBus.setMedia(null)
            return
        }
        val state = c.playbackState
        val art = artwork(meta)
        val (bitmap, accent) = art ?: (null to DEFAULT_ACCENT)
        LiveBus.setMedia(
            MediaState(
                packageName = c.packageName,
                appLabel = appLabel(c.packageName),
                title = title,
                artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                art = bitmap,
                accent = accent,
                playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
                positionMs = max(state?.position ?: 0L, 0L),
                positionSampledAt = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: android.os.SystemClock.elapsedRealtime(),
                // Buffering still shows the waveform, but the progress bar must not run ahead.
                speed = if (state?.state == PlaybackState.STATE_BUFFERING) 0f else state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                durationMs = max(meta.getLong(MediaMetadata.METADATA_KEY_DURATION), 0L),
                controls = object : MediaControls {
                    override fun playPause() {
                        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
                        if (playing) c.transportControls.pause() else c.transportControls.play()
                    }
                    override fun next() = c.transportControls.skipToNext()
                    override fun previous() = c.transportControls.skipToPrevious()
                },
                openApp = c.sessionActivity,
            ),
        )
    }

    /** Artwork scaled for the island, with a bright accent taken from it for the waveform. */
    private fun artwork(meta: MediaMetadata): Pair<Bitmap, Int>? = runCatching { artworkOrThrow(meta) }.getOrNull()

    private fun artworkOrThrow(meta: MediaMetadata): Pair<Bitmap, Int>? {
        val original = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        if (original.isRecycled || original.width <= 0 || original.height <= 0) return null
        // Some players hand over GPU-only (HARDWARE) bitmaps: their pixels can't be read directly.
        val source = if (original.config == Bitmap.Config.HARDWARE) original.copy(Bitmap.Config.ARGB_8888, false) ?: return null else original
        // Play/pause re-sends the same artwork; only a new cover is worth scaling and analyzing.
        val signature = signature(source)
        artCache?.let { (sig, cached, accent) -> if (sig == signature) return cached to accent }
        val bitmap = scaled(source)
        val palette = runCatching { Palette.from(bitmap).generate() }.getOrNull()
        val swatch = palette?.vibrantSwatch ?: palette?.lightVibrantSwatch ?: palette?.dominantSwatch
        val accent = swatch?.rgb?.let(::brighten) ?: DEFAULT_ACCENT
        artCache = Triple(signature, bitmap, accent)
        return bitmap to accent
    }

    private fun signature(b: Bitmap): Long {
        var h = b.width * 31L + b.height
        for (i in 1..4) for (j in 1..4) h = h * 31 + b.getPixel(b.width * i / 5, b.height * j / 5)
        return h
    }

    private fun scaled(b: Bitmap): Bitmap =
        if (b.width <= ART_PX && b.height <= ART_PX) b else Bitmap.createScaledBitmap(b, ART_PX, ART_PX * b.height / max(b.width, 1), true)

    /** Keeps the waveform legible on black: lift dark accents. */
    private fun brighten(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = max(hsv[2], 0.85f)
        hsv[1] = hsv[1].coerceAtMost(0.75f)
        return Color.HSVToColor(hsv)
    }

    // --- app info -----------------------------------------------------------------------------

    @Suppress("DEPRECATION") // The flags overload is Android 13+; this one works everywhere.
    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private fun appIcon(pkg: String): Drawable? = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()

    private fun largeIcon(n: Notification): Drawable? = runCatching { n.getLargeIcon()?.loadDrawable(this) }.getOrNull()

    private companion object {
        const val ART_PX = 160
        val DEFAULT_ACCENT = 0xFFFF7EB0.toInt()
        val QUIET_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STATUS,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_LOCATION_SHARING,
        )
    }
}
