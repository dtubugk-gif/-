package com.neonswarm.game

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Hosts the game on a dedicated render thread that paints with a hardware-accelerated canvas. */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val prefs = Prefs(context)
    private val sfx = Sfx(context, prefs)
    private val haptics = Haptics(context, prefs)
    private val game = Game(sfx, haptics, prefs)

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var activePointer = -1

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
        setOnApplyWindowInsetsListener { _, insets ->
            val top = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout?.safeInsetTop ?: 0 else 0
            game.setTopInset(top)
            insets
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "game-loop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        game.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            thread?.join(1000)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val surface = holder.surface
            if (surface == null || !surface.isValid) {
                Thread.sleep(16)
                continue
            }
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (_: Exception) {
                null
            }
            if (canvas == null) {
                Thread.sleep(16)
                continue
            }
            try {
                val now = System.nanoTime()
                val dt = (now - last) / 1_000_000_000f
                last = now
                game.step(dt)
                game.render(canvas)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = e.getPointerId(0)
                game.touch(Game.EV_DOWN, e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activePointer)
                if (i >= 0) game.touch(Game.EV_MOVE, e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // If the steering finger lifts while another is still down, hand control to that one.
                if (e.getPointerId(e.actionIndex) == activePointer) {
                    val next = if (e.actionIndex == 0) 1 else 0
                    activePointer = e.getPointerId(next)
                    game.touch(Game.EV_REANCHOR, e.getX(next), e.getY(next))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointer = -1
                game.touch(Game.EV_UP, e.x, e.y)
            }
        }
        return true
    }

    fun onBack(): Boolean = game.back()

    fun pause() {
        game.systemPause()
        sfx.onPause()
    }

    fun resume() {
        sfx.onResume()
    }

    fun release() {
        sfx.release()
    }
}
