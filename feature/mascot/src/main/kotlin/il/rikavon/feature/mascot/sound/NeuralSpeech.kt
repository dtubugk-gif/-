package il.rikavon.feature.mascot.sound

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** A cloud voice: one line of text in, a short audio clip out. Null on any failure; the caller falls back. */
fun interface SpeechSynthesizer {
    suspend fun synthesize(text: String, voiceId: String, languageTag: String, rate: Float): ByteArray?
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

/** [SpeechSynthesizer] on the ElevenLabs API with the user's own key; plain HTTPS, no SDK. */
class ElevenLabsSynthesizer(private val apiKey: String) : SpeechSynthesizer {
    override suspend fun synthesize(text: String, voiceId: String, languageTag: String, rate: Float): ByteArray? =
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
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    connection.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val READ_TIMEOUT_MILLIS = 8_000
    }
}
