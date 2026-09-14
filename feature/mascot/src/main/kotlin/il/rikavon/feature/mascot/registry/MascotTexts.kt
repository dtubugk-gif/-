package il.rikavon.feature.mascot.registry

import il.rikavon.feature.mascot.model.HourBucket
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import kotlin.random.Random

/** Picks personality texts from a skin, never repeating the previous pick when there is a choice. */
class MascotTexts(private val random: Random = Random.Default) {
    private var lastStageText: String? = null
    private var lastBlockText: String? = null

    fun name(skin: MascotSkin, language: String): String = skin.name.resolve(language) ?: skin.id

    fun stageText(skin: MascotSkin, stage: MascotStage, language: String): String {
        val pool =
            skin
                .stage(stage)
                .texts
                .resolve(language)
                .orEmpty()
        return pick(pool, lastStageText).also { lastStageText = it }
    }

    fun blockMessage(skin: MascotSkin, hour: Int, language: String): String {
        val buckets = skin.blockMessages.resolve(language).orEmpty()
        val pool = buckets[HourBucket.of(hour)].orEmpty().ifEmpty { buckets.values.flatten() }
        return pick(pool, lastBlockText).also { lastBlockText = it }
    }

    fun summary(skin: MascotSkin, stage: MascotStage, language: String): String =
        skin.summaries.resolve(language)?.get(stage) ?: stageText(skin, stage, language)

    private fun pick(pool: List<String>, previous: String?): String {
        if (pool.isEmpty()) return ""
        val candidates = if (pool.size > 1 && previous != null) pool.filter { it != previous } else pool
        return candidates[random.nextInt(candidates.size)]
    }
}
