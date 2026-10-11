package com.neonswarm.game

import android.content.Context

/** Settings and records, cached in memory so the game loop never touches disk on reads. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("neon_swarm", Context.MODE_PRIVATE)

    @Volatile
    var sound: Boolean = sp.getBoolean("sound", true)
        set(value) {
            field = value
            sp.edit().putBoolean("sound", value).apply()
        }

    @Volatile
    var vibration: Boolean = sp.getBoolean("vibration", true)
        set(value) {
            field = value
            sp.edit().putBoolean("vibration", value).apply()
        }

    var best: Long = sp.getLong("best", 0L)
        set(value) {
            field = value
            sp.edit().putLong("best", value).apply()
        }

    var bestWave: Int = sp.getInt("bestWave", 0)
        set(value) {
            field = value
            sp.edit().putInt("bestWave", value).apply()
        }

    var games: Int = sp.getInt("games", 0)
        set(value) {
            field = value
            sp.edit().putInt("games", value).apply()
        }
}
