package il.rikavon.feature.mascot.registry

import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AchievementId
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.UnlockRule

/** Which mascots the user may select right now. */
object MascotUnlocks {
    fun isUnlocked(skin: MascotSkin, tier: Tier, unlocked: Set<AchievementId>): Boolean =
        when (val rule = skin.unlock) {
            UnlockRule.Free -> true
            is UnlockRule.Achievement -> tier.allMascotsUnlocked || rule.id in unlocked
        }

    fun requiredAchievement(skin: MascotSkin): AchievementId? = (skin.unlock as? UnlockRule.Achievement)?.id
}
