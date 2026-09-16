package il.rikavon.feature.blocker.contact

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.systemReducedMotion
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.MascotVoice
import il.rikavon.feature.mascot.talk.ConversationEngines
import il.rikavon.feature.mascot.talk.SpeechListener
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A phone call with the pet. Incoming: shows over the lock screen and whatever app is open, rings with
 * vibration until answered or declined. Outgoing: rings for a moment, then the pet picks up. Either way the
 * call is spoken both ways ([VoiceCallSession]) and routed like a call ([CallAudioRoute]). A promise ("I'll
 * stop", said or pressed) ends an incoming call and sends the user home.
 */
@AndroidEntryPoint
class PetCallActivity : ComponentActivity() {
    @Inject lateinit var selectedMascot: SelectedMascot

    @Inject lateinit var voice: MascotVoice

    @Inject lateinit var notifier: PetContactNotifier

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var texts: MascotTexts

    @Inject lateinit var contextSource: TalkContextSource

    @Inject lateinit var engines: ConversationEngines

    private val ringer by lazy { CallRinger(applicationContext) }
    private var session: VoiceCallSession? = null
    private var route: CallAudioRoute? = null
    private var lines: List<String> = emptyList()
    private var connected = false
    private var ending = false

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            session?.hearing(if (granted) Hearing.OK else Hearing.NO_PERMISSION)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        val outgoing = intent.getBooleanExtra(EXTRA_OUTGOING, false)
        val answered = intent.getBooleanExtra(EXTRA_ANSWERED, false)
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty()
        lines = intent.getStringArrayExtra(EXTRA_LINES)?.toList().orEmpty()
        if (!outgoing && !answered) startRinging()
        lifecycleScope.launch {
            val skin = selectedMascot.current() ?: return@launch finish()
            val prefs = settings.current()
            val language = UiLanguage.fromLocale(resources.configuration.locales)
            val petName = intent.getStringExtra(EXTRA_PET_NAME) ?: texts.name(skin, language)
            val reduced =
                when (prefs.reduceMotion) {
                    ReduceMotionMode.ON -> true
                    ReduceMotionMode.OFF -> false
                    ReduceMotionMode.SYSTEM -> systemReducedMotion(this@PetCallActivity)
                }
            val session =
                VoiceCallSession(
                    scope = lifecycleScope,
                    voice = voice,
                    ears = SpeechListener(applicationContext),
                    engine = engines.create(),
                    config =
                        VoiceCallSession.Config(
                            voiceProfile = skin.voice,
                            language = language,
                            incoming = !outgoing,
                            aloud = prefs.soundsEnabled && prefs.voiceEnabled,
                        ),
                    context = { contextSource.build(skin, language) },
                )
            this@PetCallActivity.session = session
            launch { session.state.collect { onState(it) } }
            if (outgoing) {
                ensureMic()
                session.start()
            } else if (answered) {
                connect()
            }
            setContent {
                val state by session.state.collectAsState()
                PetCallContent(
                    skin = skin,
                    petName = petName,
                    appLabel = appLabel,
                    state = state,
                    reducedMotion = reduced,
                    onAnswer = ::connect,
                    onDecline = ::decline,
                    onHangUp = session::hangUp,
                    onMute = session::toggleMute,
                    onSpeaker = session::toggleSpeaker,
                    onKeyboard = ::openKeyboard,
                    onPromise = session::promise,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ANSWERED, false) && !connected) {
            lines = intent.getStringArrayExtra(EXTRA_LINES)?.toList().orEmpty()
            connect()
        }
    }

    override fun onDestroy() {
        stopRinging()
        route?.end()
        route = null
        session?.release()
        voice.stop()
        super.onDestroy()
    }

    /** The user picked up: stop ringing, make sure the pet can hear, and let it talk. */
    private fun connect() {
        if (connected) return
        connected = true
        stopRinging()
        notifier.cancelCall()
        ensureMic()
        session?.answer(lines)
    }

    private fun decline() {
        stopRinging()
        notifier.cancelCall()
        session?.hangUp() ?: finish()
    }

    private fun ensureMic() {
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) session?.hearing(Hearing.OK) else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun onState(state: VoiceCallState) {
        when (state.phase) {
            CallPhase.CONNECTED -> {
                val active = route ?: CallAudioRoute(this).also { it.begin(state.speaker) }.also { route = it }
                if (active.speaker != state.speaker) active.apply(state.speaker)
            }
            CallPhase.ENDED -> endCall(state.ended)
            CallPhase.RINGING, CallPhase.DIALING -> Unit
        }
    }

    private fun endCall(end: CallEnd?) {
        if (ending) return
        ending = true
        stopRinging()
        notifier.cancelCall()
        route?.end()
        route = null
        if (end == CallEnd.PROMISED) goHome()
        finish()
    }

    private fun goHome() {
        val home =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    /** No microphone, or a quiet place: end the call and continue in the app's typed conversation. */
    private fun openKeyboard() {
        packageManager
            .getLaunchIntentForPackage(packageName)
            ?.putExtra(DeepLinks.EXTRA_OPEN, DeepLinks.OPEN_TALK)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { launch -> runCatching { startActivity(launch) } }
        session?.hangUp() ?: finish()
    }

    /** Vibration only: the call notification's channel plays the ringtone on this path. */
    private fun startRinging() {
        ringer.start(tone = false)
        lifecycleScope.launch {
            delay(AnimationSpecs.CALL_RING_MILLIS)
            if (!connected) decline()
        }
    }

    private fun stopRinging() = ringer.stop()

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
        const val EXTRA_OUTGOING = "outgoing"

        /** The user calls the pet. */
        fun outgoing(context: Context): Intent =
            Intent(context, PetCallActivity::class.java)
                .putExtra(EXTRA_OUTGOING, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
