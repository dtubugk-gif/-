package il.rikavon.feature.blocker.contact

import il.rikavon.feature.mascot.model.VoiceProfile
import il.rikavon.feature.mascot.sound.VoiceIssue
import il.rikavon.feature.mascot.sound.VoiceOutput
import il.rikavon.feature.mascot.talk.ConversationEngine
import il.rikavon.feature.mascot.talk.SpeechFailure
import il.rikavon.feature.mascot.talk.SpeechState
import il.rikavon.feature.mascot.talk.TalkContext
import il.rikavon.feature.mascot.talk.TalkIntent
import il.rikavon.feature.mascot.talk.TalkScript
import il.rikavon.feature.mascot.talk.VoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CallPhase { RINGING, DIALING, CONNECTED, ENDED }

enum class CallTurn { IDLE, SPEAKING, LISTENING }

enum class CallEnd { HUNG_UP, PROMISED }

/** Whether the pet can hear the user at all. */
enum class Hearing { OK, NO_PERMISSION, UNAVAILABLE }

data class VoiceCallState(
    val phase: CallPhase,
    val incoming: Boolean,
    val turn: CallTurn = CallTurn.IDLE,
    /** The line the pet is saying right now (a live caption). */
    val caption: String = "",
    /** What the recogniser has heard so far in the user's current turn. */
    val heard: String = "",
    val muted: Boolean = false,
    val speaker: Boolean = true,
    val hearing: Hearing = Hearing.OK,
    val seconds: Int = 0,
    val voiceIssue: VoiceIssue? = null,
    val ended: CallEnd? = null,
)

/**
 * A phone call with the pet, hands-free: the pet says its lines out loud, then listens, then answers what it
 * heard, until someone hangs up. Silence gets one retry, then a nudge, then the pet says goodbye. "Bye" ends
 * the call; on an incoming call a promise ("I'll stop") ends it too, and the activity sends the user home.
 * The state machine is plain Kotlin so the whole loop is unit-tested with fake ears and a fake voice.
 */
