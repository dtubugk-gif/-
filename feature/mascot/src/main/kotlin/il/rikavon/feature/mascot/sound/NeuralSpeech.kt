package il.rikavon.feature.mascot.sound

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** A cloud voice: one line of text in, a short audio clip out, or the reason there is none. */
fun interface SpeechSynthesizer {
    suspend fun attempt(text: String, voice: String, instructions: String): VoiceAttempt
}

/** The request the realistic voice sends, kept pure so it is testable without a network. */
object NeuralSpeech {
    /** OpenAI's steerable speech model: Hebrew and English, and it takes directions on tone, accent and pace. */
    const val MODEL = "gpt-4o-mini-tts"

    const val ENDPOINT = "https://api.openai.com/v1/audio/speech"

    /** Small clips, fine for speech. */
    const val FORMAT = "mp3"

    /** The longest input the endpoint accepts. */
    const val MAX_INPUT_CHARS = 4096

    fun requestBody(text: String, voice: String, instructions: String): String =
        buildJsonObject {
            put("model", MODEL)
            put("input", text.take(MAX_INPUT_CHARS))
            put("voice", voice)
            put("instructions", instructions)
            put("response_format", FORMAT)
        }.toString()

    fun languageCode(languageTag: String): String =
        if (languageTag.startsWith("he") ||
            languageTag.startsWith("iw")
        ) {
            "he"
        } else {
            "en"
        }
}

/** What one request to the cloud voice came back with; a failure carries what the service said. */
sealed interface VoiceAttempt {
    class Clip(val bytes: ByteArray) : VoiceAttempt

    /** [status] is the HTTP status, or 0 when the request never got an answer (no network, a timeout). */
    data class Failed(val status: Int, val detail: String) : VoiceAttempt {
        /**
         * The status and the service's own sentence ("401 Incorrect API key provided"), dug out of its JSON
         * error when it sent one; otherwise the raw detail.
         */
        fun summary(): String = "$status ${message()}".trim()

        private fun message(): String =
            runCatching {
                val body = Json.parseToJsonElement(detail).jsonObject
                when (val error = body["error"] ?: body["detail"]) {
                    is JsonObject -> (error["message"] as? JsonPrimitive)?.content
                    is JsonPrimitive -> error.content
                    else -> null
                }
            }.getOrNull() ?: detail
    }
}

/** [SpeechSynthesizer] on OpenAI's speech endpoint with the user's own key; plain HTTPS, no SDK. */
class OpenAiSynthesizer(private val apiKey: String) : SpeechSynthesizer {
    /** One request, with the reason when it fails, logged and shown to the user in the service's words. */
    override suspend fun attempt(text: String, voice: String, instructions: String): VoiceAttempt =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(NeuralSpeech.ENDPOINT).openConnection() as HttpsURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                    connection.readTimeout = READ_TIMEOUT_MILLIS
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "audio/mpeg")
                    connection.outputStream.use {
                        it.write(NeuralSpeech.requestBody(text, voice, instructions).toByteArray())
                    }
                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) {
                        val detail = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                        return@runCatching VoiceAttempt.Failed(status, detail.take(DETAIL_CHARS))
                    }
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) VoiceAttempt.Failed(status, "empty audio") else VoiceAttempt.Clip(bytes)
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { VoiceAttempt.Failed(0, it.toString().take(DETAIL_CHARS)) }
                .also { if (it is VoiceAttempt.Failed) Log.w(TAG, "OpenAI speech ${it.summary()}") }
        }

    private companion object {
        const val TAG = "Rikavon"
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val READ_TIMEOUT_MILLIS = 12_000
        const val DETAIL_CHARS = 300
    }
}
