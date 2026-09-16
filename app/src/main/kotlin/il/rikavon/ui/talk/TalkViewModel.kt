package il.rikavon.ui.talk

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.MascotVoice
import il.rikavon.feature.mascot.ui.UiLanguage
import il.rikavon.talk.ScriptedConversation
import il.rikavon.talk.SpeechListener
import il.rikavon.talk.TalkContext
import il.rikavon.talk.TalkScript
import kotlinx.coroutines.delay
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
    /** Outgoing call: the pet has not picked up yet. */
    val dialing: Boolean = false,
)

/**
 * A spoken conversation with the pet: the device recogniser hears the user, the offline script answers in the
 * pet's personality with today's numbers, and the pet says it out loud. Typed input works the same way.
 */
@HiltViewModel
class TalkViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedState: SavedStateHandle,
    private val selectedMascot: SelectedMascot,
    private val voice: MascotVoice,
    private val texts: MascotTexts,
    private val usage: UsageRepository,
    private val limits: LimitsRepository,
    private val installed: InstalledAppsSource,
    private val score: FocusScoreProvider,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val listener = SpeechListener(context)
    private val engine = ScriptedConversation()
    private val _state =
        MutableStateFlow(
            TalkUiState(
                language = TalkScript.language(UiLanguage.fromLocale(context.resources.configuration.locales)),
                micAvailable = listener.isAvailable(),
                dialing = savedState.get<Boolean>(ARG_DIALING) ?: false,
            ),
        )
    val state: StateFlow<TalkUiState> = _state.asStateFlow()

    init {
        voice.onSpeakingChanged = { speaking -> _state.update { it.copy(speaking = speaking) } }
        viewModelScope.launch {
            val skin = selectedMascot.current() ?: return@launch
            _state.update {
                it.copy(
                    skin = skin,
                    petName = texts.name(skin, it.language),
                    stage = score.currentStage(),
                )
            }
            if (_state.value.dialing) {
                // The pet lets it ring a little, like anyone with something better to do.
                delay(DIAL_MILLIS)
                _state.update { it.copy(dialing = false) }
            }
            say(engine.greeting(context()))
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

    override fun onCleared() {
        listener.release()
        voice.stop()
        voice.onSpeakingChanged = null
        super.onCleared()
    }

    private suspend fun onUserSaid(text: String) {
        _state.update { it.copy(messages = it.messages + TalkMessage(fromPet = false, text = text), partial = "") }
        say(engine.reply(text, context()))
    }

    private suspend fun say(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(messages = it.messages + TalkMessage(fromPet = true, text = text)) }
        val prefs = settings.current()
        val skin = _state.value.skin ?: return
        if (prefs.soundsEnabled && prefs.voiceEnabled) voice.speak(text, skin.voice, _state.value.language)
    }

    private fun onListen(state: SpeechListener.State) {
        when (state) {
            SpeechListener.State.Idle -> _state.update { it.copy(listening = false, partial = "") }
            is SpeechListener.State.Listening ->
                _state.update {
                    it.copy(
                        listening = true,
                        partial = state.partial,
                        error = null,
                    )
                }
            is SpeechListener.State.Heard -> {
                _state.update { it.copy(listening = false, partial = "") }
                listener.reset()
                viewModelScope.launch { onUserSaid(state.text) }
            }
            is SpeechListener.State.Failed ->
                _state.update {
                    it.copy(
                        listening = false,
                        partial = "",
                        error =
                            when (state.reason) {
                                SpeechListener.Reason.NO_PERMISSION -> TalkError.NO_PERMISSION
                                SpeechListener.Reason.NETWORK -> TalkError.NETWORK
                                SpeechListener.Reason.UNAVAILABLE -> TalkError.UNAVAILABLE
                                else -> TalkError.NOTHING_HEARD
                            },
                    )
                }
        }
    }

    /** Today's numbers for the script: score, the pet's stage line, and the app closest to its limit. */
    private suspend fun context(): TalkContext {
        val current = _state.value
        val skin = checkNotNull(current.skin)
        val stage = score.currentStage()
        val snapshot = usage.today.value
        val worst =
            limits
                .all()
                .filter { it.enabled }
                .map { limit ->
                    val used = snapshot.usageOf(limit.packageName).minutes
                    val ratio =
                        if (limit.fullBlock) {
                            FULL_BLOCK_RATIO
                        } else {
                            used.toFloat() /
                                limit.limitMinutes.coerceAtLeast(1)
                        }
                    Triple(limit, used, ratio)
                }.maxByOrNull { it.third }
        return TalkContext(
            petName = current.petName,
            personality = skin.personality,
            stageLine = texts.stageText(skin, stage, current.language),
            score = score.score.value.total,
            language = current.language,
            app = worst?.let { installed.label(it.first.packageName) },
            minutesLeft = worst?.let { (it.first.limitMinutes - it.second).coerceAtLeast(0) } ?: 0,
            blocked = (worst?.third ?: 0f) >= 1f,
        )
    }

    private fun recognizerTag(language: String): String = if (language == "he") "he-IL" else "en-US"

    companion object {
        const val ARG_DIALING = "dialing"
        private const val FULL_BLOCK_RATIO = 2f
        private const val DIAL_MILLIS = 2_600L
    }
}
