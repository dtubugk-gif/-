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
                val title = intent.getStringExtra("title")?.trim()?.take(60).orEmpty()
                if (title.isEmpty()) return
                val text = intent.getStringExtra("text")?.trim()?.take(80).orEmpty()
                val color = runCatching { Color.parseColor(intent.getStringExtra("color") ?: "") }.getOrDefault(IslandPainter.BLUE)
                val duration = intent.getLongExtra("duration", 4000L).coerceIn(0L, IslandDirector.MAX_CUSTOM_MS)
                LiveBus.peek(Peek.Custom(id, title, text, color or 0xFF000000.toInt(), duration, intent.getBooleanExtra("card", false)))
            }
            ACTION_HIDE -> LiveBus.hideCustom(id)
        }
    }

    companion object {
        const val ACTION_SHOW = "io.github.dtubugk.island.SHOW"
        const val ACTION_HIDE = "io.github.dtubugk.island.HIDE"
    }
}
