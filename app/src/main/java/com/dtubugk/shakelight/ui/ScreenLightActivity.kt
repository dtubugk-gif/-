package com.dtubugk.shakelight.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.core.Screen

/** Turns the whole screen into a lamp. Tap = next color, long-press = color-cycling party mode. */
class ScreenLightActivity : AppCompatActivity() {

    private val colors = intArrayOf(
        Color.WHITE,
        0xFFFFE0B2.toInt(), // warm white
        0xFFFF1744.toInt(), // red — keeps night vision
        0xFFFFC400.toInt(), // amber
        0xFF00E676.toInt(), // green
        0xFF2979FF.toInt(), // blue
        0xFFD500F9.toInt(), // purple
    )
    private var index = 0
    private var party: ValueAnimator? = null
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = savedInstanceState?.getInt(KEY_INDEX) ?: 0

        val hint = TextView(this).apply {
            setText(R.string.screen_light_hint)
            setTextColor(Color.GRAY)
            setPadding(48, 48, 48, 96)
            gravity = Gravity.CENTER
        }
        root = FrameLayout(this).apply {
            setBackgroundColor(colors[index])
            addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            setOnClickListener { stopParty(); next() }
            setOnLongClickListener { if (party == null) startParty() else stopParty(); true }
        }
        setContentView(root)
        hint.animate().alpha(0f).setStartDelay(3000).setDuration(800).start()

        Screen.enterFullscreen(this)
        Screen.setMaxBrightness(this, true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_INDEX, index)
    }

    override fun onDestroy() {
        stopParty()
        super.onDestroy()
    }

    private fun next() {
        val from = colors[index]
        index = (index + 1) % colors.size
        ValueAnimator.ofObject(ArgbEvaluator(), from, colors[index]).apply {
            duration = 250
            addUpdateListener { root.setBackgroundColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun startParty() {
        party = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                root.setBackgroundColor(Color.HSVToColor(floatArrayOf(it.animatedValue as Float, 1f, 1f)))
            }
            start()
        }
    }

    private fun stopParty() {
        party?.cancel()
        party = null
        root.setBackgroundColor(colors[index])
    }

    companion object {
        private const val KEY_INDEX = "color_index"
    }
}
