package com.dtubugk.shakelight.cast

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.core.Prefs
import com.dtubugk.shakelight.databinding.ActivityCastBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/** Pick a computer running ShakeLight Receiver and project the phone screen to it. */
class CastToPcActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCastBinding
    private lateinit var discovery: ReceiverDiscovery
    private var target: ReceiverDiscovery.Receiver? = null
    private var lastState: ScreenCastService.State? = null

    private val audioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { askScreenCapture() }

    private val screenCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val receiver = target ?: return@registerForActivityResult
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                ScreenCastService.start(this, result.resultCode, data, receiver, binding.soundSwitch.isChecked)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = Prefs.get(this)
        binding.soundSwitch.isChecked = prefs.getBoolean(Prefs.CAST_SOUND, true)
        binding.soundSwitch.setOnCheckedChangeListener { _, checked -> prefs.edit { putBoolean(Prefs.CAST_SOUND, checked) } }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            binding.soundSwitch.isChecked = false
            binding.soundSwitch.isEnabled = false
        }

        binding.manualAddress.setText(prefs.getString(Prefs.CAST_LAST_IP, ""))
        binding.manualConnect.setOnClickListener { connectManually() }
        binding.stopButton.setOnClickListener { ScreenCastService.stop(this) }

        discovery = ReceiverDiscovery(this, ::renderReceivers)
        renderReceivers(emptyList())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScreenCastService.state.collect(::renderState)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        discovery.start()
    }

    override fun onStop() {
        discovery.stop()
        super.onStop()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun renderReceivers(receivers: List<ReceiverDiscovery.Receiver>) {
        binding.receiverList.removeAllViews()
        binding.searching.visibility = if (receivers.isEmpty()) View.VISIBLE else View.GONE
        for (receiver in receivers) {
            val button = MaterialButton(this).apply {
                text = getString(R.string.cast_receiver_item, receiver.name, receiver.host)
                setIconResource(R.drawable.ic_computer)
                isAllCaps = false
                setOnClickListener { begin(receiver) }
            }
            binding.receiverList.addView(button)
        }
    }

    private fun connectManually() {
        val address = binding.manualAddress.text?.toString()?.trim().orEmpty()
        if (!IP_PATTERN.matches(address)) {
            Toast.makeText(this, R.string.cast_manual_invalid, Toast.LENGTH_LONG).show()
            return
        }
        Prefs.get(this).edit { putString(Prefs.CAST_LAST_IP, address) }
        begin(ReceiverDiscovery.Receiver(address, address, CastProtocol.STREAM_PORT))
    }

    private fun begin(receiver: ReceiverDiscovery.Receiver) {
        target = receiver
        val needsAudioPermission = binding.soundSwitch.isChecked &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        if (needsAudioPermission) {
            // Android only lets apps capture the sound other apps play with this permission.
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            askScreenCapture()
        }
    }

    private fun askScreenCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        screenCapture.launch(manager.createScreenCaptureIntent())
    }

    private fun renderState(state: ScreenCastService.State) {
        val active = state is ScreenCastService.State.Connecting || state is ScreenCastService.State.Casting
        binding.stopButton.visibility = if (active) View.VISIBLE else View.GONE
        binding.stateText.text = when (state) {
            is ScreenCastService.State.Idle -> getString(R.string.cast_state_idle)
            is ScreenCastService.State.Connecting -> getString(R.string.cast_state_connecting, state.target)
            is ScreenCastService.State.Casting -> getString(R.string.cast_state_casting, state.target)
            is ScreenCastService.State.Failed -> getString(state.message)
        }
        if (state is ScreenCastService.State.Casting && lastState !is ScreenCastService.State.Casting) {
            Toast.makeText(this, R.string.cast_started_toast, Toast.LENGTH_LONG).show()
        }
        lastState = state
    }

    private companion object {
        val IP_PATTERN = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    }
}
