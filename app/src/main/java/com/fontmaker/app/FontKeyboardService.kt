package com.fontmaker.app

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat

/**
 * A keyboard that types into its own preview box using your hand-drawn font.
 * "Send" turns the box into a picture of your handwriting and drops it into the chat.
 * "Direct" mode types plain text straight into the app instead.
 */
class FontKeyboardService : InputMethodService() {

    private enum class Shift { OFF, ONCE, LOCK }
    private enum class Page { LETTERS, SYMBOLS, MORE }
    private enum class Kind { CHAR, SHIFT, BACK, SPACE, ENTER, PAGE }

    private class KeySpec(val kind: Kind, val value: String = "", val weight: Float = 1f, val page: Page? = null)

    private lateinit var store: FontStore
    private var font: HandFont? = null
    private var fontStamp = ""

    private val buffer = StringBuilder()
    private var directMode = false
    private var shift = Shift.OFF
    private var lastShiftTap = 0L
    private var page = Page.LETTERS
    private var inkIndex = 0

    private var preview: GlyphTextView? = null
    private var keysContainer: LinearLayout? = null
    private var modeButton: TextView? = null
    private var colorDot: View? = null
    private var previewCard: View? = null
    private val keyViews = mutableListOf<KeyView>()

    private val prefs by lazy { getSharedPreferences("keyboard", Context.MODE_PRIVATE) }
    private val ink get() = Ui.INKS[inkIndex % Ui.INKS.size]

    override fun onCreate() {
        super.onCreate()
        store = FontStore(this)
        inkIndex = prefs.getInt("ink", 0)
        directMode = prefs.getBoolean("direct", false)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        reloadFont(force = true)
        return buildUi()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        reloadFont(force = false)
        refreshAll()
    }

