package il.rikavon.ui.settings

import android.media.AudioAttributes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.repo.CloudKey
import il.rikavon.core.data.repo.CloudKeysRepository
import il.rikavon.feature.mascot.model.VoiceProfile
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.sound.ClipPlayer
import il.rikavon.feature.mascot.sound.ElevenLabsSynthesizer
import il.rikavon.feature.mascot.sound.VoiceAttempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The outcome of the voice test, shown in the dialog. */
sealed interface VoiceTest {
    data object Running : VoiceTest

    data object Ok : VoiceTest

    data class Failed(val detail: String) : VoiceTest
}

/**
 * Behind [CloudKeyDialog]: saves or removes a key, and tries the realistic voice out loud, with the reason when
 * it fails.
 */
@HiltViewModel
class CloudKeyViewModel @Inject constructor(
    private val keys: CloudKeysRepository,
    private val selectedMascot: SelectedMascot,
) : ViewModel() {
    private val _test = MutableStateFlow<VoiceTest?>(null)
    val test: StateFlow<VoiceTest?> = _test.asStateFlow()
    private val player = ClipPlayer()

    fun save(kind: CloudKey, key: String) = viewModelScope.launch { keys.set(kind, key) }

    fun remove(kind: CloudKey) = viewModelScope.launch { keys.set(kind, null) }

    /**
     * One real request with the saved key and the selected pet's voice, played through the same player the
     * pet uses, so both halves are exercised; a refusal is shown as ElevenLabs phrased it.
     */
    fun testVoice(line: String, languageTag: String) {
        viewModelScope.launch {
            val key = keys.current(CloudKey.VOICE) ?: return@launch
            _test.value = VoiceTest.Running
            val profile = selectedMascot.current()?.voice ?: VoiceProfile.NEUTRAL
            val voiceId = profile.neuralVoiceId ?: checkNotNull(VoiceProfile.NEUTRAL.neuralVoiceId)
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
        val MEDIA: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
    }
}
