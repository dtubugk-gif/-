package com.fontmaker.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class DrawActivity : Activity() {
    private lateinit var store: FontStore
    private lateinit var font: HandFont
    private var index = 0

    private lateinit var canvasView: DrawCanvasView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var sample: GlyphTextView

    private fun dp(v: Int) = Ui.dpi(this, v)
    private val ch get() = CharacterSet.ALL[index]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FontStore(this)
        font = store.active()
        val start = savedInstanceState?.getString(EXTRA_CHAR) ?: intent.getStringExtra(EXTRA_CHAR) ?: "a"
        index = CharacterSet.ALL.indexOf(start).coerceAtLeast(0)
        setContentView(buildUi())
        showChar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_CHAR, ch)
    }

    private fun navButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 26f
        gravity = Gravity.CENTER
        setTextColor(Ui.PRIMARY)
        background = Ui.rounded(Ui.PRIMARY_SOFT, Ui.dp(context, 28f))
        setOnClickListener { onClick() }
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Header: ◀  a  ▶
        title = TextView(this).apply {
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.TEXT)
            gravity = Gravity.CENTER
        }
        subtitle = Ui.label(this, "", 14f, Ui.TEXT_SOFT).apply { gravity = Gravity.CENTER }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(navButton("‹") { move(-1) }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(titleCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(navButton("›") { move(1) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        }
        root.addView(header)

        canvasView = DrawCanvasView(this).apply {
            font = this@DrawActivity.font
            setPadding(0, dp(12), 0, dp(12))
            onChanged = { saveCurrent() }
        }
        root.addView(canvasView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Tools
        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tools.addView(Ui.button(this, "↶ Undo", primary = false) { canvasView.undo() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        tools.addView(Ui.button(this, "Clear", primary = false) { canvasView.clear() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        tools.addView(Ui.button(this, "▦ All", primary = false) {
            startActivity(Intent(this, GlyphGridActivity::class.java))
            finish()
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tools)

        // Pen thickness (applies to the whole font)
        val thick = SeekBar(this).apply {
            max = 100
            progress = (((font.strokeWidth - Metrics.MIN_STROKE) / (Metrics.MAX_STROKE - Metrics.MIN_STROKE)) * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
            thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    font.strokeWidth = Metrics.MIN_STROKE + (Metrics.MAX_STROKE - Metrics.MIN_STROKE) * p / 100f
                    canvasView.invalidate()
                    sample.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { store.save(font) }
            })
        }
        val thickRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
            addView(Ui.label(context, "Pen", 14f, Ui.TEXT_SOFT, bold = true))
            addView(thick, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(thickRow)

        sample = GlyphTextView(this).apply {
            font = this@DrawActivity.font
            textSizePx = Ui.dp(context, 30f)
            background = Ui.rounded(Ui.SURFACE, Ui.dp(context, 14f))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        root.addView(sample, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply { topMargin = dp(8) })

        root.addView(Ui.button(this, "Next  ›") { move(1) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        return root
    }

    private fun showChar() {
        title.text = ch
        subtitle.text = "${CharacterSet.describe(ch)}  ·  ${index + 1} / ${CharacterSet.ALL.size}"
        canvasView.refChar = ch
        canvasView.glyph = font.glyphs[ch]?.copy() ?: Glyph()
        sample.text = sampleFor(ch)
    }

    private fun sampleFor(c: String): String = when (c) {
        in CharacterSet.UPPER -> "${c}ello ${c}${c.lowercase()}"
        in CharacterSet.DIGITS -> "call 555-${c}${c}${c}"
        in CharacterSet.LOWER -> "hello $c$c$c"
        else -> "hi${c} there$c"
    }

    private fun saveCurrent() {
        val g = canvasView.glyph
        if (g.isEmpty) font.glyphs.remove(ch) else font.glyphs[ch] = g.copy()
        store.save(font)
        sample.invalidate()
    }

    private fun move(delta: Int) {
        index = (index + delta + CharacterSet.ALL.size) % CharacterSet.ALL.size
        showChar()
    }

    companion object {
        const val EXTRA_CHAR = "char"
    }
}
