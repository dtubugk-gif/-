package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private object Keys {
        val MASCOT = stringPreferencesKey("mascot_id")
        val STRICT = booleanPreferencesKey("strict_mode")
        val SUMMARY_ENABLED = booleanPreferencesKey("summary_enabled")
        val SUMMARY_HOUR = intPreferencesKey("summary_hour")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_ROLLOVER = stringPreferencesKey("last_rollover")
        val STREAK = intPreferencesKey("streak_current")
        val BEST_STREAK = intPreferencesKey("streak_best")
        val PREMIUM = booleanPreferencesKey("premium")
        val REDUCE_MOTION = stringPreferencesKey("reduce_motion")
        val TRACKING = booleanPreferencesKey("tracking_enabled")
        val FREE_NOTICE = booleanPreferencesKey("free_notice_shown")
        val SOUNDS = booleanPreferencesKey("sounds_enabled")
    }

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setMascot(id: String) = edit { it[Keys.MASCOT] = id }

    suspend fun setStrictMode(enabled: Boolean) = edit { it[Keys.STRICT] = enabled }

    suspend fun setDailySummary(enabled: Boolean, hour: Int) =
        edit {
            it[Keys.SUMMARY_ENABLED] = enabled
            it[Keys.SUMMARY_HOUR] = hour
        }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.tag }

    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.ONBOARDING_DONE] = done }

    suspend fun setLastRolloverDate(date: LocalDate) = edit { it[Keys.LAST_ROLLOVER] = date.toString() }

    suspend fun setStreak(current: Int, best: Int) =
        edit {
            it[Keys.STREAK] = current
            it[Keys.BEST_STREAK] = best
        }

    suspend fun setPremium(premium: Boolean) = edit { it[Keys.PREMIUM] = premium }

    suspend fun setReduceMotion(mode: ReduceMotionMode) = edit { it[Keys.REDUCE_MOTION] = mode.name }

    suspend fun setTrackingEnabled(enabled: Boolean) = edit { it[Keys.TRACKING] = enabled }

    suspend fun setFreeTierNoticeShown(shown: Boolean) = edit { it[Keys.FREE_NOTICE] = shown }

    suspend fun setSoundsEnabled(enabled: Boolean) = edit { it[Keys.SOUNDS] = enabled }

    suspend fun restore(settings: Settings) =
        edit {
            it[Keys.MASCOT] = settings.selectedMascotId
            it[Keys.STRICT] = settings.strictMode
            it[Keys.SUMMARY_ENABLED] = settings.dailySummaryEnabled
            it[Keys.SUMMARY_HOUR] = settings.dailySummaryHour
            it[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            it[Keys.LANGUAGE] = settings.language.tag
            it[Keys.ONBOARDING_DONE] = settings.onboardingDone
            settings.lastRolloverDate?.let { date -> it[Keys.LAST_ROLLOVER] = date.toString() }
            it[Keys.STREAK] = settings.currentStreak
            it[Keys.BEST_STREAK] = settings.bestStreak
            it[Keys.REDUCE_MOTION] = settings.reduceMotion.name
            it[Keys.TRACKING] = settings.trackingEnabled
            it[Keys.SOUNDS] = settings.soundsEnabled
        }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings.DEFAULT
        return Settings(
            selectedMascotId = this[Keys.MASCOT] ?: defaults.selectedMascotId,
            strictMode = this[Keys.STRICT] ?: defaults.strictMode,
            dailySummaryEnabled = this[Keys.SUMMARY_ENABLED] ?: defaults.dailySummaryEnabled,
            dailySummaryHour = this[Keys.SUMMARY_HOUR] ?: defaults.dailySummaryHour,
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            language = AppLanguage.fromTag(this[Keys.LANGUAGE]),
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
            lastRolloverDate = this[Keys.LAST_ROLLOVER]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            currentStreak = this[Keys.STREAK] ?: defaults.currentStreak,
            bestStreak = this[Keys.BEST_STREAK] ?: defaults.bestStreak,
            premium = this[Keys.PREMIUM] ?: defaults.premium,
            reduceMotion =
                this[Keys.REDUCE_MOTION]?.let { mode ->
                    runCatching { ReduceMotionMode.valueOf(mode) }.getOrNull()
                } ?: defaults.reduceMotion,
            trackingEnabled = this[Keys.TRACKING] ?: defaults.trackingEnabled,
            freeTierNoticeShown = this[Keys.FREE_NOTICE] ?: defaults.freeTierNoticeShown,
            soundsEnabled = this[Keys.SOUNDS] ?: defaults.soundsEnabled,
        )
    }
}
