package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/** One voice in the user's ElevenLabs account, as the voices endpoint describes it. */
@Serializable
data class CloudVoice(
    @SerialName("voice_id") val id: String,
    val name: String,
    val category: String = "",
    val labels: Map<String, String> = emptyMap(),
) {
    val gender: String get() = labels["gender"].orEmpty().lowercase()
    val age: String get() = labels["age"].orEmpty().lowercase()

    /** "warm, British" and the like, for the picker. */
    val description: String get() = listOfNotNull(labels["description"], labels["accent"]).joinToString(", ")
}

@Serializable
private data class VoicesResponse(val voices: List<CloudVoice> = emptyList())

/**
 * The voices the user's own account may use. A free ElevenLabs account may only call the voices in "My
 * Voices" (its premade set), never an arbitrary library voice, so the pet's voice is chosen from that list:
 * the user's pick for this pet, else the manifest's, else the premade voice whose gender and age fit the
 * personality. Fetched once per key and kept.
 */
@Singleton
class VoiceCatalog @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var cachedKey: String? = null
    private var cached: List<CloudVoice> = emptyList()

    suspend fun voices(apiKey: String): List<CloudVoice> =
        lock.withLock {
            if (cachedKey == apiKey && cached.isNotEmpty()) return@withLock cached
            fetch(apiKey).also {
                cached = it
                cachedKey = apiKey
            }
        }

    /** The voice id to speak [profile] with, or null when the account has no usable voice at all. */
    suspend fun resolve(apiKey: String, profile: VoiceProfile, choice: String?): String? =
        choice ?: profile.neuralVoiceId ?: pick(voices(apiKey), profile.personality)?.id

    private suspend fun fetch(apiKey: String): List<CloudVoice> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(VOICES_URL).openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = TIMEOUT_MILLIS
                    connection.readTimeout = TIMEOUT_MILLIS
                    connection.setRequestProperty("xi-api-key", apiKey)
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching emptyList()
                    val body = connection.inputStream.use { it.readBytes().decodeToString() }
                    json.decodeFromString<VoicesResponse>(body).voices
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault(emptyList())
        }

    companion object {
        private const val VOICES_URL = "https://api.elevenlabs.io/v1/voices"
        private const val TIMEOUT_MILLIS = 6_000
        private const val PREMADE = "premade"
        private const val PREMADE_POINTS = 8
        private const val GENDER_POINTS = 4

        /** What each personality sounds like: a gender, and the ages that fit, best first. */
        private val WANTED: Map<String, Pair<String, List<String>>> =
            mapOf(
                "cynical" to ("male" to listOf("middle_aged", "middle-aged", "old")),
                "dramatic" to ("female" to listOf("young", "middle_aged", "middle-aged")),
                "confused" to ("female" to listOf("young")),
                "judgmental" to ("female" to listOf("middle_aged", "middle-aged")),
                "bureaucratic" to ("male" to listOf("middle_aged", "middle-aged", "old")),
                "indifferent" to ("male" to listOf("old", "middle_aged", "middle-aged")),
            )

        /**
         * The voice that fits [personality] best: the account's premade voices first (the only ones a free plan
         * may call), then the wanted gender, then the best-fitting age; ties go by name so the pick is stable.
         */
        fun pick(voices: List<CloudVoice>, personality: String): CloudVoice? {
            if (voices.isEmpty()) return null
            val (gender, ages) = WANTED[personality] ?: ("" to emptyList())
            return voices
                .sortedBy { it.name }
                .maxByOrNull { voice ->
                    val agePoints = ages.indexOf(voice.age).let { if (it < 0) 0 else ages.size - it }
                    (if (voice.category == PREMADE) PREMADE_POINTS else 0) +
                        (if (gender.isNotEmpty() && voice.gender == gender) GENDER_POINTS else 0) +
                        agePoints
                }
        }
    }
}
