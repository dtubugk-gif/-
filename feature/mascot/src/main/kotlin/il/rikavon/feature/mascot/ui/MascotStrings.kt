package il.rikavon.feature.mascot.ui

import androidx.annotation.StringRes
import il.rikavon.core.data.model.AchievementId
import il.rikavon.feature.mascot.R

/** Resource lookups shared by gallery, achievements and home. */
object MascotStrings {
    @StringRes
    fun achievementTitle(id: AchievementId): Int =
        when (id) {
            AchievementId.FIRST_LIMIT -> R.string.achievement_first_limit
            AchievementId.FIRST_CLEAN_DAY -> R.string.achievement_first_clean_day
            AchievementId.STREAK_3 -> R.string.achievement_streak_3
            AchievementId.STREAK_7 -> R.string.achievement_streak_7
            AchievementId.STREAK_30 -> R.string.achievement_streak_30
            AchievementId.FEWER_OPENS_50 -> R.string.achievement_fewer_opens_50
            AchievementId.SCORE_90 -> R.string.achievement_score_90
            AchievementId.PERFECT_100 -> R.string.achievement_perfect_100
            AchievementId.NIGHT_QUIET_3 -> R.string.achievement_night_quiet_3
            AchievementId.SCHEDULE_KEPT_5 -> R.string.achievement_schedule_kept_5
            AchievementId.LATE_START_5 -> R.string.achievement_late_start_5
            AchievementId.RECOVERY -> R.string.achievement_recovery
        }

    @StringRes
    fun achievementDescription(id: AchievementId): Int =
        when (id) {
            AchievementId.FIRST_LIMIT -> R.string.achievement_desc_first_limit
            AchievementId.FIRST_CLEAN_DAY -> R.string.achievement_desc_first_clean_day
            AchievementId.STREAK_3 -> R.string.achievement_desc_streak_3
            AchievementId.STREAK_7 -> R.string.achievement_desc_streak_7
            AchievementId.STREAK_30 -> R.string.achievement_desc_streak_30
            AchievementId.FEWER_OPENS_50 -> R.string.achievement_desc_fewer_opens_50
            AchievementId.SCORE_90 -> R.string.achievement_desc_score_90
            AchievementId.PERFECT_100 -> R.string.achievement_desc_perfect_100
            AchievementId.NIGHT_QUIET_3 -> R.string.achievement_desc_night_quiet_3
            AchievementId.SCHEDULE_KEPT_5 -> R.string.achievement_desc_schedule_kept_5
            AchievementId.LATE_START_5 -> R.string.achievement_desc_late_start_5
            AchievementId.RECOVERY -> R.string.achievement_desc_recovery
        }

    /** Personality keys used in manifests map to localised labels; unknown keys show as-is. */
    @StringRes
    fun personality(key: String): Int? =
        when (key) {
            "cynical" -> R.string.personality_cynical
            "dramatic" -> R.string.personality_dramatic
            "confused" -> R.string.personality_confused
            "judgmental" -> R.string.personality_judgmental
            "bureaucratic" -> R.string.personality_bureaucratic
            "indifferent" -> R.string.personality_indifferent
            else -> null
        }
}
