package com.neonswarm.game

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

/**
 * Drives the real engine headlessly: a bot plays several minutes of game time (through a boss
 * fight) while every frame is simulated, and key moments are rendered to PNG screenshots in
 * app/build/screens. Fails on any exception or if the game doesn't progress.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameSimulationTest {

    private val w = 1080
    private val h = 2340
    private val dt = 1f / 60f
    private lateinit var game: Game
    private val outDir = File("build/screens").apply { mkdirs() }

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(name: String): T {
        val f = Game::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(game) as T
    }

    private fun set(name: String, v: Any) {
        val f = Game::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(game, v)
    }

    private fun shot(name: String) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        game.render(Canvas(bmp))
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun playsThroughBossAndGameOver() {
        val ctx = RuntimeEnvironment.getApplication()
        val prefs = Prefs(ctx)
        game = Game(Sfx(ctx, prefs), Haptics(ctx, prefs), prefs)
        game.resize(w, h)
        game.setTopInset(90)
        repeat(90) { game.step(dt) }
        assertEquals(Game.State.MENU, game.state)
        shot("01_menu")

        game.touch(Game.EV_DOWN, 540f, 1900f)
        game.step(dt)
        assertEquals(Game.State.PLAYING, game.state)

        var t = 0f
        var tookAction = false
        var tookOverdrive = false
        var tookBoss = false
        var tookLate = false
        var maxWave = 0
        var maxParticles = 0
        while (t < 300f) {
            t += dt
            game.touch(Game.EV_MOVE, 540f + sin(t * 0.9f) * 420f, 1900f + sin(t * 0.37f) * 160f)
            set("lives", 3) // keep the bot alive so the run reaches the boss
            game.step(dt)
            val wave: Int = get("wave")
            maxWave = maxOf(maxWave, wave)
            val parts: Array<Particle> = get("particles")
            maxParticles = maxOf(maxParticles, parts.count { it.alive })

            if (!tookAction && t > 14f) {
                set("invuln", 0f); shot("02_action"); tookAction = true
            }
            val od: Float = get("overdrive")
            if (!tookOverdrive && od in 3f..4.5f) {
                set("invuln", 0f); shot("03_overdrive"); tookOverdrive = true
            }
            val boss: Enemy? = get("boss")
            if (!tookBoss && boss != null && boss.entered && boss.t > 9f) {
                set("invuln", 0f); shot("04_boss"); tookBoss = true
            }
            if (!tookLate && wave >= 7 && t > 200f) {
                set("invuln", 0f); shot("05_late_wave"); tookLate = true
            }
            if (maxWave >= 8 && tookBoss) break
        }
        println("simulated ${t}s, reached wave $maxWave, peak particles $maxParticles")
        assertTrue("game should progress past the first boss (wave $maxWave)", maxWave >= 6)
        assertTrue("boss fight should have happened", tookBoss)

        game.systemPause()
        game.step(dt)
        assertEquals(Game.State.PAUSED, game.state)
        shot("06_pause")
        game.touch(Game.EV_DOWN, 540f, 1300f)
        game.step(dt)
        assertEquals(Game.State.PLAYING, game.state)

        // Let the bot die for real.
        var guard = 0
        while (game.state == Game.State.PLAYING && guard++ < 60 * 120) {
            set("lives", 1)
            set("invuln", 0f)
            set("shield", 0f)
            set("overdrive", 0f)
            set("pty", 700f)
            game.step(dt)
        }
        while (game.state != Game.State.GAME_OVER && guard++ < 60 * 130) game.step(dt)
        assertEquals(Game.State.GAME_OVER, game.state)
        repeat(90) { game.step(dt) }
        shot("07_game_over")

        game.touch(Game.EV_DOWN, 540f, 1300f)
        game.step(dt)
        assertEquals(Game.State.PLAYING, game.state)
    }
}
