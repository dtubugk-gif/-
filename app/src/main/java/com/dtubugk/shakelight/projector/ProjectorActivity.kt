package com.dtubugk.shakelight.projector

import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.cast.CastToPcActivity
import com.dtubugk.shakelight.core.Prefs
import com.dtubugk.shakelight.databinding.ActivityProjectorBinding
import com.google.android.material.materialswitch.MaterialSwitch

/** Projector setup: choose a photo/video, choose how it is projected, connect a screen. */
class ProjectorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectorBinding
    private lateinit var displayManager: DisplayManager
    private var mediaUri: Uri? = null
    private var isVideo = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { select(it, video = false) }
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { select(it, video = true) }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = renderDisplay()
        override fun onDisplayRemoved(displayId: Int) = renderDisplay()
        override fun onDisplayChanged(displayId: Int) = renderDisplay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        displayManager = getSystemService(DisplayManager::class.java)

        savedInstanceState?.let {
            mediaUri = BundleCompat.getParcelable(it, KEY_URI, Uri::class.java)
            isVideo = it.getBoolean(KEY_VIDEO)
        }

        binding.pickImage.setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        binding.pickVideo.setOnClickListener { pickVideo.launch(arrayOf("video/*")) }
        binding.castButton.setOnClickListener { startActivity(Intent(this, CastToPcActivity::class.java)) }
        binding.startButton.setOnClickListener { startProjection() }

        bindSwitch(binding.rotateSwitch, Prefs.PROJ_ROTATE, false)
        bindSwitch(binding.mirrorSwitch, Prefs.PROJ_MIRROR, false)
        bindSwitch(binding.brightSwitch, Prefs.PROJ_BRIGHT, true)
        bindSwitch(binding.loopSwitch, Prefs.PROJ_LOOP, true)

        renderSelection()
    }

    override fun onStart() {
        super.onStart()
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        renderDisplay()
    }

    override fun onStop() {
        displayManager.unregisterDisplayListener(displayListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_URI, mediaUri)
        outState.putBoolean(KEY_VIDEO, isVideo)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindSwitch(view: MaterialSwitch, key: String, default: Boolean) {
        val prefs = Prefs.get(this)
        view.isChecked = prefs.getBoolean(key, default)
        view.setOnCheckedChangeListener { _, checked -> prefs.edit { putBoolean(key, checked) } }
    }

    private fun select(uri: Uri, video: Boolean) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        mediaUri = uri
        isVideo = video
        renderSelection()
    }

    private fun renderSelection() {
        val uri = mediaUri
        binding.startButton.isEnabled = uri != null
        binding.selectedLabel.text = when {
            uri == null -> getString(R.string.nothing_selected)
            isVideo -> getString(R.string.selected_video, displayName(uri))
            else -> getString(R.string.selected_image, displayName(uri))
        }
    }

    private fun renderDisplay() {
        val external = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        binding.displayStatus.text = if (external == null) {
            getString(R.string.proj_no_display)
        } else {
            getString(R.string.proj_display, external.name)
        }
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    private fun startProjection() {
        val uri = mediaUri ?: return
        startActivity(
            Intent(this, ProjectionActivity::class.java)
                .setData(uri)
                .putExtra(ProjectionActivity.EXTRA_VIDEO, isVideo)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_VIDEO = "video"
    }
}
