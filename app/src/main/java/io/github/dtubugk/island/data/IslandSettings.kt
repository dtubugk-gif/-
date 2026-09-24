package io.github.dtubugk.island.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TextSide { RIGHT, LEFT }

/** Everything the user can tune. Sizes are in dp so they survive display-size changes. */
data class IslandConfig(
    val visible: Boolean = true,
    val text: String = DEFAULT_TEXT,
    val textSide: TextSide = TextSide.RIGHT,
    val colorIndex: Int = 0,
    val widthDp: Float = DEFAULT_WIDTH_DP,
    val heightDp: Float = DEFAULT_HEIGHT_DP,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val expandOnTap: Boolean = true,
    val chargingAnimation: Boolean = true,
    val haptics: Boolean = true,
    /** Now playing: artwork and waveform, controls when opened. */
    val music: Boolean = true,
    /** New messages pop the island open for a moment. */
    val notifications: Boolean = true,
    /** Show the message text, not just the sender. Never shown on the lock screen. */
    val notificationText: Boolean = true,
    /** Ongoing calls and timers live in the island. */
    val liveActivities: Boolean = true,
    /** Silent mode, Do Not Disturb, headphones, low battery. */
    val systemAlerts: Boolean = true,
    /** Stretch the island over Samsung's own status-bar chip for the same song or call. */
    val absorbChip: Boolean = true,
) {
    companion object {
        const val DEFAULT_TEXT = "א"
        const val MAX_TEXT_LENGTH = 4
        const val DEFAULT_WIDTH_DP = 96f
        const val DEFAULT_HEIGHT_DP = 30f
        const val MIN_WIDTH_DP = 64f
        const val MAX_WIDTH_DP = 220f
        const val MIN_HEIGHT_DP = 22f
        const val MAX_HEIGHT_DP = 44f
        const val MAX_OFFSET_X_DP = 60f
        const val MAX_OFFSET_Y_DP = 16f
    }
}

/** Glyph colors offered in the app. [GRADIENT] paints the text blue-to-pink. */
object IslandColors {
    // Fully transparent black: never a real swatch (opaque white is -1 as an Int, so -1 won't do).
    const val GRADIENT = 0
    val swatches: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // white
        0xFFFF6FA3.toInt(), // pink
        0xFF6EB6FF.toInt(), // sky
        0xFFFFD166.toInt(), // gold
        0xFF5CE1A6.toInt(), // mint
        0xFFB69CFF.toInt(), // lavender
        GRADIENT,
    )
    const val GRADIENT_START = 0xFF6E8BFF.toInt()
    const val GRADIENT_END = 0xFFFF6FA3.toInt()

    fun of(index: Int): Int = swatches.getOrElse(index) { swatches[0] }
}

/** One-shot requests from the app screen: show a sample of each island feature. */
enum class IslandCommand { EXPANDED, MUSIC, CALL, TIMER, MESSAGE, SILENT, CHARGING }

/**
 * Process-wide settings store. The app screen and the accessibility service live in the same
 * process, so a SharedPreferences listener gives the island live updates while sliders move.
 */
object IslandSettings {
    private const val PREFS = "island"
    private lateinit var prefs: SharedPreferences
    private val _config = MutableStateFlow(IslandConfig())
    val config: StateFlow<IslandConfig> = _config.asStateFlow()

    private val _commands = MutableSharedFlow<IslandCommand>(extraBufferCapacity = 4)
    val commands: SharedFlow<IslandCommand> = _commands.asSharedFlow()

    // Held strongly: SharedPreferences only keeps weak references to listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> _config.value = read(p) }

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _config.value = read(prefs)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun update(transform: (IslandConfig) -> IslandConfig) {
        val next = transform(_config.value)
        _config.value = next
        prefs.edit()
            .putBoolean("visible", next.visible)
            .putString("text", next.text)
            .putString("textSide", next.textSide.name)
            .putInt("colorIndex", next.colorIndex)
            .putFloat("widthDp", next.widthDp)
            .putFloat("heightDp", next.heightDp)
            .putFloat("offsetXDp", next.offsetXDp)
            .putFloat("offsetYDp", next.offsetYDp)
            .putBoolean("expandOnTap", next.expandOnTap)
            .putBoolean("chargingAnimation", next.chargingAnimation)
            .putBoolean("haptics", next.haptics)
            .putBoolean("music", next.music)
            .putBoolean("notifications", next.notifications)
            .putBoolean("notificationText", next.notificationText)
            .putBoolean("liveActivities", next.liveActivities)
            .putBoolean("systemAlerts", next.systemAlerts)
            .putBoolean("absorbChip", next.absorbChip)
            .apply()
    }

    fun send(command: IslandCommand) {
        _commands.tryEmit(command)
    }

    private fun read(p: SharedPreferences): IslandConfig {
        val d = IslandConfig()
        return IslandConfig(
            visible = p.getBoolean("visible", d.visible),
            text = p.getString("text", d.text) ?: d.text,
            textSide = runCatching { TextSide.valueOf(p.getString("textSide", null)!!) }.getOrDefault(d.textSide),
            colorIndex = p.getInt("colorIndex", d.colorIndex),
            widthDp = p.getFloat("widthDp", d.widthDp),
            heightDp = p.getFloat("heightDp", d.heightDp),
            offsetXDp = p.getFloat("offsetXDp", d.offsetXDp),
            offsetYDp = p.getFloat("offsetYDp", d.offsetYDp),
            expandOnTap = p.getBoolean("expandOnTap", d.expandOnTap),
            chargingAnimation = p.getBoolean("chargingAnimation", d.chargingAnimation),
            haptics = p.getBoolean("haptics", d.haptics),
            music = p.getBoolean("music", d.music),
            notifications = p.getBoolean("notifications", d.notifications),
            notificationText = p.getBoolean("notificationText", d.notificationText),
            liveActivities = p.getBoolean("liveActivities", d.liveActivities),
            systemAlerts = p.getBoolean("systemAlerts", d.systemAlerts),
            absorbChip = p.getBoolean("absorbChip", d.absorbChip),
        )
    }
}
