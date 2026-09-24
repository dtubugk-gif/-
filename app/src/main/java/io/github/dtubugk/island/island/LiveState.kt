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

enum class LiveKind { CALL, TIMER }

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
    ) : Peek()
    data class Charging(val level: Int) : Peek()
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

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
        if (!connected) {
            _media.value = null
            _activities.value = emptyList()
        }
    }
}
