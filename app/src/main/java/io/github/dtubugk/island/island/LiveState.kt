package io.github.dtubugk.island.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

interface MediaControls {
    fun playPause()
    fun next()
    fun previous()
}

/** What is playing right now, as reported by the app's media session. */
data class MediaState(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    /** Accent picked from the artwork; drives the waveform color. */
    val accent: Int,
    val playing: Boolean,
    val positionMs: Long,
    /** [SystemClock.elapsedRealtime] when [positionMs] was sampled. */
    val positionSampledAt: Long,
    val speed: Float,
    val durationMs: Long,
    val controls: MediaControls?,
    val openApp: PendingIntent?,
) {
    fun positionNow(): Long {
        if (!playing) return positionMs
        val elapsed = ((SystemClock.elapsedRealtime() - positionSampledAt) * speed).toLong()
        val p = positionMs + elapsed
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** In priority order: what the island shows first when several are live. */
enum class LiveKind { CALL, ALARM, NAVIGATION, RECORDING, TIMER, PROGRESS }

/** Things the island can do to the phone itself, from its cards. */
enum class IslandAction { SPEAKER, MUTE, AIRPLANE, FLASHLIGHT, DND, SCREENSHOT, LOCK, SETTINGS }

/** Toggles the island draws as on/off. */
data class SystemState(
    val flashlight: Boolean = false,
    val doNotDisturb: Boolean = false,
    val speaker: Boolean = false,
    val muted: Boolean = false,
)

/** A button from the call's or timer's own notification. Null intent: a sample with nothing to do. */
data class LiveAction(val title: String, val intent: PendingIntent?)

/** Tells call buttons apart by their label, in Hebrew or English, to color and auto-open. */
object CallActions {
    private val DECLINE = listOf("נתק", "דחה", "דחיי", "סיים", "סיום", "בטל", "decline", "end", "hang", "reject", "cancel", "stop")
    private val ACCEPT = listOf("ענה", "מענה", "answer", "accept")

    fun isDecline(title: String) = title.lowercase().let { t -> DECLINE.any { it in t } }
    fun isAccept(title: String) = title.lowercase().let { t -> ACCEPT.any { it in t } }
}

/** An ongoing call or timer, mirrored from its notification. */
data class LiveActivity(
    val key: String,
    val kind: LiveKind,
    val appLabel: String,
    val title: String,
    val text: String,
    /** Chronometer anchor in wall-clock ms, or 0 when the notification shows no running time. */
    val chronometerBase: Long,
    val countDown: Boolean,
    val openApp: PendingIntent?,
    val actions: List<LiveAction>,
    /** Navigation: the maneuver arrow; progress: the app icon. */
    val icon: Drawable? = null,
    /** 0..1 for [LiveKind.PROGRESS], else -1. */
    val progress: Float = -1f,
)

/** Short-lived events that pop the island open for a moment, like on the iPhone. */
sealed class Peek {
    data class Message(
        val key: String,
        val appLabel: String,
        val icon: Drawable?,
        val title: String,
        val text: String,
        val open: PendingIntent?,
        /** Opening it clears it from the notification shade, as tapping it there would. */
        val autoCancel: Boolean = false,
    ) : Peek()
    /** [minutesLeft] until full, when the phone can tell; else -1. */
    data class Charging(val level: Int, val minutesLeft: Int = -1) : Peek()
    data class BatteryFull(val unit: Unit = Unit) : Peek()
    data class PowerSave(val on: Boolean, val level: Int) : Peek()
    data class LowBattery(val level: Int) : Peek()
    enum class RingerMode { SILENT, VIBRATE, SOUND }
    data class Ringer(val mode: RingerMode) : Peek()
    data class DoNotDisturb(val on: Boolean) : Peek()
    data class Headphones(val name: String) : Peek()
}

/**
 * Hand-off point between the notification listener (media, calls, timers, messages) and the
 * accessibility service that draws the island. Both run in the app's single process.
 */
object LiveBus {
    private val _media = MutableStateFlow<MediaState?>(null)
    val media: StateFlow<MediaState?> = _media.asStateFlow()

    private val _activities = MutableStateFlow<List<LiveActivity>>(emptyList())
    val activities: StateFlow<List<LiveActivity>> = _activities.asStateFlow()

    private val _peeks = MutableSharedFlow<Peek>(extraBufferCapacity = 8)
    val peeks: SharedFlow<Peek> = _peeks.asSharedFlow()

    private val _removed = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Keys of notifications that left the shade (read, dismissed, cancelled by their app). */
    val removed: SharedFlow<String> = _removed.asSharedFlow()

    /** Set by the listener while connected: removes a notification from the shade. */
    @Volatile
    var canceller: ((String) -> Unit)? = null

    /** Set by the listener while connected: Do Not Disturb on/off through the listener's own right. */
    @Volatile
    var dndSetter: ((Boolean) -> Unit)? = null

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    fun setMedia(state: MediaState?) {
        _media.value = state
    }

    fun setActivities(list: List<LiveActivity>) {
        _activities.value = list
    }

    fun peek(p: Peek) {
        _peeks.tryEmit(p)
    }

    fun notificationRemoved(key: String) {
        _removed.tryEmit(key)
    }

    fun cancelNotification(key: String) {
        canceller?.invoke(key)
    }

    fun setDoNotDisturb(on: Boolean) {
        dndSetter?.invoke(on)
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
        if (!connected) {
            _media.value = null
            _activities.value = emptyList()
        }
    }
}
