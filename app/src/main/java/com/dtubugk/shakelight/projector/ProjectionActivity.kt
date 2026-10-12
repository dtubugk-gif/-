package com.dtubugk.shakelight.projector

import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.core.Prefs
import com.dtubugk.shakelight.core.Screen
import com.dtubugk.shakelight.databinding.ActivityProjectionBinding

/**
 * Plays the chosen photo/video. If a projector/TV is connected as a second screen, the content
 * goes there and the phone shows the controls; otherwise it fills the phone screen.
 */
class ProjectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectionBinding
    private lateinit var displayManager: DisplayManager
    private lateinit var uri: Uri
    private var isVideo = false
    private var presentation: ProjectorPresentation? = null
    private var positionMs = 0
    private var onPhone = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { binding.controls.visibility = View.GONE }

    private val prefs by lazy { Prefs.get(this) }
    private var rotate180
        get() = prefs.getBoolean(Prefs.PROJ_ROTATE, false)
        set(value) = prefs.edit { putBoolean(Prefs.PROJ_ROTATE, value) }
    private var mirror
        get() = prefs.getBoolean(Prefs.PROJ_MIRROR, false)
        set(value) = prefs.edit { putBoolean(Prefs.PROJ_MIRROR, value) }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = route()
        override fun onDisplayRemoved(displayId: Int) = route()
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    private val activeStage: MediaStage get() = presentation?.stage ?: binding.stage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = intent.data ?: run { finish(); return }
        isVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
        positionMs = savedInstanceState?.getInt(KEY_POSITION) ?: 0

        binding = ActivityProjectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        displayManager = getSystemService(DisplayManager::class.java)

        Screen.enterFullscreen(this)
        Screen.setMaxBrightness(this, prefs.getBoolean(Prefs.PROJ_BRIGHT, true))

        binding.stage.onError = ::onMediaError
        binding.root.setOnClickListener { showControls() }
        binding.stage.setOnClickListener { showControls() }
        binding.rotateButton.setOnClickListener { rotate180 = !rotate180; applyTransforms(); showControls() }
        binding.mirrorButton.setOnClickListener { mirror = !mirror; applyTransforms(); showControls() }
        binding.playButton.setOnClickListener { activeStage.togglePlay(); showControls() }
        binding.closeButton.setOnClickListener { finish() }
        binding.playButton.visibility = if (isVideo) View.VISIBLE else View.GONE
    }

    override fun onStart() {
        super.onStart()
        displayManager.registerDisplayListener(displayListener, handler)
        route()
        showControls()
    }

    override fun onStop() {
        positionMs = activeStage.positionMs
        displayManager.unregisterDisplayListener(displayListener)
        presentation?.dismiss()
        presentation = null
        binding.stage.release()
        onPhone = false
        handler.removeCallbacks(hideControls)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_POSITION, positionMs)
    }

    /** Sends the content to an external screen when one exists, otherwise to the phone. */
    private fun route() {
        val external = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        val current = presentation

        if (external != null) {
            if (current?.display?.displayId == external.displayId) return
            positionMs = activeStage.positionMs.takeIf { it > 0 } ?: positionMs
            current?.dismiss()
            binding.stage.release()
            onPhone = false
            val next = ProjectorPresentation(this, external)
            try {
                next.show()
            } catch (e: WindowManager.InvalidDisplayException) {
                presentation = null
                showOnPhone()
                return
            }
            presentation = next
            next.stage.onError = ::onMediaError
            next.stage.show(uri, isVideo, loop(), positionMs)
            binding.externalLabel.text = getString(R.string.proj_external_playing, external.name)
            binding.externalLabel.visibility = View.VISIBLE
            applyTransforms()
        } else if (!onPhone) {
            if (current != null) positionMs = current.stage.positionMs
            current?.dismiss()
            presentation = null
            showOnPhone()
        }
    }

    private fun showOnPhone() {
        onPhone = true
        binding.externalLabel.visibility = View.GONE
        binding.stage.show(uri, isVideo, loop(), positionMs)
        applyTransforms()
    }

    private fun loop() = prefs.getBoolean(Prefs.PROJ_LOOP, true)

    private fun applyTransforms() {
        activeStage.setTransforms(rotate180, mirror)
    }

    private fun showControls() {
        binding.controls.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3500)
    }

    private fun onMediaError() {
        Toast.makeText(this, R.string.proj_error, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_VIDEO = "video"
        private const val KEY_POSITION = "position"
    }
}
