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
    /** One controls object per session, so an unchanged track compares equal and doesn't redraw. */
    private val controls = HashMap<android.media.session.MediaSession.Token, MediaControls>()
    /** App icons by package, so a re-posted activity compares equal and doesn't churn the island. */
    private val appIcons = HashMap<String, Drawable?>()
    /** System status notifications (hotspot, VPN) currently up, by key. */
    private val statusKeys = HashMap<String, Int>()

    /** Hotspot or VPN, told by the system's own ongoing notification. */
    private fun statusKind(sbn: StatusBarNotification): Int? {
        if (!sbn.isOngoing || sbn.packageName !in SYSTEM_PACKAGES) return null
        val e = sbn.notification.extras
        val text = (e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty() + " " +
            e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()).lowercase()
        // VPN is read from the network itself (the service), not from notification wording:
        // the only system VPN notification is "Always-on VPN disconnected".
        return if (HOTSPOT_WORDS.any { it in text }) STATUS_HOTSPOT else null
    }

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
        // A notification listener may switch Do Not Disturb without the separate policy permission.
        LiveBus.dndSetter = { on ->
            runCatching {
                requestInterruptionFilter(if (on) INTERRUPTION_FILTER_PRIORITY else INTERRUPTION_FILTER_ALL)
            }
        }
        val msm = getSystemService(MediaSessionManager::class.java)
        sessions = msm
        runCatching {
            msm?.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, javaClass), handler)
        }
        runCatching { refreshSessions() }
        statusKeys.clear()
        runCatching { activeNotifications }.getOrNull()?.forEach {
            seen[it.key] = it.notification.`when` to contentHash(it.notification)
            // An already-running hotspot: no notice now, but its later "off" is judged right.
            statusKind(it)?.let { k -> statusKeys[it.key] = k }
        }
        runCatching { publishActivities() }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controllers = emptyList()
        controller = null
        statusKeys.clear()
        seen.clear()
        LiveBus.canceller = null
        LiveBus.dndSetter = null
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
        statusKind(sbn)?.let { kind ->
            if (sbn.key !in statusKeys) {
                statusKeys[sbn.key] = kind
                if (kind == STATUS_HOTSPOT) LiveBus.peek(Peek.Hotspot(true))
            }
            return
        }
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
                // Clearing from here counts as a swipe-away; if the app listens for that (deleteIntent),
                // leave it to the app, which clears its own notification when the chat opens.
                autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0 && n.deleteIntent == null,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        runCatching {
            statusKeys.remove(sbn.key)?.let { kind ->
                if (kind == STATUS_HOTSPOT) LiveBus.peek(Peek.Hotspot(false))
            }
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
            if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) {
                // New text still counts for chats (a new message in the same conversation), but a
                // silently refreshed status (delivery, score, download) stays silent, as on the phone.
                val chat = n.category == Notification.CATEGORY_MESSAGE || n.extras.containsKey(Notification.EXTRA_MESSAGES)
                if (!chat || previousContent == contentHash(n)) return false
            }
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

    private fun isLiveCandidate(sbn: StatusBarNotification): Boolean = liveKind(sbn) != null

    /** What kind of live activity a notification is, or null when it is an ordinary one. */
    private fun liveKind(sbn: StatusBarNotification): LiveKind? {
        val n = sbn.notification
        val e = n.extras
        return when {
            n.category == Notification.CATEGORY_CALL -> LiveKind.CALL
            // A ringing alarm is ongoing or takes the screen; "alarm in 30 minutes" is neither.
            n.category == Notification.CATEGORY_ALARM && (sbn.isOngoing || n.fullScreenIntent != null) -> LiveKind.ALARM
            n.category == Notification.CATEGORY_NAVIGATION || (sbn.isOngoing && sbn.packageName in NAV_PACKAGES) -> LiveKind.NAVIGATION
            // Screen or voice recording: a red dot and the running time, like the iPhone's.
            sbn.isOngoing && e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) && sbn.packageName in RECORDER_PACKAGES -> LiveKind.RECORDING
            sbn.isOngoing && e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) -> LiveKind.TIMER
            // A determinate progress bar: download, upload, delivery or ride on its way.
            sbn.isOngoing && e.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 && !e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) -> LiveKind.PROGRESS
            // Android 16 Live Updates (rides, deliveries, scores): apps ask for promotion with this extra.
            sbn.isOngoing && e.getBoolean(EXTRA_REQUEST_PROMOTED_ONGOING) -> LiveKind.PROGRESS
            else -> null
        }
    }

    private fun publishActivities() {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.packageName != packageName && isLiveCandidate(it) && !it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }
            .mapNotNull { sbn ->
                val n = sbn.notification
                val extras = n.extras
                val kind = liveKind(sbn) ?: return@mapNotNull null
                val chrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
                val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX)
                LiveActivity(
                    key = sbn.key,
                    kind = kind,
                    icon = when (kind) {
                        LiveKind.NAVIGATION -> largeIcon(n)
                        LiveKind.PROGRESS -> appIcons.getOrPut(sbn.packageName) { appIcon(sbn.packageName) }
                        else -> null
                    },
                    progress = if (kind == LiveKind.PROGRESS && max > 0) (extras.getInt(Notification.EXTRA_PROGRESS).toFloat() / max).coerceIn(0f, 1f) else -1f,
                    subText = (extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT) ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty(),
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
            // In priority order: calls, alarms, guidance, timers, progress.
            .sortedBy { it.kind.ordinal }
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
        // The system hands out fresh MediaController objects on every call, and a callback lives
        // only as long as the object it was registered on. Keep the registered ones.
        val keep = list.map { it.sessionToken }.toSet()
        val old = controllers.associateBy { it.sessionToken }
        controllers.filter { it.sessionToken !in keep }.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controls.keys.retainAll(keep)
        controllers = list.map { c ->
            old[c.sessionToken] ?: c.also { runCatching { it.registerCallback(controllerCallback, handler) } }
        }
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
                controls = controls.getOrPut(c.sessionToken) {
                    object : MediaControls {
                        override fun playPause() {
                            runCatching {
                                val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
                                if (playing) c.transportControls.pause() else c.transportControls.play()
                            }
                        }
                        override fun next() {
                            runCatching { c.transportControls.skipToNext() }
                        }
                        override fun previous() {
                            runCatching { c.transportControls.skipToPrevious() }
                        }
                    }
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
        // Every play/pause re-sends the same cover: recognize it by the track, not by its pixels,
        // before paying for a copy or a Palette pass.
        val trackKey = listOf(
            meta.getString(MediaMetadata.METADATA_KEY_TITLE),
            meta.getString(MediaMetadata.METADATA_KEY_ARTIST),
            meta.getString(MediaMetadata.METADATA_KEY_ALBUM),
            original.width, original.height,
        ).hashCode().toLong()
        artCache?.let { (key, cached, accent) -> if (key == trackKey) return cached to accent }
        // Some players hand over GPU-only (HARDWARE) bitmaps: their pixels can't be read directly.
        val source = if (original.config == Bitmap.Config.HARDWARE) original.copy(Bitmap.Config.ARGB_8888, false) ?: return null else original
        val bitmap = scaled(source)
        val palette = runCatching { Palette.from(bitmap).generate() }.getOrNull()
        val swatch = palette?.vibrantSwatch ?: palette?.lightVibrantSwatch ?: palette?.dominantSwatch
        val accent = swatch?.rgb?.let(::brighten) ?: DEFAULT_ACCENT
        artCache = Triple(trackKey, bitmap, accent)
        return bitmap to accent
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
    private fun appLabel(pkg: String): String {
        labels[pkg]?.let { return it }
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            ?: return pkg
        labels[pkg] = label
        return label
    }

    /** App names follow the phone's language. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        labels.clear()
    }

    private fun appIcon(pkg: String): Drawable? = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()

    private fun largeIcon(n: Notification): Drawable? = runCatching { n.getLargeIcon()?.loadDrawable(this) }.getOrNull()

    private companion object {
        const val ART_PX = 160
        val DEFAULT_ACCENT = 0xFFFF7EB0.toInt()
        val NAV_PACKAGES = setOf("com.google.android.apps.maps", "com.waze", "com.here.app.maps", "com.sygic.aura")
        val SYSTEM_PACKAGES = setOf("android", "com.android.systemui", "com.android.settings", "com.android.networkstack.tethering", "com.google.android.networkstack.tethering", "com.samsung.android.net.wifi.wifiguider", "com.sec.android.app.wlantest", "com.samsung.android.app.telephonyui")
        val HOTSPOT_WORDS = listOf("hotspot", "נקודה חמה", "tethering", "שיתוף אינטרנט")
        const val STATUS_HOTSPOT = 1
        // Android 16 keys, spelled out so the app builds against older SDKs too.
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
        val RECORDER_PACKAGES = setOf("com.samsung.android.app.smartcapture", "com.sec.android.app.voicenote", "com.google.android.apps.recorder")
        val QUIET_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
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
