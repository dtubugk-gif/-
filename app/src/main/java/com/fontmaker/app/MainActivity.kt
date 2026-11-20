package com.fontmaker.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var store: FontStore
    private lateinit var font: HandFont

    private lateinit var fontName: TextView
    private lateinit var progressText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var tryPreview: GlyphTextView
    private lateinit var tryInput: EditText
    private lateinit var step1: TextView
    private lateinit var step2: TextView

    private fun dp(v: Int) = Ui.dpi(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FontStore(this)
        font = store.active()
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        font = store.active()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshKeyboardStatus()
    }

    private fun card(vararg children: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.SURFACE, Ui.dp(context, 20f))
        elevation = Ui.dp(context, 1f)
        setPadding(dp(18), dp(18), dp(18), dp(18))
        children.forEach { addView(it) }
    }

    private fun gap(h: Int = 12) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun fullWidth(v: View, top: Int = 10): View {
        v.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }
        return v
    }

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }

        col.addView(Ui.label(this, "Font Maker", 30f, Ui.TEXT, bold = true))
        col.addView(Ui.label(this, "Draw your handwriting, then type with it anywhere.", 15f, Ui.TEXT_SOFT))
        col.addView(gap(16))

        // --- Font card
        fontName = Ui.label(this, "", 22f, Ui.TEXT, bold = true)
        val switchBtn = Ui.label(this, "Change ▾", 15f, Ui.PRIMARY, bold = true).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { chooseFont() }
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(fontName, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(switchBtn)
        }
        progressText = Ui.label(this, "", 14f, Ui.TEXT_SOFT)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = CharacterSet.ALL.size
            progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        }
        val drawBtn = Ui.button(this, "✏️  Draw my letters") { openDraw(firstUndrawn()) }
        val gridBtn = Ui.button(this, "▦  See all characters", primary = false) {
            startActivity(Intent(this, GlyphGridActivity::class.java))
        }
        val fontCard = card(header, progressText, progress)
        fontCard.addView(fullWidth(drawBtn, 14))
        fontCard.addView(fullWidth(gridBtn))
        col.addView(fontCard)
        col.addView(gap(16))

        // --- Keyboard setup card
        step1 = Ui.button(this, "") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        step2 = Ui.button(this, "") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        val kbCard = card(
            Ui.label(this, "⌨️  Font keyboard", 20f, Ui.TEXT, bold = true),
            Ui.label(
                this,
                "Type in the keyboard's box and your words appear in your handwriting. " +
                    "Tap Send and they're dropped into the chat as a picture.",
                14f, Ui.TEXT_SOFT,
            ),
        )
        kbCard.addView(fullWidth(step1, 14))
        kbCard.addView(fullWidth(step2))
        col.addView(kbCard)
        col.addView(gap(16))

        // --- Try it card
        tryPreview = GlyphTextView(this).apply {
            textSizePx = Ui.dp(context, 44f)
            text = "Hello there!"
            background = Ui.rounded(0xFFFBFAFE.toInt(), Ui.dp(context, 14f), 0xFFE4DEF5.toInt(), dp(1))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        tryInput = EditText(this).apply {
            hint = "Type something to preview…"
            setText("Hello there!")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { tryPreview.text = s?.toString() ?: "" }
            })
        }
        val shareBtn = Ui.button(this, "Share as picture", primary = false) {
            val t = tryInput.text.toString().trim()
            if (t.isEmpty()) return@button toast("Type something first")
            Sharing.shareImage(this, Sharing.createMessageImage(this, font, t, Color.BLACK), fromService = false)
        }
        val tryCard = card(Ui.label(this, "Try it", 20f, Ui.TEXT, bold = true))
        tryCard.addView(fullWidth(tryPreview))
        tryCard.addView(fullWidth(tryInput, 6))
        tryCard.addView(fullWidth(shareBtn))
        col.addView(tryCard)
        col.addView(gap(16))

        // --- Export card
        val exportCard = card(
            Ui.label(this, "Export", 20f, Ui.TEXT, bold = true),
            Ui.label(this, "Save your handwriting as a real .ttf font file to use in other apps or on a computer.", 14f, Ui.TEXT_SOFT),
        )
        exportCard.addView(fullWidth(Ui.button(this, "Save .ttf to Files", primary = false) { saveTtf() }, 14))
        exportCard.addView(fullWidth(Ui.button(this, "Share .ttf", primary = false) { shareTtf() }))
        exportCard.addView(fullWidth(Ui.button(this, "Rename font", primary = false) { renameFont() }))
        exportCard.addView(fullWidth(Ui.label(this, "Delete this font", 14f, 0xFFD93025.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener { deleteFont() }
        }))
        col.addView(exportCard)

        return ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(col)
        }
    }

    private fun refresh() {
        fontName.text = font.name
        val n = font.drawnCount
        progress.progress = n
        progressText.text = when {
            n == 0 -> "No letters drawn yet. Start with a–z."
            n < CharacterSet.ALL.size -> "$n of ${CharacterSet.ALL.size} characters drawn. Missing ones use a stand-in font."
            else -> "All ${CharacterSet.ALL.size} characters drawn 🎉"
        }
        tryPreview.font = font
        refreshKeyboardStatus()
    }

    private fun refreshKeyboardStatus() {
        if (!::step1.isInitialized) return
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        step1.text = if (enabled) "✓  Step 1: Keyboard turned on" else "Step 1: Turn on “Font Maker Keyboard”"
        step2.text = if (current) "✓  Step 2: Font keyboard selected" else "Step 2: Switch to the font keyboard"
        style(step1, done = enabled)
        style(step2, done = current)
    }

    private fun style(v: TextView, done: Boolean) {
        v.background = Ui.rounded(if (done) 0xFFE6F4EA.toInt() else Ui.PRIMARY, Ui.dp(this, 14f))
        v.setTextColor(if (done) Ui.SUCCESS else Color.WHITE)
    }

    private fun firstUndrawn(): String = CharacterSet.ALL.firstOrNull { !font.has(it) } ?: "a"

    private fun openDraw(ch: String) {
        startActivity(Intent(this, DrawActivity::class.java).putExtra(DrawActivity.EXTRA_CHAR, ch))
    }

    private fun chooseFont() {
        val fonts = store.list()
        val names = fonts.map { (if (it.id == font.id) "● " else "   ") + it.name } + "＋ New font"
        AlertDialog.Builder(this)
            .setTitle("Your fonts")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == fonts.size) newFont() else {
                    store.setActive(fonts[which].id)
                    font = store.active()
                    refresh()
                }
            }
            .show()
    }

    private fun askName(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(initial.length)
        }
        val wrap = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("OK") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) onOk(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun newFont() = askName("New font", "My Font ${store.list().size + 1}") {
        font = store.create(it)
        refresh()
    }

    private fun renameFont() = askName("Rename font", font.name) {
        font.name = it
        store.save(font)
        refresh()
    }

    private fun deleteFont() {
        AlertDialog.Builder(this)
            .setTitle("Delete “${font.name}”?")
            .setMessage("All the letters you drew for this font will be removed.")
            .setPositiveButton("Delete") { _, _ ->
                store.delete(font)
                font = store.active()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun ensureSomethingDrawn(): Boolean {
        if (font.drawnCount == 0) {
            toast("Draw some letters first")
            return false
        }
        return true
    }

    private fun shareTtf() {
        if (!ensureSomethingDrawn()) return
        Sharing.shareFile(this, Sharing.exportTtf(this, font), "font/ttf")
    }

    private fun saveTtf() {
        if (!ensureSomethingDrawn()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "font/ttf"
            putExtra(Intent.EXTRA_TITLE, Sharing.safeFileName(font.name) + ".ttf")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_SAVE_TTF)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SAVE_TTF && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(TtfWriter.write(font)) }
                toast("Font saved")
            } catch (e: Exception) {
                toast("Couldn't save: ${e.message}")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_SAVE_TTF = 41
    }
}
