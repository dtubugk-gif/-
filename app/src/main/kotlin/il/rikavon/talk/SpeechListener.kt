package il.rikavon.talk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's voice, through the device's own speech recogniser (the Google app on most phones). Audio never
 * touches this app's process; this app holds no INTERNET permission. Must be used from the main thread.
 */
class SpeechListener(private val context: Context) {
    sealed interface State {
        data object Idle : State

        data class Listening(val partial: String) : State

        data class Heard(val text: String) : State

        data class Failed(val reason: Reason) : State
    }

    enum class Reason { NOTHING_HEARD, NO_PERMISSION, NETWORK, UNAVAILABLE, BUSY, OTHER }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String) {
        if (!isAvailable()) {
            _state.value = State.Failed(Reason.UNAVAILABLE)
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
        _state.value = State.Listening("")
        runCatching { engine.startListening(intent) }.onFailure { _state.value = State.Failed(Reason.OTHER) }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun reset() {
        _state.value = State.Idle
    }

    fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = State.Idle
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
                if (text.isNotBlank()) _state.value = State.Listening(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.firstResult().orEmpty()
                _state.value = if (text.isBlank()) State.Failed(Reason.NOTHING_HEARD) else State.Heard(text)
            }

            override fun onError(error: Int) {
                _state.value =
                    State.Failed(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            -> Reason.NOTHING_HEARD
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Reason.NO_PERMISSION
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER,
                            -> Reason.NETWORK
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Reason.BUSY
                            else -> Reason.OTHER
                        },
                    )
            }
        }

    private fun Bundle.firstResult(): String? =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
