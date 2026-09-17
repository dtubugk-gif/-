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
import il.rikavon.feature.mascot.sound.CloudVoice
import il.rikavon.feature.mascot.sound.ElevenLabsSynthesizer
import il.rikavon.feature.mascot.sound.VoiceAttempt
import il.rikavon.feature.mascot.sound.VoiceCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The outcome of the voice test, shown in the dialog. */
sealed interface VoiceTest {
    data object Running : VoiceTest

    data object Ok : VoiceTest

    /** The account has no voice the app may use, so there was nothing to try. */
    data object NoVoice : VoiceTest

    data class Failed(val detail: String) : VoiceTest
}

/**
 * Behind [CloudKeyDialog]: saves or removes a key, lists the account's voices so the user can pick one for the
 * selected pet, and tries the realistic voice out loud, with the reason when it fails.
 */
@HiltViewModel
class CloudKeyViewModel @Inject constructor(
    private val keys: CloudKeysRepository,
    private val selectedMascot: SelectedMascot,
    private val catalog: VoiceCatalog,
    private val choices: VoiceChoicesRepository,
) : ViewModel() {
    private val _test = MutableStateFlow<VoiceTest?>(null)
    val test: StateFlow<VoiceTest?> = _test.asStateFlow()

    private val _voices = MutableStateFlow<List<CloudVoice>>(emptyList())

    /** The voices the account may use; empty until a key is saved and answered. */
    val voices: StateFlow<List<CloudVoice>> = _voices.asStateFlow()

    /** The user's pick for the selected pet; null means the app chooses by personality. */
    val choice: StateFlow<String?> =
        selectedMascot.skin
            .flatMapLatest { skin -> skin?.let { choices.choice(it.id) } ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val player = ClipPlayer()

    init {
        viewModelScope.launch {
            keys.key(CloudKey.VOICE).collect { key -> _voices.value = key?.let { catalog.voices(it) }.orEmpty() }
        }
    }

    fun save(kind: CloudKey, key: String) = viewModelScope.launch { keys.set(kind, key) }

    fun remove(kind: CloudKey) = viewModelScope.launch { keys.set(kind, null) }

    fun chooseVoice(voiceId: String?) =
        viewModelScope.launch { selectedMascot.current()?.let { choices.set(it.id, voiceId) } }

    /**
     * One real request with the saved key and the voice this pet would speak with, played through the same
     * player the pet uses, so both halves are exercised; a refusal is shown as ElevenLabs phrased it.
     */
    fun testVoice(line: String, languageTag: String) {
        viewModelScope.launch {
            val key = keys.current(CloudKey.VOICE) ?: return@launch
            _test.value = VoiceTest.Running
            val skin = selectedMascot.current()
            val profile = skin?.voice ?: VoiceProfile.NEUTRAL
            val voiceId = catalog.resolve(key, profile, skin?.let { choices.current(it.id) })
            if (voiceId == null) {
                _test.value = VoiceTest.NoVoice
                return@launch
            }
            when (val attempt = ElevenLabsSynthesizer(key).attempt(line, voiceId, languageTag, profile.rate)) {
                is VoiceAttempt.Clip -> {
                    _test.value = VoiceTest.Ok
                    player.play(attempt.bytes, MEDIA)
                }
                is VoiceAttempt.Failed -> _test.value = VoiceTest.Failed("${attempt.status} ${attempt.detail}".trim())
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
