package com.fontmaker.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Drives the real keyboard and screens on a simulated Android and saves screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private val out = File(System.getProperty("screensOut") ?: "build/screens").apply { mkdirs() }

    private fun pts(vararg xy: Float) = Stroke(xy.toList().chunked(2).map { Pt(it[0], it[1]) }.toMutableList())
    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Double, to: Double, n: Int = 20) =
        Stroke((0..n).map { i -> val a = from + (to - from) * i / n; Pt(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat()) }.toMutableList())
    private fun g(vararg s: Stroke) = Glyph(s.toMutableList())

    /** A small hand-drawn style font, standing in for one drawn on the phone. */
    private fun demoFont(): HandFont {
        val f = HandFont("demo", "My Handwriting", strokeWidth = 0.05f)
        f.glyphs["H"] = g(pts(0.3f, 0.23f, 0.31f, 0.72f), pts(0.62f, 0.22f, 0.6f, 0.72f), pts(0.29f, 0.48f, 0.45f, 0.46f, 0.62f, 0.47f))
        f.glyphs["h"] = g(pts(0.36f, 0.2f, 0.35f, 0.72f), Stroke(mutableListOf(Pt(0.35f, 0.56f), Pt(0.4f, 0.47f), Pt(0.48f, 0.44f), Pt(0.56f, 0.47f), Pt(0.59f, 0.56f), Pt(0.6f, 0.72f))))
        f.glyphs["e"] = g(Stroke((listOf(Pt(0.37f, 0.58f), Pt(0.6f, 0.57f)) + arc(0.49f, 0.58f, 0.12f, 0.14f, 0.0, -1.65 * PI).points).toMutableList()))
        f.glyphs["l"] = g(Stroke(mutableListOf(Pt(0.46f, 0.2f), Pt(0.45f, 0.5f), Pt(0.46f, 0.67f), Pt(0.52f, 0.72f))))
        f.glyphs["o"] = g(arc(0.5f, 0.58f, 0.13f, 0.14f, -PI / 2, 1.55 * PI))
        f.glyphs["w"] = g(Stroke(mutableListOf(Pt(0.28f, 0.44f), Pt(0.36f, 0.72f), Pt(0.45f, 0.5f), Pt(0.54f, 0.72f), Pt(0.63f, 0.44f))))
        f.glyphs["r"] = g(pts(0.4f, 0.44f, 0.4f, 0.72f), Stroke(mutableListOf(Pt(0.4f, 0.56f), Pt(0.44f, 0.48f), Pt(0.5f, 0.44f), Pt(0.58f, 0.45f))))
        f.glyphs["d"] = g(arc(0.45f, 0.58f, 0.12f, 0.14f, -0.1, 2 * PI - 0.3), pts(0.58f, 0.2f, 0.58f, 0.72f))
        f.glyphs["a"] = g(arc(0.46f, 0.58f, 0.12f, 0.14f, -0.2, 2 * PI - 0.2), pts(0.59f, 0.45f, 0.6f, 0.72f))
        f.glyphs["y"] = g(pts(0.36f, 0.44f, 0.49f, 0.71f), Stroke(mutableListOf(Pt(0.62f, 0.44f), Pt(0.5f, 0.72f), Pt(0.42f, 0.86f), Pt(0.34f, 0.88f))))
        f.glyphs["u"] = g(Stroke(mutableListOf(Pt(0.37f, 0.44f), Pt(0.37f, 0.62f), Pt(0.43f, 0.71f), Pt(0.52f, 0.7f), Pt(0.6f, 0.6f))), pts(0.6f, 0.44f, 0.61f, 0.72f))
        f.glyphs["i"] = g(pts(0.5f, 0.45f, 0.5f, 0.72f), pts(0.5f, 0.32f))
        f.glyphs["t"] = g(Stroke(mutableListOf(Pt(0.46f, 0.26f), Pt(0.45f, 0.64f), Pt(0.5f, 0.72f), Pt(0.57f, 0.7f))), pts(0.36f, 0.44f, 0.58f, 0.43f))
        f.glyphs["!"] = g(pts(0.5f, 0.22f, 0.49f, 0.6f), pts(0.49f, 0.71f))
        return f
    }

    private fun installFont(): HandFont {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = FontStore(ctx)
        val font = demoFont()
        store.save(font)
        store.setActive(font.id)
        return font
    }

    private fun snapshot(v: View, w: Int, h: Int, name: String, bg: Int = Color.WHITE) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            if (h > 0) View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY) else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        v.draw(c)
        FileOutputStream(File(out, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun findByDesc(v: View, desc: String): View? {
        if (v.contentDescription?.toString() == desc) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findByDesc(v.getChildAt(i), desc)?.let { return it }
        return null
    }

    private fun tap(v: View) {
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 5f, 5f, 0))
        v.dispatchTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 5f, 5f, 0))
    }

    @Test
    fun keyboardTypesIntoBoxInHandwriting() {
        installFont()
        val service = Robolectric.buildService(FontKeyboardService::class.java).create().get()
        val kb = service.onCreateInputView()
        val w = 1080
        snapshot(kb, w, 0, "keyboard_empty.png") // lays out the keys

        // Type "hello world" by pressing the real keys.
        for (ch in "hello world") {
            val key = if (ch == ' ') findByDesc(kb, "space") else findByDesc(kb, ch.toString())
            assertNotNull("key $ch", key)
            tap(key!!)
        }
        snapshot(kb, w, 0, "keyboard_typed.png")

        val preview = findPreview(kb)
        assertEquals("hello world", preview.text)
    }

    private fun findPreview(v: View): GlyphTextView {
        if (v is GlyphTextView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) runCatching { return findPreview(v.getChildAt(i)) }
        throw NoSuchElementException()
    }

    @Test
    fun messagePicture() {
        val font = installFont()
        val bmp = GlyphRenderer.renderBitmap("Hello world! how are you", font, 150f, Ui.INKS[1], Color.WHITE, 1080f)
        FileOutputStream(File(out, "message_image.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun mainScreen() {
        installFont()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        snapshot(a.window.decorView.rootView, 1080, 3400, "main_screen.png", Ui.BG)
    }

    @Test
    fun drawScreen() {
        installFont()
        val intent = android.content.Intent(ApplicationProvider.getApplicationContext(), DrawActivity::class.java)
            .putExtra(DrawActivity.EXTRA_CHAR, "h")
        val a = Robolectric.buildActivity(DrawActivity::class.java, intent).setup().get()
        snapshot(a.window.decorView.rootView, 1080, 2300, "draw_screen.png", Ui.BG)
    }

    @Test
    fun gridScreen() {
        installFont()
        val a = Robolectric.buildActivity(GlyphGridActivity::class.java).setup().get()
        snapshot(a.window.decorView.rootView, 1080, 1800, "grid_screen.png", Ui.BG)
    }
}
