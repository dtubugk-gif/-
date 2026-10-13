package com.dtubugk.shakelight.cast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import kotlin.math.abs

/**
 * Performs the computer's mouse and keyboard input on the phone. Android only allows taps, swipes
 * and typing into other apps through an accessibility service that the user turns on in Settings.
 * It does nothing on its own: it only acts on commands from an active "Project to computer" session.
 */
class ControlService : AccessibilityService() {

    private var passwordNode: AccessibilityNodeInfo? = null
    private val passwordText = StringBuilder()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ---- pointer ----

    /** [points] are (x, y) as 0..1 of the screen, with times in ms from the press. */
    fun gesture(points: List<Triple<Float, Float, Long>>) {
        val (w, h) = screenSize()
        val px = points.map { TouchPoint(clamp(it.first * w, w), clamp(it.second * h, h), it.third) }
        val slop = resources.displayMetrics.density * 8
        when (val plan = GesturePlan.from(px, slop) ?: return) {
            is GesturePlan.Tap -> stroke(path(listOf(TouchPoint(plan.x, plan.y, 0))), 50)
            is GesturePlan.LongPress -> stroke(path(listOf(TouchPoint(plan.x, plan.y, 0))), plan.durationMs.coerceAtMost(5000))
            is GesturePlan.Swipe -> stroke(path(plan.points), plan.durationMs)
            is GesturePlan.HoldThenDrag -> holdThenDrag(plan)
        }
    }

    fun scroll(x: Float, y: Float, dx: Float, dy: Float) {
        val (w, h) = screenSize()
        val sx = clamp(x * w, w)
        val sy = clamp(y * h, h)
        // Wheel down shows content further down, so the finger moves up.
        val ex = clamp(sx - (dx * w * 0.18f).coerceIn(-w * 0.6f, w * 0.6f), w)
        val ey = clamp(sy - (dy * h * 0.12f).coerceIn(-h * 0.6f, h * 0.6f), h)
        if (abs(ex - sx) < 2 && abs(ey - sy) < 2) return
        stroke(path(listOf(TouchPoint(sx, sy, 0), TouchPoint(ex, ey, 0))), 260)
    }

    private fun holdThenDrag(plan: GesturePlan.HoldThenDrag) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            stroke(path(listOf(plan.start) + plan.points), plan.holdMs + plan.moveMs)
            return
        }
        val hold = GestureDescription.StrokeDescription(path(listOf(plan.start)), 0, plan.holdMs.coerceAtLeast(1), true)
        dispatchGesture(GestureDescription.Builder().addStroke(hold).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                val move = hold.continueStroke(path(listOf(plan.start) + plan.points), 0, plan.moveMs, false)
                dispatchGesture(GestureDescription.Builder().addStroke(move).build(), null, null)
            }
        }, null)
    }

    private fun stroke(path: Path, durationMs: Long) {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(1, 10_000))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun path(points: List<TouchPoint>): Path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (p in points.drop(1)) lineTo(p.x, p.y)
    }

    // ---- keys ----

    fun key(name: String) {
        val action = when (name) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick" -> GLOBAL_ACTION_QUICK_SETTINGS
            "power" -> GLOBAL_ACTION_POWER_DIALOG
            "split" -> GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
            "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else return
            "screenshot" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_TAKE_SCREENSHOT else return
            "vol+", "vol-", "mute" -> return volume(name)
            else -> return
        }
        performGlobalAction(action)
    }

    private fun volume(name: String) {
        val direction = when (name) {
            "vol+" -> AudioManager.ADJUST_RAISE
            "vol-" -> AudioManager.ADJUST_LOWER
            else -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        getSystemService(AudioManager::class.java)
            .adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    // ---- text ----
    // Android 13+: type through the system input channel, exactly like a keyboard app, so it works in
    // every text field. Older Android: edit the focused field's text through accessibility.

    fun type(text: String) {
        val input = inputConnection()
        if (input != null) {
            input.commitText(text, 1, null)
            return
        }
        editFocused { current, start, end -> TextEdit.insert(current, start, end, text) }
    }

    fun backspace() {
        val input = inputConnection()
        if (input != null) {
            input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            return
        }
        editFocused { current, start, end -> TextEdit.backspace(current, start, end) }
    }

    fun enter() {
        val input = inputConnection()
        if (input != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val info = inputMethod?.currentInputEditorInfo
            val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
            val multiLine = (info?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            val noEnterAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            if (!multiLine && !noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                input.performEditorAction(action) // Search, Send, Go, Done…
            } else {
                input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                input.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            return
        }
        val node = focusedInput() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id }
        ) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else if (node.isMultiLine) {
            type("\n")
        }
    }

    private fun inputConnection() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) inputMethod?.currentInputConnection else null

    private fun editFocused(edit: (String, Int, Int) -> TextEdit.Result) {
        val node = focusedInput() ?: return
        val result = if (node.isPassword) {
            // Password fields only expose dots, so keep our own copy of what was typed.
            if (node != passwordNode) {
                passwordNode = node
                passwordText.clear()
            }
            edit(passwordText.toString(), -1, -1).also {
                passwordText.clear()
                passwordText.append(it.text)
            }
        } else {
            val showingHint = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText
            val current = if (showingHint) "" else node.text?.toString().orEmpty()
            edit(current, node.textSelectionStart, node.textSelectionEnd)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result.text)
        })
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, result.cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, result.cursor)
        })
    }

    private fun focusedInput(): AccessibilityNodeInfo? =
        (rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
            ?.takeIf { it.isEditable }

    // ---- helpers ----

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Float, Float> {
        val metrics = DisplayMetrics()
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        return metrics.widthPixels.toFloat() to metrics.heightPixels.toFloat()
    }

    private fun clamp(v: Float, max: Float) = v.coerceIn(0f, max - 1)

    companion object {
        @Volatile
        var instance: ControlService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, ControlService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