class VoiceCallSession(
    private val scope: CoroutineScope,
    private val voice: VoiceOutput,
    private val ears: VoiceInput,
    private val engine: ConversationEngine,
    private val config: Config,
    private val context: suspend () -> TalkContext,
) {
    /** Everything about this particular call except its moving parts (the voice, the ears, the engine). */
    data class Config(
        val voiceProfile: VoiceProfile,
        val language: String,
        val incoming: Boolean,
        /** The pet's voice is on (sounds and voice enabled); false reads the lines as captions instead. */
        val aloud: Boolean = true,
        val timings: Timings = Timings(),
    )

    /** The call's pacing, overridable in tests: how long it rings, how long a read line stays, the retry gap. */
    data class Timings(
        val dialMillis: Long = DIAL_MILLIS,
        val readMillis: Long = READ_MILLIS,
        val retryMillis: Long = RETRY_MILLIS,
    )

    private val _state =
        MutableStateFlow(
            VoiceCallState(
                phase = if (config.incoming) CallPhase.RINGING else CallPhase.DIALING,
                incoming = config.incoming,
            ),
        )
    val state: StateFlow<VoiceCallState> = _state.asStateFlow()

    @Volatile
    private var lines: List<String> = emptyList()
    private var endAfterSpeech: CallEnd? = null
    private var silences = 0
    private var timer: Job? = null
    private var reading: Job? = null

    init {
        voice.onSpeakingChanged = { speaking -> if (!speaking) scope.launch { spoken() } }
        voice.onLineStarted =
            { index -> lines.getOrNull(index)?.let { line -> _state.update { it.copy(caption = line) } } }
        scope.launch { voice.issue.collect { issue -> _state.update { it.copy(voiceIssue = issue) } } }
        scope.launch { ears.state.collect { onEars(it) } }
    }

    /** Outgoing: let it ring a little, then the pet picks up and opens. Incoming: nothing until [answer]. */
    fun start() {
        if (_state.value.phase != CallPhase.DIALING) return
        scope.launch {
            delay(config.timings.dialMillis)
            if (_state.value.phase == CallPhase.DIALING) connect(listOf(engine.greeting(context())))
        }
    }

    /** The user picked up an incoming call; the pet says [opening] and then listens. */
    fun answer(opening: List<String>) {
        if (_state.value.phase != CallPhase.RINGING) return
        connect(opening)
    }

    fun hangUp() = finish(CallEnd.HUNG_UP)

    /** The "I'll stop" button: the pet acknowledges out loud, then the call ends as a promise. */
    fun promise() {
        if (_state.value.phase != CallPhase.CONNECTED) return
        scope.launch { say(listOf(engine.answer(TalkIntent.PROMISE, context())), CallEnd.PROMISED) }
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        _state.update { it.copy(muted = muted) }
        if (muted && _state.value.turn == CallTurn.LISTENING) {
            ears.stop()
            ears.reset()
            _state.update { it.copy(turn = CallTurn.IDLE, heard = "") }
        } else if (!muted && _state.value.turn == CallTurn.IDLE) {
            listen()
        }
    }

    fun toggleSpeaker() = _state.update { it.copy(speaker = !it.speaker) }

    /** The microphone permission came back, or the recogniser turned out to be missing. */
    fun hearing(hearing: Hearing) {
        _state.update { it.copy(hearing = hearing) }
        if (hearing == Hearing.OK && _state.value.turn == CallTurn.IDLE) listen()
    }

    fun release() {
        timer?.cancel()
        reading?.cancel()
        voice.onSpeakingChanged = null
        voice.onLineStarted = null
        ears.release()
    }

    private fun connect(opening: List<String>) {
        _state.update { it.copy(phase = CallPhase.CONNECTED) }
        timer =
            scope.launch {
                while (isActive) {
                    delay(SECOND_MILLIS)
                    _state.update { it.copy(seconds = it.seconds + 1) }
                }
            }
        say(opening)
    }

    private fun say(lines: List<String>, thenEnd: CallEnd? = null) {
        if (_state.value.phase != CallPhase.CONNECTED) return
        val spoken = lines.filter { it.isNotBlank() }
        this.lines = spoken
        endAfterSpeech = thenEnd
        if (spoken.isEmpty()) {
            scope.launch { afterSpeaking() }
            return
        }
        _state.update { it.copy(turn = CallTurn.SPEAKING, caption = spoken.first(), heard = "") }
        val issue = voice.issue.value
        val voiceWorks = config.aloud && issue != VoiceIssue.NO_ENGINE && issue != VoiceIssue.NO_LANGUAGE
        if (voiceWorks) {
            voice.speakLines(
                spoken,
                config.voiceProfile,
                config.language,
                prompted = true,
                inCall = true,
            )
        } else {
            read(spoken)
        }
    }

    /** No voice: the lines stay on screen at reading pace instead. */
    private fun read(lines: List<String>) {
        reading?.cancel()
        reading =
            scope.launch {
                for (line in lines) {
                    _state.update { it.copy(caption = line) }
                    delay(config.timings.readMillis)
                }
                afterSpeaking()
            }
    }

    /** The voice fell silent: either it finished, or it failed and the rest of the lines are read instead. */
    private suspend fun spoken() {
        val current = _state.value
        if (current.phase != CallPhase.CONNECTED || current.turn != CallTurn.SPEAKING) return
        val issue = voice.issue.value
        if (issue == VoiceIssue.NO_ENGINE || issue == VoiceIssue.NO_LANGUAGE) read(lines.drop(1)) else afterSpeaking()
    }

    private suspend fun afterSpeaking() {
        if (_state.value.phase != CallPhase.CONNECTED) return
        endAfterSpeech?.let {
            finish(it)
            return
        }
        listen()
    }

    private fun listen() {
        val current = _state.value
        if (current.phase != CallPhase.CONNECTED) return
        if (current.muted || current.hearing != Hearing.OK) {
            _state.update { it.copy(turn = CallTurn.IDLE) }
            return
        }
        if (!ears.isAvailable()) {
            _state.update { it.copy(turn = CallTurn.IDLE, hearing = Hearing.UNAVAILABLE) }
            return
        }
        _state.update { it.copy(turn = CallTurn.LISTENING, heard = "") }
        ears.start(recognizerTag(config.language))
    }

    private fun onEars(heard: SpeechState) {
        when (heard) {
            SpeechState.Idle -> Unit
            is SpeechState.Listening -> _state.update { it.copy(heard = heard.partial) }
            is SpeechState.Heard -> {
                ears.reset()
                silences = 0
                _state.update { it.copy(heard = heard.text) }
                respond(heard.text)
            }
            is SpeechState.Failed -> {
                ears.reset()
                when (heard.reason) {
                    SpeechFailure.NO_PERMISSION ->
                        _state.update {
                            it.copy(
                                turn = CallTurn.IDLE,
                                hearing = Hearing.NO_PERMISSION,
                            )
                        }
                    SpeechFailure.NETWORK,
                    SpeechFailure.UNAVAILABLE,
                    -> _state.update { it.copy(turn = CallTurn.IDLE, hearing = Hearing.UNAVAILABLE) }
                    else -> onSilence()
                }
            }
        }
    }

    private fun respond(text: String) {
        if (_state.value.phase != CallPhase.CONNECTED) return
        scope.launch {
            val intent = TalkScript.intentOf(text, config.language)
            val end =
                when {
                    intent == TalkIntent.BYE -> CallEnd.HUNG_UP
                    intent == TalkIntent.PROMISE && _state.value.incoming -> CallEnd.PROMISED
                    else -> null
                }
            say(listOf(engine.reply(text, context())), end)
        }
    }

    /** Nothing came back: listen again once, then ask, then say goodbye. */
    private fun onSilence() {
        val current = _state.value
        if (current.phase != CallPhase.CONNECTED || current.turn != CallTurn.LISTENING) return
        silences++
        scope.launch {
            when {
                silences < NUDGE_AT -> {
                    delay(config.timings.retryMillis)
                    listen()
                }
                silences < HANG_UP_AT -> say(listOf(engine.answer(TalkIntent.SILENCE, context())))
                else -> say(listOf(engine.answer(TalkIntent.BYE, context())), CallEnd.HUNG_UP)
            }
        }
    }

    private fun finish(end: CallEnd) {
        if (_state.value.phase == CallPhase.ENDED) return
        _state.update { it.copy(phase = CallPhase.ENDED, turn = CallTurn.IDLE, ended = end) }
        timer?.cancel()
        reading?.cancel()
        ears.stop()
        voice.stop()
    }

    private fun recognizerTag(language: String): String = if (language == "he") "he-IL" else "en-US"

    companion object {
        const val DIAL_MILLIS = 2_600L
        const val READ_MILLIS = 2_200L
        const val RETRY_MILLIS = 400L
        private const val SECOND_MILLIS = 1_000L
        private const val NUDGE_AT = 2
        private const val HANG_UP_AT = 3
    }
}