    private fun reloadFont(force: Boolean) {
        val stamp = store.activeStamp()
        if (force || font == null || stamp != fontStamp) {
            font = store.active()
            fontStamp = store.activeStamp()
        }
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int) = Ui.dpi(this, v)

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KB_BG)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        // The box you type into, rendered in your font.
        val pv = GlyphTextView(this).apply {
            textSizePx = Ui.dp(context, 40f)
            minTextSizePx = Ui.dp(context, 20f)
            showCursor = true
            centerVertically = true
            hint = "Type here – it's written in your font"
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        preview = pv
        val card = FrameLayout(this).apply {
            background = Ui.rounded(Color.WHITE, Ui.dp(context, 14f))
            addView(pv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        previewCard = card
        root.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(84)).apply {
            setMargins(dp(4), 0, dp(4), dp(6))
        })

        // Toolbar
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(pill("🌐", KEY_SPECIAL) { pickKeyboard() }, barLp())
        modeButton = pill("", KEY_SPECIAL) { toggleMode() }
        bar.addView(modeButton, barLp())
        val dot = View(this).apply {
            setOnClickListener { cycleInk() }
            contentDescription = "Ink colour"
        }
        colorDot = dot
        bar.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)).apply { setMargins(dp(6), 0, dp(6), 0) })
        bar.addView(pill("✎", KEY_SPECIAL) { openApp() }, barLp())
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(pill("✕", KEY_SPECIAL) { clearBox() }, barLp())
        bar.addView(pill("Send ➤", SEND_BG, Color.WHITE) { send() }, barLp())
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
            setMargins(0, 0, 0, dp(4))
        })

        keysContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keysContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        buildKeys()
        refreshAll()
        return root
    }

    private fun barLp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
        setMargins(dp(2), 0, dp(2), 0)
    }

    private fun pill(text: String, bg: Int, fg: Int = Color.WHITE, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(fg)
        background = Ui.rounded(bg, Ui.dp(context, 10f))
        setPadding(dp(11), 0, dp(11), 0)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
    }

    private fun rowsFor(p: Page): List<List<KeySpec>> {
        fun chars(s: String) = s.map { KeySpec(Kind.CHAR, it.toString()) }
        val bottom = { pageKey: KeySpec ->
            listOf(
                pageKey,
                KeySpec(Kind.CHAR, ","),
                KeySpec(Kind.SPACE, "space", 4.5f),
                KeySpec(Kind.CHAR, "."),
                KeySpec(Kind.ENTER, "⏎", 1.5f),
            )
        }
        return when (p) {
            Page.LETTERS -> listOf(
                chars("qwertyuiop"),
                listOf(KeySpec(Kind.CHAR, "", 0.5f)) + chars("asdfghjkl") + listOf(KeySpec(Kind.CHAR, "", 0.5f)),
                listOf(KeySpec(Kind.SHIFT, "⇧", 1.5f)) + chars("zxcvbnm") + listOf(KeySpec(Kind.BACK, "⌫", 1.5f)),
                bottom(KeySpec(Kind.PAGE, "?123", 1.5f, Page.SYMBOLS)),
            )
            Page.SYMBOLS -> listOf(
                chars("1234567890"),
                chars("@#$%&-+()/"),
                listOf(KeySpec(Kind.PAGE, "=\\<", 1.5f, Page.MORE)) + chars("*\"':;!?") + listOf(KeySpec(Kind.BACK, "⌫", 1.5f)),
                bottom(KeySpec(Kind.PAGE, "ABC", 1.5f, Page.LETTERS)),
            )
            Page.MORE -> listOf(
                chars("~_=<>[]{}^"),
                chars("\\|•°€£¥©♥✓"),
                listOf(KeySpec(Kind.PAGE, "?123", 1.5f, Page.SYMBOLS)) + chars("…«»¿¡§¶") + listOf(KeySpec(Kind.BACK, "⌫", 1.5f)),
                bottom(KeySpec(Kind.PAGE, "ABC", 1.5f, Page.LETTERS)),
            )
        }
    }

    private fun buildKeys() {
        val container = keysContainer ?: return
        container.removeAllViews()
        keyViews.clear()
        for (row in rowsFor(page)) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (spec in row) {
                if (spec.kind == Kind.CHAR && spec.value.isEmpty()) {
                    r.addView(View(this), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, spec.weight))
                    continue
                }
                val kv = KeyView(this, spec)
                keyViews += kv
                r.addView(kv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, spec.weight))
            }
            container.addView(r, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))
        }
    }

    private fun refreshAll() {
        preview?.let {
            it.font = font
            it.inkColor = ink
            it.text = buffer.toString()
            it.hint = if (directMode) "Direct mode: typing goes straight into the app" else "Type here – it's written in your font"
        }
        previewCard?.alpha = if (directMode) 0.55f else 1f
        modeButton?.text = if (directMode) "abc" else "✍ Box"
        colorDot?.background = Ui.rounded(ink, Ui.dp(this, 15f), Color.WHITE, dp(2))
        keyViews.forEach { it.invalidate() }
    }

    // ---------------------------------------------------------------- Actions

    private fun labelFor(spec: KeySpec): String = when (spec.kind) {
        Kind.CHAR -> if (page == Page.LETTERS && shift != Shift.OFF) spec.value.uppercase() else spec.value
        Kind.SHIFT -> if (shift == Shift.LOCK) "⇪" else "⇧"
        Kind.ENTER -> if (directMode) "⏎" else "↵"
        else -> spec.value
    }

    private fun onKey(spec: KeySpec) {
        when (spec.kind) {
            Kind.CHAR -> {
                type(labelFor(spec))
                if (shift == Shift.ONCE) { shift = Shift.OFF; refreshAll() }
            }
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when (shift) {
                    Shift.OFF -> Shift.ONCE
                    Shift.ONCE -> if (now - lastShiftTap < 400) Shift.LOCK else Shift.OFF
                    Shift.LOCK -> Shift.OFF
                }
                lastShiftTap = now
                refreshAll()
            }
            Kind.BACK -> backspace()
            Kind.SPACE -> type(" ")
            Kind.ENTER -> if (directMode) enterDirect() else type("\n")
            Kind.PAGE -> {
                page = spec.page ?: Page.LETTERS
                buildKeys()
                refreshAll()
            }
        }
    }

    private fun type(s: String) {
        if (directMode) {
            currentInputConnection?.commitText(s, 1)
        } else {
            if (buffer.length < 2000) buffer.append(s)
            preview?.text = buffer.toString()
        }
    }

    private fun backspace() {
        if (directMode) {
            currentInputConnection?.let {
                it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            return
        }
        if (buffer.isEmpty()) return
        val cp = buffer.codePointBefore(buffer.length)
        buffer.setLength(buffer.length - Character.charCount(cp))
        preview?.text = buffer.toString()
    }

    private fun enterDirect() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun clearBox() {
        buffer.clear()
        refreshAll()
    }

    private fun toggleMode() {
        directMode = !directMode
        prefs.edit().putBoolean("direct", directMode).apply()
        refreshAll()
    }

    private fun cycleInk() {
        inkIndex = (inkIndex + 1) % Ui.INKS.size
        prefs.edit().putInt("ink", inkIndex).apply()
        refreshAll()
    }

    private fun pickKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm.showInputMethodPicker()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Turns the box into a handwriting picture and puts it into the chat. */
    private fun send() {
        val text = buffer.toString().trimEnd()
        if (text.isBlank()) {
            toast("Type a message in the box first")
            return
        }
        val uri = try {
            Sharing.createMessageImage(this, font, text, ink)
        } catch (e: Exception) {
            toast("Couldn't create the image: ${e.message}")
            return
        }

        val ei = currentInputEditorInfo
        val ic = currentInputConnection
        var committed = false
        if (ei != null && ic != null) {
            val accepts = EditorInfoCompat.getContentMimeTypes(ei).any { ClipDescription.compareMimeTypes("image/png", it) }
            if (accepts) {
                val info = InputContentInfoCompat(uri, ClipDescription("Handwritten message", arrayOf("image/png")), null)
                committed = try {
                    InputConnectionCompat.commitContent(ic, ei, info, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                } catch (e: Exception) {
                    false
                }
            }
        }

        if (!committed) {
            // This app's text box doesn't accept pictures from keyboards: use the share sheet instead.
            try {
                Sharing.shareImage(this, uri, fromService = true)
                toast("Pick the chat to send your handwriting to")
            } catch (e: Exception) {
                toast("Couldn't share: ${e.message}")
                return
            }
        }
        buffer.clear()
        refreshAll()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- Key view

    @SuppressLint("ViewConstructor")
    private inner class KeyView(ctx: Context, val spec: KeySpec) : View(ctx) {
        private val rect = RectF()
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        private var down = false
        private val repeater = object : Runnable {
            override fun run() {
                if (!down) return
                onKey(spec)
                postDelayed(this, 55)
            }
        }

        init {
            contentDescription = spec.value
        }

        override fun onDraw(canvas: Canvas) {
            val inset = Ui.dp(context, 3f)
            rect.set(inset, inset, width - inset, height - inset)
            val special = spec.kind != Kind.CHAR && spec.kind != Kind.SPACE
            bg.color = when {
                down -> KEY_PRESSED
                spec.kind == Kind.SHIFT && shift != Shift.OFF -> SEND_BG
                special -> KEY_SPECIAL
                else -> KEY_BG
            }
            canvas.drawRoundRect(rect, Ui.dp(context, 8f), Ui.dp(context, 8f), bg)

            val label = labelFor(spec)
            val f = font
            val g = if (spec.kind == Kind.CHAR) f?.glyph(label) else null
            if (f != null && g != null) {
                val h = rect.height() * 0.62f
                val r = RectF(rect.centerX() - h, rect.centerY() - h / 2f, rect.centerX() + h, rect.centerY() + h / 2f)
                GlyphRenderer.drawGlyphInRect(canvas, f, g, r, Color.WHITE, centerOnInk = true)
            } else {
                text.textSize = if (label.length > 2) Ui.dp(context, 14f) else Ui.dp(context, 21f)
                text.typeface = if (spec.kind == Kind.CHAR) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
                text.color = if (spec.kind == Kind.SPACE) 0xFF9AA0A6.toInt() else Color.WHITE
                canvas.drawText(label, rect.centerX(), rect.centerY() - (text.ascent() + text.descent()) / 2f, text)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    down = true
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onKey(spec)
                    if (spec.kind == Kind.BACK) postDelayed(repeater, 420)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    down = false
                    removeCallbacks(repeater)
                    invalidate()
                }
            }
            return true
        }
    }

    companion object {
        private const val KB_BG = 0xFF1C1B22.toInt()
        private const val KEY_BG = 0xFF3A3846.toInt()
        private const val KEY_SPECIAL = 0xFF2A2933.toInt()
        private const val KEY_PRESSED = 0xFF5E5A72.toInt()
        private const val SEND_BG = 0xFF7C5CFF.toInt()
    }
}
