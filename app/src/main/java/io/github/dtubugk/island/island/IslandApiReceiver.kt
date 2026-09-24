package io.github.dtubugk.island.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import io.github.dtubugk.island.data.IslandSettings

/**
 * The island's open API: any app or automation (Tasker, MacroDroid, Automate) shows its own
 * island with a broadcast, no SDK needed.
 *
 *   action io.github.dtubugk.island.SHOW
 *     id        String   updates or replaces an island with the same id (default "default")
 *     title     String   one short line, required
 *     text      String   a value or a second line
 *     color     String   "#RRGGBB" for the glyph (default blue)
 *     duration  Long     milliseconds; 0 keeps it until HIDE or a dismissal (default 4000)
 *     card      Boolean  true for a two-line card instead of a pill
 *   action io.github.dtubugk.island.HIDE
 *     id        String
 *
 * It only displays text the user chose to let apps show (a switch in the settings).
 */
class IslandApiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        IslandSettings.init(context)
        if (!IslandSettings.config.value.api) return
        val id = intent.getStringExtra("id")?.take(64)?.ifBlank { null } ?: "default"
        when (intent.action) {
            ACTION_SHOW -> {
                // Automation apps (Tasker, MacroDroid) send every extra as text, so each value is
                // read whatever its type.
                val extras = intent.extras
                val title = extras?.get("title")?.toString()?.trim()?.take(60).orEmpty()
                if (title.isEmpty()) return
                val text = extras?.get("text")?.toString()?.trim()?.take(80).orEmpty()
                val color = when (val c = extras?.get("color")) {
                    is Number -> c.toInt()
                    is String -> runCatching { Color.parseColor(c.trim()) }.getOrDefault(IslandPainter.BLUE)
                    else -> IslandPainter.BLUE
                }
                val duration = when (val d = extras?.get("duration")) {
                    is Number -> d.toLong()
                    is String -> d.trim().toLongOrNull() ?: 4000L
                    else -> 4000L
                }.coerceIn(0L, IslandDirector.MAX_CUSTOM_MS)
                val card = when (val c = extras?.get("card")) {
                    is Boolean -> c
                    is String -> c.trim().lowercase() in setOf("true", "1", "yes")
                    is Number -> c.toInt() != 0
                    else -> false
                }
                LiveBus.peek(Peek.Custom(id, title, text, color or 0xFF000000.toInt(), duration, card))
            }
            ACTION_HIDE -> LiveBus.hideCustom(id)
        }
    }

    companion object {
        const val ACTION_SHOW = "io.github.dtubugk.island.SHOW"
        const val ACTION_HIDE = "io.github.dtubugk.island.HIDE"
    }
}
