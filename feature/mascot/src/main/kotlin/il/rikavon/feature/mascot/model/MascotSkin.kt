package il.rikavon.feature.mascot.model

import il.rikavon.core.data.model.AchievementId

/** Six visual states of every mascot, keyed by the score threshold each one represents. */
enum class MascotStage(val key: Int) {
    PRISTINE(100),
    FRESH(80),
    WORN(60),
    WILTED(40),
    ROTTING(20),
    ROTTEN(0),
    ;

    /** 0 = rotten, 1 = pristine. */
    val health: Float get() = key / MAX_KEY

    val isLow: Boolean get() = this == ROTTING || this == ROTTEN

    companion object {
        private const val MAX_KEY = 100f
        private const val PRISTINE_MIN = 90
        private const val FRESH_MIN = 70
        private const val WORN_MIN = 50
        private const val WILTED_MIN = 30
        private const val ROTTING_MIN = 10

        fun fromScore(score: Int): MascotStage =
            when {
                score >= PRISTINE_MIN -> PRISTINE
                score >= FRESH_MIN -> FRESH
                score >= WORN_MIN -> WORN
                score >= WILTED_MIN -> WILTED
                score >= ROTTING_MIN -> ROTTING
                else -> ROTTEN
            }

        fun fromKey(key: Int): MascotStage? = entries.firstOrNull { it.key == key }
    }
}

enum class HourBucket(val jsonKey: String) {
    MORNING("morning"),
    DAY("day"),
    EVENING("evening"),
    NIGHT("night"),
    ;

    companion object {
        private const val MORNING_START = 5
        private const val DAY_START = 12
        private const val EVENING_START = 17
        private const val NIGHT_START = 22

        fun of(hour: Int): HourBucket =
            when {
                hour >= NIGHT_START || hour < MORNING_START -> NIGHT
                hour >= EVENING_START -> EVENING
                hour >= DAY_START -> DAY
                else -> MORNING
            }

        fun fromKey(key: String): HourBucket? = entries.firstOrNull { it.jsonKey == key }
    }
}

/** Long-press reaction presets, chosen per mascot in its manifest. */
enum class ReactionPreset(val jsonKey: String) {
    PULSE("pulse"),
    WILT("wilt"),
    FLIP("flip"),
    TURN_AWAY("turn_away"),
    GLITCH("glitch"),
    ROLL("roll"),
    ;

    companion object {
        fun fromKey(key: String): ReactionPreset? = entries.firstOrNull { it.jsonKey == key }
    }
}

sealed interface UnlockRule {
    data object Free : UnlockRule

    data class Achievement(val id: AchievementId) : UnlockRule
}

/** Text in several languages, resolved with a fallback chain. */
data class Localized<T>(val byLanguage: Map<String, T>) {
    fun resolve(language: String): T? =
        byLanguage[language] ?: byLanguage[DEFAULT_LANGUAGE] ?: byLanguage.values.firstOrNull()

    companion object {
        const val DEFAULT_LANGUAGE = "he"
    }
}

data class StageSkin(
    val stage: MascotStage,
    /** Asset path relative to the assets root, e.g. `mascots/brain/stage_100.json`; null = use built-in fallback. */
    val lottieAsset: String?,
    val texts: Localized<List<String>>,
)

data class MascotSkin(
    val id: String,
    val name: Localized<String>,
    val themeColorArgb: Long,
    /** Optional hue for tinting surfaces when it should differ from the accent (a cool grey cat with a gold accent). */
    val surfaceTintArgb: Long?,
    val personality: String,
    val unlock: UnlockRule,
    val reaction: ReactionPreset,
    val soundAsset: String?,
    /** How the pet sounds when it reads a line aloud (text-to-speech pitch and rate). */
    val voice: VoiceProfile,
    val stages: Map<MascotStage, StageSkin>,
    val blockMessages: Localized<Map<HourBucket, List<String>>>,
    val summaries: Localized<Map<MascotStage, String>>,
) {
    fun stage(stage: MascotStage): StageSkin = stages.getValue(stage)

    companion object {
        const val MIN_TEXTS_PER_STAGE = 8
        const val MIN_BLOCK_MESSAGES_PER_BUCKET = 2
        const val SCHEMA_VERSION = 1
    }
}

/**
 * The pet's voice: for the device engine, [pitch] 0.5–2.0 (1 = neutral) and [rate] 0.5–2.0 (1 = normal); for
 * the realistic voice, the [personality] a cloud voice is cast and directed for, unless a manifest names one
 * of the speech model's voices outright in [neuralVoiceId].
 */
data class VoiceProfile(
    val pitch: Float,
    val rate: Float,
    val neuralVoiceId: String? = null,
    val personality: String = "",
) {
    companion object {
        val NEUTRAL = VoiceProfile(pitch = 1f, rate = 1f)
        private const val MIN = 0.5f
        private const val MAX = 2f

        /** A voice that fits the personality when the manifest does not specify one. */
        fun forPersonality(personality: String): VoiceProfile =
            when (personality) {
                "cynical" -> VoiceProfile(pitch = 0.75f, rate = 0.95f, personality = personality)
                "dramatic" -> VoiceProfile(pitch = 1.15f, rate = 0.85f, personality = personality)
                "confused" -> VoiceProfile(pitch = 1.35f, rate = 1.05f, personality = personality)
                "judgmental" -> VoiceProfile(pitch = 1.2f, rate = 0.9f, personality = personality)
                "bureaucratic" -> VoiceProfile(pitch = 0.6f, rate = 1.15f, personality = personality)
                "indifferent" -> VoiceProfile(pitch = 0.8f, rate = 0.8f, personality = personality)
                else -> NEUTRAL.copy(personality = personality)
            }

        fun clamped(pitch: Float, rate: Float, neuralVoiceId: String? = null, personality: String = ""): VoiceProfile =
            VoiceProfile(pitch.coerceIn(MIN, MAX), rate.coerceIn(MIN, MAX), neuralVoiceId, personality)
    }
}
