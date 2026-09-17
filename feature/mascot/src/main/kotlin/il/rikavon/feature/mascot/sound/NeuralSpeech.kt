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
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** A cloud voice: one line of text in, a short audio clip out, or the reason there is none. */
fun interface SpeechSynthesizer {
    suspend fun attempt(text: String, voiceId: String, languageTag: String, rate: Float): VoiceAttempt
}

/** The request the realistic voice sends, kept pure so it is testable without a network. */
object NeuralSpeech {
    /** ElevenLabs' low-latency multilingual model: Hebrew and English, and fast enough for a phone call. */
    const val MODEL = "eleven_flash_v2_5"

    /** Small clips, fine for speech: 22 kHz MP3 at 32 kbps. */
    const val OUTPUT_FORMAT = "mp3_22050_32"

    fun endpoint(voiceId: String): String = "$BASE_URL/$voiceId?output_format=$OUTPUT_FORMAT"

    fun requestBody(text: String, languageTag: String, rate: Float): String =
        buildJsonObject {
            put("text", text)
            put("model_id", MODEL)
            put("language_code", languageCode(languageTag))
            putJsonObject("voice_settings") {
                put("stability", STABILITY)
                put("similarity_boost", SIMILARITY)
                put("style", STYLE)
                put("use_speaker_boost", true)
                put("speed", speed(rate))
            }
        }.toString()

    /** The pet's text-to-speech rate, inside the range the cloud voice accepts. */
    fun speed(rate: Float): Float = rate.coerceIn(MIN_SPEED, MAX_SPEED)

    fun languageCode(languageTag: String): String =
        if (languageTag.startsWith("he") ||
            languageTag.startsWith("iw")
        ) {
            "he"
        } else {
            "en"
        }

    private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
    private const val STABILITY = 0.45
    private const val SIMILARITY = 0.8
    private const val STYLE = 0.2
    private const val MIN_SPEED = 0.8f
    private const val MAX_SPEED = 1.15f
}

/** What one request to the cloud voice came back with; a failure carries what the service said. */
sealed interface VoiceAttempt {
    class Clip(val bytes: ByteArray) : VoiceAttempt

    /** [status] is the HTTP status, or 0 when the request never got an answer (no network, a timeout). */
    data class Failed(val status: Int, val detail: String) : VoiceAttempt {
        /**
         * The status and the service's own sentence ("402 Free users cannot use library voices via the API"),
         * dug out of its JSON error when it sent one; otherwise the raw detail.
         */
        fun summary(): String = "$status ${message()}".trim()

        private fun message(): String =
            runCatching {
                when (val error = Json.parseToJsonElement(detail).jsonObject["detail"]) {
                    is JsonObject -> (error["message"] as? JsonPrimitive)?.content
                    is JsonPrimitive -> error.content
                    else -> null
                }
            }.getOrNull() ?: detail
    }
}

/** [SpeechSynthesizer] on the ElevenLabs API with the user's own key; plain HTTPS, no SDK. */
class ElevenLabsSynthesizer(private val apiKey: String) : SpeechSynthesizer {
    /** One request, with the reason when it fails, logged and shown to the user in the service's words. */
    override suspend fun attempt(text: String, voiceId: String, languageTag: String, rate: Float): VoiceAttempt =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(NeuralSpeech.endpoint(voiceId)).openConnection() as HttpsURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                    connection.readTimeout = READ_TIMEOUT_MILLIS
                    connection.doOutput = true
                    connection.setRequestProperty("xi-api-key", apiKey)
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "audio/mpeg")
                    connection.outputStream.use {
                        it.write(NeuralSpeech.requestBody(text, languageTag, rate).toByteArray())
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
                .also { if (it is VoiceAttempt.Failed) Log.w(TAG, "ElevenLabs ${it.summary()}") }
        }

    private companion object {
        const val TAG = "Rikavon"
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val READ_TIMEOUT_MILLIS = 8_000
        const val DETAIL_CHARS = 200
    }
}
