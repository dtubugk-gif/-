package il.rikavon.feature.mascot.sound

import android.util.Log
import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt

/**
 * Azure Speech: the one cloud with voices that are native Israeli Hebrew (Avri and Hila), and a free tier of
 * half a million characters a month that never turns into a bill (it stops instead). Each pet is cast to a
 * Hebrew voice and an English one by personality, and its device-voice pitch and rate become SSML prosody, so
 * the same manifest shapes the pet on every engine. Kept pure so it is testable without a network.
 */
object AzureSpeech {
    /** MP3 at 24 kHz: small clips that still sound like the voice. */
    const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"

    /** Every voice the user may pick from: the two Hebrew ones, then multilingual ones that speak both. */
    val VOICES: List<String> =
        listOf(
            "he-IL-AvriNeural",
            "he-IL-HilaNeural",
            "en-US-AndrewMultilingualNeural",
            "en-US-AvaMultilingualNeural",
            "en-US-BrianMultilingualNeural",
            "en-US-EmmaMultilingualNeural",
            "en-US-DavisMultilingualNeural",
            "en-US-JennyMultilingualNeural",
        )

    fun endpoint(region: String): String = "https://${normalizeRegion(
        region,
    )}.tts.speech.microsoft.com/cognitiveservices/v1"

    /** "West Europe" and "westeurope " both mean the region id `westeurope`. */
    fun normalizeRegion(region: String): String = region.lowercase().filter { it.isLetterOrDigit() }

    /** The voice for a pet in a language: the user's [choice], else the one cast for the personality. */
    fun voice(profile: VoiceProfile, languageTag: String, choice: String? = null): String {
        choice?.takeIf { it in VOICES }?.let { return it }
        val cast = CAST[profile.personality] ?: DEFAULT
        return if (NeuralSpeech.languageCode(languageTag) == "he") cast.first else cast.second
    }

    /** The SSML for one line: the voice, then the pet's pitch and pace as prosody, with the text escaped. */
    fun ssml(text: String, voice: String, languageTag: String, profile: VoiceProfile): String {
        val locale = if (NeuralSpeech.languageCode(languageTag) == "he") "he-IL" else "en-US"
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$locale'>" +
            "<voice name='$voice'>" +
            "<prosody pitch='${pitch(profile.pitch)}' rate='${rate(profile.rate)}'>${escape(text)}</prosody>" +
            "</voice></speak>"
    }

    /** The device engine's pitch multiplier (1 = neutral) as a relative percentage, kept where voices stay natural. */
    fun pitch(
        multiplier: Float,
    ): String = percent(((multiplier - 1f) * PITCH_SCALE).roundToInt().coerceIn(-PITCH_MAX, PITCH_MAX))

    /** The device engine's rate multiplier (1 = normal) as a relative percentage. */
    fun rate(
        multiplier: Float,
    ): String = percent(((multiplier - 1f) * RATE_SCALE).roundToInt().coerceIn(-RATE_MAX, RATE_MAX))

    private fun percent(value: Int): String = (if (value >= 0) "+" else "") + "$value%"

    fun escape(text: String): String =
        buildString(text.length) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }

    private const val PITCH_SCALE = 50f
    private const val PITCH_MAX = 20
    private const val RATE_SCALE = 100f
    private const val RATE_MAX = 30

    /** Hebrew voice to English voice, per personality. */
    private val CAST: Map<String, Pair<String, String>> =
        mapOf(
            "cynical" to ("he-IL-AvriNeural" to "en-US-AndrewMultilingualNeural"),
            "dramatic" to ("he-IL-HilaNeural" to "en-US-EmmaMultilingualNeural"),
            "confused" to ("he-IL-HilaNeural" to "en-US-AvaMultilingualNeural"),
            "judgmental" to ("he-IL-HilaNeural" to "en-US-JennyMultilingualNeural"),
            "bureaucratic" to ("he-IL-AvriNeural" to "en-US-DavisMultilingualNeural"),
            "indifferent" to ("he-IL-AvriNeural" to "en-US-BrianMultilingualNeural"),
        )
    private val DEFAULT = "he-IL-AvriNeural" to "en-US-AndrewMultilingualNeural"
}

/** [SpeechSynthesizer] on Azure Speech with the user's own resource key and region; plain HTTPS, no SDK. */
class AzureSynthesizer(private val apiKey: String, private val region: String) : SpeechSynthesizer {
    override val voices: List<String> get() = AzureSpeech.VOICES

    override suspend fun attempt(
        text: String,
        profile: VoiceProfile,
        languageTag: String,
        choice: String?,
    ): VoiceAttempt =
        withContext(Dispatchers.IO) {
            if (AzureSpeech.normalizeRegion(region).isEmpty()) {
                return@withContext VoiceAttempt.Failed(0, "no region")
            }
            val voice = AzureSpeech.voice(profile, languageTag, choice)
            runCatching {
                val connection = URL(AzureSpeech.endpoint(region)).openConnection() as HttpsURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                    connection.readTimeout = READ_TIMEOUT_MILLIS
                    connection.doOutput = true
                    connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey)
                    connection.setRequestProperty("Content-Type", "application/ssml+xml")
                    connection.setRequestProperty("X-Microsoft-OutputFormat", AzureSpeech.OUTPUT_FORMAT)
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    connection.outputStream.use {
                        it.write(AzureSpeech.ssml(text, voice, languageTag, profile).toByteArray())
                    }
                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) {
                        val detail = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                        return@runCatching VoiceAttempt.Failed(
                            status,
                            detail.take(DETAIL_CHARS).ifBlank { reason(status) },
                        )
                    }
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) VoiceAttempt.Failed(status, "empty audio") else VoiceAttempt.Clip(bytes)
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { VoiceAttempt.Failed(0, it.toString().take(DETAIL_CHARS)) }
                .also { if (it is VoiceAttempt.Failed) Log.w(TAG, "Azure speech ${it.summary()}") }
        }

    /** Azure answers most refusals with an empty body, so the status is explained in its own words. */
    private fun reason(status: Int): String =
        when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> "the key is wrong, or it belongs to another region"
            HttpURLConnection.HTTP_BAD_REQUEST -> "the request was refused (a voice this region does not have?)"
            TOO_MANY_REQUESTS -> "the free quota for this month or minute is used up"
            else -> "no details"
        }

    private companion object {
        const val TAG = "Rikavon"
        const val USER_AGENT = "Rikavon"
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val READ_TIMEOUT_MILLIS = 12_000
        const val DETAIL_CHARS = 4_000
        const val TOO_MANY_REQUESTS = 429
    }
}
