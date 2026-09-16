package il.rikavon.feature.mascot.talk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the recogniser is doing right now. */
sealed interface SpeechState {
    data object Idle : SpeechState

    data class Listening(val partial: String) : SpeechState

    data class Heard(val text: String) : SpeechState

    data class Failed(val reason: SpeechFailure) : SpeechState
}

enum class SpeechFailure { NOTHING_HEARD, NO_PERMISSION, NETWORK, UNAVAILABLE, BUSY, OTHER }

/** Something that can hear the user; [SpeechListener] on a device, a fake in tests. */
interface VoiceInput {
    val state: StateFlow<SpeechState>

    fun isAvailable(): Boolean

    fun start(languageTag: String)

    fun stop()

    fun reset()

    fun release()
}

/**
 * The user's voice, through the device's own speech recogniser (the Google app on most phones). Audio never
 * touches this app's process; this app holds no INTERNET permission. Must be used from the main thread.
 */
class SpeechListener(private val context: Context) : VoiceInput {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(languageTag: String) {
        if (!isAvailable()) {
            _state.value = SpeechState.Failed(SpeechFailure.UNAVAILABLE)
            return
        }
        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        engine.setRecognitionListener(listener)
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        _state.value = SpeechState.Listening("")
        runCatching { engine.startListening(intent) }.onFailure {
            _state.value = SpeechState.Failed(SpeechFailure.OTHER)
        }
    }

    override fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    override fun reset() {
        _state.value = SpeechState.Idle
    }

    override fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = SpeechState.Idle
    }

    private val listener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.firstResult().orEmpty()
                if (text.isNotBlank()) _state.value = SpeechState.Listening(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.firstResult().orEmpty()
                _state.value =
                    if (text.isBlank()) SpeechState.Failed(SpeechFailure.NOTHING_HEARD) else SpeechState.Heard(text)
            }

            override fun onError(error: Int) {
                _state.value =
                    SpeechState.Failed(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            -> SpeechFailure.NOTHING_HEARD
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.NO_PERMISSION
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER,
                            -> SpeechFailure.NETWORK
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.BUSY
                            else -> SpeechFailure.OTHER
                        },
                    )
            }
        }

    private fun Bundle.firstResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
