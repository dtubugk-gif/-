package il.rikavon.feature.blocker.contact

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.systemReducedMotion
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.MascotVoice
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The pet's call. Shows over the lock screen and whatever app is open, rings with vibration until answered
 * or declined, then says its lines out loud (text-to-speech) while they appear on screen, and ends with the
 * one ask: put the phone down.
 */
@AndroidEntryPoint
class PetCallActivity : ComponentActivity() {
    @Inject lateinit var selectedMascot: SelectedMascot

    @Inject lateinit var voice: MascotVoice

    @Inject lateinit var notifier: PetContactNotifier

    @Inject lateinit var settings: SettingsRepository

    private var answered by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        answered = intent.getBooleanExtra(EXTRA_ANSWERED, false)
        val petName = intent.getStringExtra(EXTRA_PET_NAME).orEmpty()
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty()
        val lines = intent.getStringArrayExtra(EXTRA_LINES)?.toList().orEmpty()
        if (!answered) startRinging()
        lifecycleScope.launch {
            val skin = selectedMascot.current() ?: return@launch finish()
            val prefs = settings.current()
            val reduced =
                when (prefs.reduceMotion) {
                    ReduceMotionMode.ON -> true
                    ReduceMotionMode.OFF -> false
                    ReduceMotionMode.SYSTEM -> systemReducedMotion(this@PetCallActivity)
                }
            if (answered) speak(skin, lines, prefs.soundsEnabled && prefs.voiceEnabled)
            setContent {
                PetCallContent(
                    skin = skin,
                    petName = petName,
                    appLabel = appLabel,
                    lines = lines,
                    answered = answered,
                    reducedMotion = reduced,
                    onAnswer = { answer(skin, lines, prefs.soundsEnabled && prefs.voiceEnabled) },
                    onDecline = ::hangUp,
                    onPromise = ::promiseAndGoHome,
                    onHangUp = ::hangUp,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ANSWERED, false) && !answered) {
            lifecycleScope.launch {
                val skin = selectedMascot.current() ?: return@launch
                val prefs = settings.current()
                answer(
                    skin,
                    intent.getStringArrayExtra(EXTRA_LINES)?.toList().orEmpty(),
                    prefs.soundsEnabled && prefs.voiceEnabled,
                )
            }
        }
    }

    override fun onDestroy() {
        stopRinging()
        voice.stop()
        super.onDestroy()
    }

    private fun answer(skin: MascotSkin, lines: List<String>, aloud: Boolean) {
        stopRinging()
        notifier.cancelCall()
        answered = true
        speak(skin, lines, aloud)
    }

    private fun speak(skin: MascotSkin, lines: List<String>, aloud: Boolean) {
        if (!aloud) return
        voice.speakLines(lines, skin.voice, UiLanguage.fromLocale(resources.configuration.locales))
    }

    private fun promiseAndGoHome() {
        hangUp()
        val home =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    private fun hangUp() {
        stopRinging()
        notifier.cancelCall()
        voice.stop()
        finish()
    }

    private fun startRinging() {
        val effect = VibrationEffect.createWaveform(RING_PATTERN, 0)
        runCatching { vibrator()?.vibrate(effect) }
        lifecycleScope.launch {
            delay(AnimationSpecs.CALL_RING_MILLIS)
            if (!answered) hangUp()
        }
    }

    private fun stopRinging() {
        runCatching { vibrator()?.cancel() }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_PET_NAME = "pet_name"
        const val EXTRA_APP_LABEL = "app_label"
        const val EXTRA_LINES = "lines"
        const val EXTRA_ANSWERED = "answered"
        private val RING_PATTERN = longArrayOf(0, 600, 400, 600, 1_200)
    }
}
