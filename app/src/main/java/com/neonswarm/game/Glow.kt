package com.neonswarm.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.SparseArray

/** Pre-rendered radial glow sprites, one per colour, drawn with additive blending. */
object Glow {
    private const val SIZE = 64
    private val hot = SparseArray<Bitmap>()
    private val soft = SparseArray<Bitmap>()

    /** Glow with a white-hot core: particles, bullets. */
    fun hot(color: Int): Bitmap = hot.get(color) ?: build(color, true).also { hot.put(color, it) }

    /** Coloured halo with no core: auras behind ships and enemies. */
    fun soft(color: Int): Bitmap = soft.get(color) ?: build(color, false).also { soft.put(color, it) }

    private fun build(color: Int, core: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = SIZE / 2f
        val rgb = color and 0x00FFFFFF
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = if (core) {
            RadialGradient(
                r, r, r,
                intArrayOf(-0x1, -0x1000000 or rgb, 0x70000000 or rgb, 0x18000000 or rgb, rgb),
                floatArrayOf(0f, 0.14f, 0.38f, 0.68f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            RadialGradient(
                r, r, r,
                intArrayOf(0x66000000 or rgb, 0x33000000 or rgb, 0x0C000000 or rgb, rgb),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(r, r, r, p)
        return bmp
    }
}
