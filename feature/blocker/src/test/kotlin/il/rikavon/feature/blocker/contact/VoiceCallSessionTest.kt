package il.rikavon.feature.blocker.contact

import il.rikavon.feature.mascot.model.VoiceProfile
import il.rikavon.feature.mascot.sound.VoiceIssue
import il.rikavon.feature.mascot.sound.VoiceOutput
import il.rikavon.feature.mascot.talk.ConversationEngine
import il.rikavon.feature.mascot.talk.SpeechFailure
import il.rikavon.feature.mascot.talk.SpeechState
import il.rikavon.feature.mascot.talk.TalkContext
import il.rikavon.feature.mascot.talk.TalkIntent
import il.rikavon.feature.mascot.talk.VoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCallSessionTest {
    private class FakeVoice : VoiceOutput {
        override val issue = MutableStateFlow<VoiceIssue?>(null)
        override var onSpeakingChanged: ((Boolean) -> Unit)? = null
        override var onLineStarted: ((Int) -> Unit)? = null
        val spoken = mutableListOf<List<String>>()
        var stopped = 0

        override fun speakLines(
            lines: List<String>,
            profile: VoiceProfile,
            languageTag: String,
            prompted: Boolean,
            inCall: Boolean,
        ) {
            spoken += lines
            onLineStarted?.invoke(0)
            onSpeakingChanged?.invoke(true)
            if (issue.value == VoiceIssue.NO_ENGINE) onSpeakingChanged?.invoke(false)
        }

        override fun stop() {
            stopped++
            onSpeakingChanged?.invoke(false)
        }

        fun finishSpeaking() = onSpeakingChanged?.invoke(false)
    }

    private class FakeEars : VoiceInput {
        override val state = MutableStateFlow<SpeechState>(SpeechState.Idle)
        var starts = 0
        var stops = 0
        var available = true

        override fun isAvailable() = available

        override fun start(languageTag: String) {
            starts++
            state.value = SpeechState.Listening("")
        }

        override fun stop() {
            stops++
        }

        override fun reset() {
            state.value = SpeechState.Idle
        }

        override fun release() = Unit

        fun hear(text: String) {
            state.value = SpeechState.Heard(text)
        }

        fun fail(reason: SpeechFailure) {
            state.value = SpeechState.Failed(reason)
        }
    }

    private companion object {
        const val RETRY_ADVANCE = 20L
    }

    private object Echo : ConversationEngine {
        override fun reply(userText: String, context: TalkContext) = "re: $userText"

        override fun greeting(context: TalkContext) = "hi"

        override fun answer(intent: TalkIntent, context: TalkContext) = intent.name
    }

    private val context =
        TalkContext(petName = "Potato", personality = "indifferent", stageLine = "", score = 50, language = "en")

    private fun session(
        scope: CoroutineScope,
        voice: FakeVoice,
        ears: FakeEars,
        incoming: Boolean,
        aloud: Boolean = true,
    ) = VoiceCallSession(
        scope = scope,
        voice = voice,
        ears = ears,
        engine = Echo,
        config =
            VoiceCallSession.Config(
                voiceProfile = VoiceProfile.NEUTRAL,
                language = "en",
                incoming = incoming,
                aloud = aloud,
                timings = VoiceCallSession.Timings(dialMillis = 100, readMillis = 50, retryMillis = 10),
            ),
        context = { context },
    )

    @Test
    fun `an outgoing call rings, the pet picks up, greets, then listens`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice()
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = false)
            call.start()
            assertEquals(CallPhase.DIALING, call.state.value.phase)
            advanceTimeBy(150)
            assertEquals(CallPhase.CONNECTED, call.state.value.phase)
            assertEquals(listOf(listOf("hi")), voice.spoken)
            assertEquals(CallTurn.SPEAKING, call.state.value.turn)
            assertEquals("hi", call.state.value.caption)
            voice.finishSpeaking()
            advanceUntilIdle()
            assertEquals(CallTurn.LISTENING, call.state.value.turn)
            assertEquals(1, ears.starts)
        }

    @Test
    fun `what the user says gets a reply, and bye ends the call`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice()
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = false)
            call.start()
            advanceTimeBy(150)
            voice.finishSpeaking()
            advanceUntilIdle()
            ears.state.value = SpeechState.Listening("five more")
            advanceUntilIdle()
            assertEquals("five more", call.state.value.heard)
            ears.hear("five more minutes")
            advanceUntilIdle()
            assertEquals(listOf("re: five more minutes"), voice.spoken.last())
            voice.finishSpeaking()
            advanceUntilIdle()
            assertEquals(CallTurn.LISTENING, call.state.value.turn)
            ears.hear("ok bye")
            advanceUntilIdle()
            assertEquals(CallPhase.CONNECTED, call.state.value.phase)
            voice.finishSpeaking()
            advanceUntilIdle()
            assertEquals(CallPhase.ENDED, call.state.value.phase)
            assertEquals(CallEnd.HUNG_UP, call.state.value.ended)
        }

    @Test
    fun `an incoming call speaks its lines after answer, and a spoken promise ends it as one`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice()
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = true)
            assertEquals(CallPhase.RINGING, call.state.value.phase)
            assertTrue(voice.spoken.isEmpty())
            call.answer(listOf("It's Potato.", "Put the phone down."))
            assertEquals(listOf("It's Potato.", "Put the phone down."), voice.spoken.single())
            voice.onLineStarted?.invoke(1)
            assertEquals("Put the phone down.", call.state.value.caption)
            voice.finishSpeaking()
            advanceUntilIdle()
            ears.hear("ok fine, I'll stop")
            advanceUntilIdle()
            assertEquals(listOf("re: ok fine, I'll stop"), voice.spoken.last())
            voice.finishSpeaking()
            advanceUntilIdle()
            assertEquals(CallEnd.PROMISED, call.state.value.ended)
        }

    @Test
    fun `silence gets a retry, then a nudge, then goodbye`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice()
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = false)
            call.start()
            advanceTimeBy(150)
            voice.finishSpeaking()
            advanceUntilIdle()
            ears.fail(SpeechFailure.NOTHING_HEARD)
            // The retry re-listens after a short delay, and that delayed task lives in backgroundScope,
            // which advanceUntilIdle skips; advanceTimeBy runs it.
            advanceTimeBy(RETRY_ADVANCE)
            assertEquals(2, ears.starts)
            ears.fail(SpeechFailure.NOTHING_HEARD)
            advanceUntilIdle()
            assertEquals(listOf("SILENCE"), voice.spoken.last())
            voice.finishSpeaking()
            advanceUntilIdle()
            ears.fail(SpeechFailure.NOTHING_HEARD)
            advanceUntilIdle()
            assertEquals(listOf("BYE"), voice.spoken.last())
            voice.finishSpeaking()
            advanceUntilIdle()
            assertEquals(CallPhase.ENDED, call.state.value.phase)
        }

    @Test
    fun `mute stops listening and unmute resumes`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice()
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = false)
            call.start()
            advanceTimeBy(150)
            voice.finishSpeaking()
            advanceUntilIdle()
            call.toggleMute()
            assertEquals(CallTurn.IDLE, call.state.value.turn)
            assertEquals(1, ears.stops)
            call.toggleMute()
            assertEquals(CallTurn.LISTENING, call.state.value.turn)
            assertEquals(2, ears.starts)
        }

    @Test
    fun `without the microphone permission the pet waits, and listens once it is granted`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice()
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = false)
            call.start()
            advanceTimeBy(150)
            voice.finishSpeaking()
            advanceUntilIdle()
            ears.fail(SpeechFailure.NO_PERMISSION)
            advanceUntilIdle()
            assertEquals(Hearing.NO_PERMISSION, call.state.value.hearing)
            assertEquals(CallTurn.IDLE, call.state.value.turn)
            call.hearing(Hearing.OK)
            assertEquals(CallTurn.LISTENING, call.state.value.turn)
        }

    @Test
    fun `with no voice engine the lines are shown at reading pace instead`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice().apply { issue.value = VoiceIssue.NO_ENGINE }
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = true)
            call.answer(listOf("one", "two"))
            assertTrue(voice.spoken.isEmpty())
            assertEquals("one", call.state.value.caption)
            advanceTimeBy(60)
            assertEquals("two", call.state.value.caption)
            advanceTimeBy(60)
            assertEquals(CallTurn.LISTENING, call.state.value.turn)
        }

    @Test
    fun `hanging up stops the voice and the call timer`() =
        runTest(UnconfinedTestDispatcher()) {
            val voice = FakeVoice()
            val ears = FakeEars()
            val call = session(backgroundScope, voice, ears, incoming = false)
            call.start()
            advanceTimeBy(2_150)
            assertEquals(2, call.state.value.seconds)
            call.hangUp()
            assertEquals(CallPhase.ENDED, call.state.value.phase)
            assertEquals(1, voice.stopped)
            advanceTimeBy(5_000)
            assertEquals(2, call.state.value.seconds)
            assertFalse(call.state.value.turn == CallTurn.LISTENING)
        }
}
