package il.rikavon.feature.mascot.sound

import il.rikavon.feature.mascot.model.VoiceProfile

/**
 * Which of the cloud's voices each pet speaks with, and the directions it is given: the personality as a
 * character sketch, the language with its accent, and the pace the pet's device-voice rate implies. All of
 * it is fixed text, so a pet sounds the same on every phone and nothing has to be looked up.
 */
object VoiceCasting {
    /** Every voice the speech model offers, the two newest (and best) first. */
    val VOICES: List<String> =
        listOf(
            "marin",
            "cedar",
            "alloy",
            "ash",
            "ballad",
            "coral",
            "echo",
            "fable",
            "nova",
            "onyx",
            "sage",
            "shimmer",
            "verse",
        )

    /** The voice for a pet: the user's [choice], else the manifest's, else the one cast for the personality. */
    fun voice(profile: VoiceProfile, choice: String? = null): String =
        choice?.takeIf { it in VOICES }
            ?: profile.neuralVoiceId?.takeIf { it in VOICES }
            ?: CAST[profile.personality]
            ?: DEFAULT

    /** The directions for one line: who is speaking, in which language and accent, and how fast. */
    fun instructions(profile: VoiceProfile, languageTag: String): String =
        listOf(
            FRAME,
            PERSONALITIES[profile.personality] ?: DEFAULT_CHARACTER,
            if (NeuralSpeech.languageCode(languageTag) == "he") HEBREW else ENGLISH,
            pace(profile.rate),
        ).joinToString(" ")

    private fun pace(rate: Float): String =
        when {
            rate < SLOW_BELOW -> "Speak slowly and unhurriedly."
            rate > BRISK_ABOVE -> "Speak briskly, without rushing the words."
            else -> "Speak at a natural, conversational pace."
        }

    private const val DEFAULT = "cedar"
    private const val SLOW_BELOW = 0.9f
    private const val BRISK_ABOVE = 1.1f

    private val CAST: Map<String, String> =
        mapOf(
            "cynical" to "onyx",
            "dramatic" to "ballad",
            "confused" to "coral",
            "judgmental" to "sage",
            "bureaucratic" to "echo",
            "indifferent" to "ash",
        )

    private const val FRAME =
        "You are voicing a small virtual pet that lives in a phone app and slowly rots when its owner overuses " +
            "apps. Deliver the line as natural spoken dialogue, not narration: real breath, real pauses."
    private const val DEFAULT_CHARACTER = "Character: a warm, natural adult, clear and friendly."
    private const val HEBREW =
        "Language: fluent, native Israeli Hebrew with a natural Israeli accent; pronounce every word as an " +
            "Israeli would, with Israeli intonation, never a foreign accent."
    private const val ENGLISH = "Language: natural, clear English."

    private val PERSONALITIES: Map<String, String> =
        mapOf(
            "cynical" to
                "Character: a dry, sardonic man in his fifties with a deep, unhurried, slightly gravelly voice and " +
                "a deadpan delivery; sounds unimpressed by everything.",
            "dramatic" to
                "Character: a theatrical, expressive woman in her thirties with a warm, musical voice that swells " +
                "with emotion; every sentence is a small performance.",
            "confused" to
                "Character: a bright, bubbly young woman with a light, airy voice, easily distracted and " +
                "cheerfully puzzled by everything.",
            "judgmental" to
                "Character: a poised, refined woman in her forties with a crisp, precise voice, cool and quietly " +
                "disapproving.",
            "bureaucratic" to
                "Character: a flat, nasal, clipped official reading regulations aloud, precise and monotone, " +
                "faintly robotic.",
            "indifferent" to
                "Character: a slow, low, bored elderly man, mumbling slightly, utterly unbothered, always on the " +
                "edge of a sigh.",
        )
}
