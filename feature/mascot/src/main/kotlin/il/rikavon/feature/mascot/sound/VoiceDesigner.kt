package il.rikavon.feature.mascot.sound

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** What designing a voice came back with. */
sealed interface DesignOutcome {
    /** The voice now lives in the account under [voiceId]; [preview] is the clip it was designed from. */
    class Voice(val voiceId: String, val preview: ByteArray) : DesignOutcome

    data class Failed(val status: Int, val detail: String) : DesignOutcome
}

@Serializable
private data class VoicePreview(
    @SerialName("generated_voice_id") val generatedVoiceId: String,
    @SerialName("audio_base_64") val audioBase64: String = "",
)

@Serializable
private data class DesignResponse(val previews: List<VoicePreview> = emptyList())

@Serializable
private data class CreatedVoice(
    @SerialName("voice_id") val voiceId: String,
)

/**
 * Designs a brand-new voice for a pet from a description of its personality (ElevenLabs "Voice Design"),
 * saves it into the user's own account and hands back its id. A voice the account owns is one every plan may
 * speak with, which the ready-made library voices are not on the free plan.
 */
class VoiceDesigner(private val apiKey: String) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Designs from [description] with [sample] as the preview text (100–1000 characters), then saves as [name]. */
    suspend fun designAndSave(name: String, description: String, sample: String): DesignOutcome =
        withContext(Dispatchers.IO) {
            runCatching {
                var design = post(DESIGN_URL, designBody(description, sample, MODEL_V3))
                if (design.status !=
                    HttpURLConnection.HTTP_OK
                ) {
                    design = post(DESIGN_URL, designBody(description, sample, MODEL_V2))
                }
                if (design.status != HttpURLConnection.HTTP_OK) return@runCatching design.failure()
                val preview =
                    json.decodeFromString<DesignResponse>(design.body).previews.firstOrNull()
                        ?: return@runCatching DesignOutcome.Failed(design.status, "no preview")
                val created =
                    post(
                        CREATE_URL,
                        buildJsonObject {
                            put("voice_name", name)
                            put("voice_description", description)
                            put("generated_voice_id", preview.generatedVoiceId)
                        }.toString(),
                    )
                if (created.status != HttpURLConnection.HTTP_OK) return@runCatching created.failure()
                val voiceId = json.decodeFromString<CreatedVoice>(created.body).voiceId
                DesignOutcome.Voice(voiceId, Base64.decode(preview.audioBase64, Base64.DEFAULT))
            }.getOrElse { DesignOutcome.Failed(0, it.toString().take(DETAIL_CHARS)) }
        }

    private fun designBody(description: String, sample: String, model: String): String =
        buildJsonObject {
            put("voice_description", description)
            put("model_id", model)
            put("text", sample)
            put("output_format", NeuralSpeech.OUTPUT_FORMAT)
        }.toString()

    private class Reply(val status: Int, val body: String) {
        fun failure(): DesignOutcome.Failed = DesignOutcome.Failed(status, body.take(DETAIL_CHARS))
    }

    private fun post(url: String, body: String): Reply {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("xi-api-key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val stream = if (status == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
            return Reply(status, text)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val DESIGN_URL = "https://api.elevenlabs.io/v1/text-to-voice/design"
        private const val CREATE_URL = "https://api.elevenlabs.io/v1/text-to-voice"
        private const val MODEL_V3 = "eleven_ttv_v3"
        private const val MODEL_V2 = "eleven_multilingual_ttv_v2"
        private const val CONNECT_TIMEOUT_MILLIS = 6_000
        private const val READ_TIMEOUT_MILLIS = 40_000
        private const val DETAIL_CHARS = 200

        private val PERSONALITIES: Map<String, String> =
            mapOf(
                "cynical" to
                    "A dry, sardonic man in his fifties with a deep, unhurried, slightly gravelly voice and a " +
                    "deadpan delivery; sounds unimpressed by everything.",
                "dramatic" to
                    "A theatrical, expressive woman in her thirties with a warm, musical voice that swells with " +
                    "emotion; every sentence is a small performance.",
                "confused" to
                    "A bright, bubbly young woman with a light, airy voice, easily distracted and cheerfully " +
                    "puzzled by everything.",
                "judgmental" to
                    "A poised, refined woman in her forties with a crisp, precise voice, cool and quietly " +
                    "disapproving.",
                "bureaucratic" to
                    "A flat, nasal, clipped male voice of an official reading regulations aloud, precise and " +
                    "monotone, faintly robotic.",
                "indifferent" to
                    "A slow, low, bored elderly man, mumbling slightly, utterly unbothered, always on the edge of " +
                    "a sigh.",
            )
        private const val DEFAULT_DESCRIPTION = "A warm, natural adult voice, clear and friendly."
        private const val HEBREW_NOTE = " Speaks fluent, native Israeli Hebrew with a natural Israeli accent."
        private const val ENGLISH_NOTE = " Speaks natural, clear English."

        /** The design prompt for a personality, in the language the pet will speak. */
        fun describe(personality: String, languageTag: String): String =
            (PERSONALITIES[personality] ?: DEFAULT_DESCRIPTION) +
                if (NeuralSpeech.languageCode(languageTag) == "he") HEBREW_NOTE else ENGLISH_NOTE
    }
}
