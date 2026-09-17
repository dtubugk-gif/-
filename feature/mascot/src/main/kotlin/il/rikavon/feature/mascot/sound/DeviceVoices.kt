package il.rikavon.feature.mascot.sound

/** A voice the device's own engine offers: as much of android.speech.tts.Voice as choosing one needs. */
data class DeviceVoice(
    val name: String,
    val language: String,
    val quality: Int,
    val latency: Int,
    val network: Boolean,
)

/**
 * The best voice the device engine has for a language, chosen instead of the engine's default, which is
 * usually its plainest one. Higher quality wins; among equals a network voice when the phone is online (on
 * Google's engine those are the natural-sounding set, the local ones the robotic set), then the quicker one.
 * Pets are spread across equally good voices by personality, so two pets do not share one voice when the
 * engine has several.
 */
object DeviceVoices {
    fun pick(voices: List<DeviceVoice>, languageTag: String, personality: String, online: Boolean): DeviceVoice? {
        val wanted = NeuralSpeech.languageCode(languageTag)
        val usable =
            voices
                .filter { NeuralSpeech.languageCode(it.language) == wanted && (online || !it.network) }
                .sortedWith(
                    compareByDescending<DeviceVoice> { it.quality }
                        .thenByDescending { it.network }
                        .thenBy { it.latency }
                        .thenBy { it.name },
                )
        val best = usable.firstOrNull() ?: return null
        val peers = usable.filter { it.quality == best.quality && it.network == best.network }
        return peers[PERSONALITIES.indexOf(personality).coerceAtLeast(0) % peers.size]
    }

    /** The pets' personalities in a fixed order, so neighbours in it land on different voices. */
    private val PERSONALITIES = listOf("cynical", "dramatic", "confused", "judgmental", "bureaucratic", "indifferent")
}
