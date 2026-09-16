package il.rikavon.feature.blocker.contact

import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.usage.InstalledAppsSource
import il.rikavon.feature.blocker.engine.FocusScoreProvider
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.talk.TalkContext
import javax.inject.Inject
import javax.inject.Singleton

/** Today's numbers for the conversation script: score, the pet's stage line, and the app closest to its limit. */
@Singleton
class TalkContextSource @Inject constructor(
    private val texts: MascotTexts,
    private val usage: UsageRepository,
    private val limits: LimitsRepository,
    private val installed: InstalledAppsSource,
    private val score: FocusScoreProvider,
) {
    suspend fun build(skin: MascotSkin, language: String): TalkContext {
        val stage = score.currentStage()
        val snapshot = usage.today.value
        val worst =
            limits
                .all()
                .filter { it.enabled }
                .map { limit ->
                    val used = snapshot.usageOf(limit.packageName).minutes
                    val ratio =
                        if (limit.fullBlock) {
                            FULL_BLOCK_RATIO
                        } else {
                            used.toFloat() /
                                limit.limitMinutes.coerceAtLeast(1)
                        }
                    Triple(limit, used, ratio)
                }.maxByOrNull { it.third }
        return TalkContext(
            petName = texts.name(skin, language),
            personality = skin.personality,
            stageLine = texts.stageText(skin, stage, language),
            score = score.score.value.total,
            language = language,
            app = worst?.let { installed.label(it.first.packageName) },
            minutesLeft = worst?.let { (it.first.limitMinutes - it.second).coerceAtLeast(0) } ?: 0,
            blocked = (worst?.third ?: 0f) >= 1f,
        )
    }

    private companion object {
        const val FULL_BLOCK_RATIO = 2f
    }
}
