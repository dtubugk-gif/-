package il.rikavon.ui.talk

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.feature.blocker.contact.TalkContextSource
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.MascotVoice
import il.rikavon.feature.mascot.sound.VoiceIssue
import il.rikavon.feature.mascot.talk.ConversationEngine
import il.rikavon.feature.mascot.talk.ConversationEngines
import il.rikavon.feature.mascot.talk.SpeechFailure
import il.rikavon.feature.mascot.talk.SpeechListener
import il.rikavon.feature.mascot.talk.SpeechState
import il.rikavon.feature.mascot.talk.TalkContext
import il.rikavon.feature.mascot.talk.TalkScript
import il.rikavon.feature.mascot.ui.UiLanguage
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TalkMessage(val fromPet: Boolean, val text: String)

enum class TalkError { NOTHING_HEARD, NO_PERMISSION, NETWORK, UNAVAILABLE }

data class TalkUiState(
    val skin: MascotSkin? = null,
    val petName: String = "",
    val stage: MascotStage = MascotStage.PRISTINE,
    val messages: List<TalkMessage> = emptyList(),
    val listening: Boolean = false,
    val partial: String = "",
    val speaking: Boolean = false,
    /** "en" or "he": the language the pet listens in and answers in. */
    val language: String = "en",
    val micAvailable: Boolean = true,
    val error: TalkError? = null,
    /** The pet's voice is switched off in Settings, so replies are text only. */
    val voiceOff: Boolean = false,
    /** Why the last spoken reply could not be heard, if it could not. */
    val voiceIssue: VoiceIssue? = null,
)

/**
 * The typed-or-spoken conversation with the pet (the keyboard side of a call): the device recogniser hears
 * the user, the engine (the AI brain with the user's key, or the offline script) answers in the pet's
 * personality with today's numbers, and the pet says it out loud. Typed input works the same way.
 */
@HiltViewModel
class TalkViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val selectedMascot: SelectedMascot,
    private val voice: MascotVoice,
    private val texts: MascotTexts,
    private val contextSource: TalkContextSource,
    private val score: FocusScoreProvider,
    private val settings: SettingsRepository,
    engines: ConversationEngines,
) : ViewModel() {
    private val listener = SpeechListener(context)
    private val engine: Deferred<ConversationEngine> = viewModelScope.async { engines.create() }
    private val _state =
        MutableStateFlow(
            TalkUiState(
                language = TalkScript.language(UiLanguage.fromLocale(context.resources.configuration.locales)),
                micAvailable = listener.isAvailable(),
            ),
        )
    val state: StateFlow<TalkUiState> = _state.asStateFlow()

    init {
        voice.onSpeakingChanged = { speaking -> _state.update { it.copy(speaking = speaking) } }
        viewModelScope.launch { voice.issue.collect { issue -> _state.update { it.copy(voiceIssue = issue) } } }
        viewModelScope.launch {
            val skin = selectedMascot.current() ?: return@launch
            _state.update {
                it.copy(
                    skin = skin,
                    petName = texts.name(skin, it.language),
                    stage = score.currentStage(),
                )
            }
            say(engine.await().greeting(context()))
        }
        viewModelScope.launch { listener.state.collect { onListen(it) } }
    }

    fun toggleListening() {
        if (_state.value.listening) listener.stop() else listener.start(recognizerTag(_state.value.language))
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { onUserSaid(clean) }
    }

    fun setLanguage(language: String) {
        _state.update { current ->
            current.copy(
                language = language,
                petName =
                    current.skin?.let { texts.name(it, language) } ?: current.petName,
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
        listener.reset()
    }

    /** The hint said the voice is off; one tap turns sounds and the voice back on. */
    fun enableVoice() {
        viewModelScope.launch {
            settings.setAudio(soundsEnabled = true, voiceEnabled = true)
            _state.update { it.copy(voiceOff = false) }
        }
    }

    override fun onCleared() {
        listener.release()
        voice.stop()
        voice.onSpeakingChanged = null
        super.onCleared()
    }

    private suspend fun onUserSaid(text: String) {
        _state.update { it.copy(messages = it.messages + TalkMessage(fromPet = false, text = text), partial = "") }
        say(engine.await().reply(text, context()))
    }

    private suspend fun say(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(messages = it.messages + TalkMessage(fromPet = true, text = text)) }
        val prefs = settings.current()
        val skin = _state.value.skin ?: return
        val on = prefs.soundsEnabled && prefs.voiceEnabled
        _state.update { it.copy(voiceOff = !on) }
        if (on) voice.speak(text, skin.voice, _state.value.language, prompted = true)
    }

    private fun onListen(state: SpeechState) {
        when (state) {
            SpeechState.Idle -> _state.update { it.copy(listening = false, partial = "") }
            is SpeechState.Listening ->
                _state.update {
                    it.copy(
                        listening = true,
                        partial = state.partial,
                        error = null,
                    )
                }
            is SpeechState.Heard -> {
                _state.update { it.copy(listening = false, partial = "") }
                listener.reset()
                viewModelScope.launch { onUserSaid(state.text) }
            }
            is SpeechState.Failed ->
                _state.update {
                    it.copy(
                        listening = false,
                        partial = "",
                        error =
                            when (state.reason) {
                                SpeechFailure.NO_PERMISSION -> TalkError.NO_PERMISSION
                                SpeechFailure.NETWORK -> TalkError.NETWORK
                                SpeechFailure.UNAVAILABLE -> TalkError.UNAVAILABLE
                                else -> TalkError.NOTHING_HEARD
                            },
                    )
                }
        }
    }

    /** Today's numbers for the script, in the language the user picked on screen. */
    private suspend fun context(): TalkContext {
        val current = _state.value
        return contextSource.build(checkNotNull(current.skin), current.language).copy(petName = current.petName)
    }

    private fun recognizerTag(language: String): String = if (language == "he") "he-IL" else "en-US"
}
