package com.dtubugk.shakelight.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.dtubugk.shakelight.R
import com.dtubugk.shakelight.core.Morse
import com.dtubugk.shakelight.core.Prefs
import com.dtubugk.shakelight.core.TorchController
import com.dtubugk.shakelight.databinding.ActivityFlashlightBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FlashlightActivity : AppCompatActivity() {

    private enum class Mode { STEADY, STROBE, SOS, MORSE }

    private lateinit var binding: ActivityFlashlightBinding
    private var mode = Mode.STEADY
    private var patternJob: Job? = null
    private val torchListener: (Boolean) -> Unit = { renderPower() }

    private val level get() = Prefs.torchLevel(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlashlightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.power.isEnabled = TorchController.isAvailable
        binding.power.setOnClickListener { onPowerPressed() }

        binding.modeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            stopPattern()
            mode = when (id) {
                R.id.modeStrobe -> Mode.STROBE
                R.id.modeSos -> Mode.SOS
                R.id.modeMorse -> Mode.MORSE
                else -> Mode.STEADY
            }
            renderMode()
            renderPower()
        }

        binding.strobeSlider.addOnChangeListener { _, value, _ ->
            binding.strobeLabel.text = getString(R.string.strobe_speed, value.toInt())
        }
        binding.strobeLabel.text = getString(R.string.strobe_speed, binding.strobeSlider.value.toInt())

        if (TorchController.supportsStrength) {
            binding.brightnessSlider.value = level.toFloat()
            binding.brightnessLabel.text = getString(R.string.brightness, level)
            binding.brightnessSlider.addOnChangeListener { _, value, fromUser ->
                if (!fromUser) return@addOnChangeListener
                val percent = value.toInt()
                Prefs.get(this).edit { putInt(Prefs.TORCH_LEVEL, percent) }
                binding.brightnessLabel.text = getString(R.string.brightness, percent)
                if (mode == Mode.STEADY && TorchController.isOn) TorchController.set(true, percent)
            }
        } else {
            binding.brightnessLabel.visibility = View.GONE
            binding.brightnessSlider.visibility = View.GONE
            binding.brightnessUnsupported.visibility = View.VISIBLE
        }

        renderMode()
    }

    override fun onStart() {
        super.onStart()
        TorchController.addListener(torchListener)
        renderPower()
    }

    override fun onStop() {
        // Patterns only run while you're looking at them; a steady light stays on.
        stopPattern()
        TorchController.removeListener(torchListener)
        super.onStop()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun onPowerPressed() {
        when {
            mode == Mode.STEADY -> {
                if (!TorchController.toggle(level)) toast(R.string.torch_busy)
            }
            patternJob?.isActive == true -> stopPattern()
            else -> startPattern()
        }
        renderPower()
    }

    private fun startPattern() {
        val steps: List<Pair<Boolean, Long>> = when (mode) {
            Mode.SOS -> Morse.timings("SOS", 200)
            Mode.MORSE -> Morse.timings(binding.morseText.text?.toString().orEmpty(), 150)
            else -> emptyList()
        }
        if (mode == Mode.MORSE && steps.isEmpty()) {
            toast(R.string.morse_invalid)
            return
        }

        patternJob = lifecycleScope.launch {
            try {
                when (mode) {
                    Mode.STROBE -> while (true) {
                        val halfPeriod = (500 / binding.strobeSlider.value).toLong()
                        TorchController.set(true, level)
                        delay(halfPeriod)
                        TorchController.set(false)
                        delay(halfPeriod)
                    }
                    Mode.SOS -> while (true) {
                        play(steps)
                        delay(1400)
                    }
                    Mode.MORSE -> play(steps)
                    Mode.STEADY -> Unit
                }
            } finally {
                TorchController.set(false)
            }
        }.also { job -> job.invokeOnCompletion { renderPower() } }
    }

    private suspend fun play(steps: List<Pair<Boolean, Long>>) {
        for ((on, duration) in steps) {
            TorchController.set(on, level)
            delay(duration)
        }
        TorchController.set(false)
    }

    private fun stopPattern() {
        patternJob?.cancel()
        patternJob = null
    }

    private fun renderMode() {
        binding.strobePanel.visibility = if (mode == Mode.STROBE) View.VISIBLE else View.GONE
        binding.morsePanel.visibility = if (mode == Mode.MORSE) View.VISIBLE else View.GONE
    }

    private fun renderPower() {
        if (!TorchController.isAvailable) {
            binding.status.setText(R.string.no_flash)
            return
        }
        val patternRunning = patternJob?.isActive == true
        val active = if (mode == Mode.STEADY) TorchController.isOn else patternRunning
        binding.power.isActivated = active
        binding.status.setText(
            when {
                mode != Mode.STEADY && patternRunning -> R.string.pattern_running
                mode != Mode.STEADY -> R.string.pattern_ready
                active -> R.string.torch_on
                else -> R.string.torch_off
            },
        )
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
