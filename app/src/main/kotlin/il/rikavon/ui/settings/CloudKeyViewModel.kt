package il.rikavon.ui.settings

import android.media.AudioAttributes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.repo.CloudKey
import il.rikavon.core.data.repo.CloudKeysRepository
import il.rikavon.core.data.repo.VoiceChoicesRepository
import il.rikavon.feature.mascot.model.VoiceProfile
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.ClipPlayer
import il.rikavon.feature.mascot.sound.NeuralSpeech
import il.rikavon.feature.mascot.sound.SpeechProvider
import il.rikavon.feature.mascot.sound.VoiceAttempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The outcome of the voice test, shown in the dialog. */
sealed interface VoiceTest {
    data object Running : VoiceTest

    data object Ok : VoiceTest

    /** The saved key is an Anthropic key (the AI brain's), so there was no point asking a voice service. */
    data object WrongProvider : VoiceTest

    /** An Azure key without its region: the request has nowhere to go. */
    data object NoRegion : VoiceTest

    data class Failed(val detail: String) : VoiceTest
}

/** What the saved voice key is, for the dialog: which provider, and the voices it offers. */
data class VoiceSetup(val provider: SpeechProvider, val region: String?, val voices: List<String>)

/**
 * Behind [CloudKeyDialog]: saves or removes a key (and, for Azure, its region), lets the user pick one of the
 * provider's voices for the selected pet, and tries the realistic voice out loud, with the reason when it fails.
 */
@HiltViewModel
class CloudKeyViewModel @Inject constructor(
    private val keys: CloudKeysRepository,
    private val selectedMascot: SelectedMascot,
    private val choices: VoiceChoicesRepository,
) : ViewModel() {
    private val _test = MutableStateFlow<VoiceTest?>(null)
    val test: StateFlow<VoiceTest?> = _test.asStateFlow()

    /** The saved voice key's provider, region and voices; null while no key is saved. */
    val setup: StateFlow<VoiceSetup?> =
        combine(keys.key(CloudKey.VOICE), keys.voiceRegion) { key, region ->
            key?.let { NeuralSpeech.synthesizer(it, region) }?.let {
                VoiceSetup(NeuralSpeech.provider(key), region, it.voices)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The user's pick for the selected pet; null means the one cast for its personality. */
    val choice: StateFlow<String?> =
        selectedMascot.skin
            .flatMapLatest { skin -> skin?.let { choices.choice(it.id) } ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val player = ClipPlayer()

    /** Saves the key; for the voice, the Azure [region] too (ignored for an OpenAI key). */
    fun save(kind: CloudKey, key: String, region: String = "") =
        viewModelScope.launch {
            keys.set(kind, key)
            if (kind == CloudKey.VOICE) keys.setVoiceRegion(region)
        }

    fun remove(kind: CloudKey) =
        viewModelScope.launch {
            keys.set(kind, null)
            if (kind == CloudKey.VOICE) keys.setVoiceRegion(null)
        }

    fun chooseVoice(voice: String?) =
        viewModelScope.launch { selectedMascot.current()?.let { choices.set(it.id, voice) } }

    /**
     * One real request with the saved key, the voice this pet would speak with, played through the same player
     * the pet uses, so both halves are exercised; a refusal is shown as the service phrased it.
     */
    fun testVoice(line: String, languageTag: String) {
        viewModelScope.launch {
            val key = keys.current(CloudKey.VOICE) ?: return@launch
            if (KeyWarning.of(CloudKey.VOICE, key) != null) {
                _test.value = VoiceTest.WrongProvider
                return@launch
            }
            val region = keys.currentVoiceRegion()
            if (NeuralSpeech.provider(key) == SpeechProvider.AZURE && region.isNullOrBlank()) {
                _test.value = VoiceTest.NoRegion
                return@launch
            }
            _test.value = VoiceTest.Running
            val skin = selectedMascot.current()
            val profile = skin?.voice ?: VoiceProfile.NEUTRAL
            val choice = skin?.let { choices.current(it.id) }
            when (val attempt = NeuralSpeech.synthesizer(key, region).attempt(line, profile, languageTag, choice)) {
                is VoiceAttempt.Clip -> {
                    _test.value = VoiceTest.Ok
                    player.play(attempt.bytes, MEDIA)
                }
                is VoiceAttempt.Failed -> _test.value = VoiceTest.Failed(attempt.summary())
            }
        }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val MEDIA: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
    }
}
