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
    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setMascot(id: String) = edit { it[SettingsKeys.MASCOT] = id }

    suspend fun setStrictMode(enabled: Boolean) = edit { it[SettingsKeys.STRICT] = enabled }

    suspend fun setDailySummary(enabled: Boolean, hour: Int) =
        edit {
            it[SettingsKeys.SUMMARY_ENABLED] = enabled
            it[SettingsKeys.SUMMARY_HOUR] = hour
        }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[SettingsKeys.DYNAMIC_COLOR] = enabled }

    suspend fun setLanguage(language: AppLanguage) = edit { it[SettingsKeys.LANGUAGE] = language.tag }

    suspend fun setOnboardingDone(done: Boolean) = edit { it[SettingsKeys.ONBOARDING_DONE] = done }

    suspend fun setLastRolloverDate(date: LocalDate) = edit { it[SettingsKeys.LAST_ROLLOVER] = date.toString() }

    suspend fun setStreak(current: Int, best: Int) =
        edit {
            it[SettingsKeys.STREAK] = current
            it[SettingsKeys.BEST_STREAK] = best
        }

    suspend fun setPremium(premium: Boolean) = edit { it[SettingsKeys.PREMIUM] = premium }

    suspend fun setReduceMotion(mode: ReduceMotionMode) = edit { it[SettingsKeys.REDUCE_MOTION] = mode.name }

    suspend fun setTrackingEnabled(enabled: Boolean) = edit { it[SettingsKeys.TRACKING] = enabled }

    suspend fun setFreeTierNoticeShown(shown: Boolean) = edit { it[SettingsKeys.FREE_NOTICE] = shown }

    /** Sounds and the pet's voice; a null leaves that one as it is. */
    suspend fun setAudio(soundsEnabled: Boolean? = null, voiceEnabled: Boolean? = null) =
        edit { prefs ->
            soundsEnabled?.let { prefs[SettingsKeys.SOUNDS] = it }
            voiceEnabled?.let { prefs[SettingsKeys.VOICE] = it }
        }

    suspend fun setPetMessagesEnabled(enabled: Boolean) = edit { it[SettingsKeys.PET_MESSAGES] = enabled }

    suspend fun setPetCallsEnabled(enabled: Boolean) = edit { it[SettingsKeys.PET_CALLS] = enabled }

    suspend fun setBreathingGateEnabled(enabled: Boolean) = edit { it[SettingsKeys.BREATHING_GATE] = enabled }

    suspend fun setReminderMinutes(minutes: Int) = edit { it[SettingsKeys.REMINDER_MINUTES] = minutes }

    suspend fun restore(settings: Settings) =
        edit {
            it[SettingsKeys.MASCOT] = settings.selectedMascotId
            it[SettingsKeys.STRICT] = settings.strictMode
            it[SettingsKeys.SUMMARY_ENABLED] = settings.dailySummaryEnabled
            it[SettingsKeys.SUMMARY_HOUR] = settings.dailySummaryHour
            it[SettingsKeys.DYNAMIC_COLOR] = settings.dynamicColor
            it[SettingsKeys.LANGUAGE] = settings.language.tag
            it[SettingsKeys.ONBOARDING_DONE] = settings.onboardingDone
            settings.lastRolloverDate?.let { date -> it[SettingsKeys.LAST_ROLLOVER] = date.toString() }
            it[SettingsKeys.STREAK] = settings.currentStreak
            it[SettingsKeys.BEST_STREAK] = settings.bestStreak
            it[SettingsKeys.REDUCE_MOTION] = settings.reduceMotion.name
            it[SettingsKeys.TRACKING] = settings.trackingEnabled
            it[SettingsKeys.SOUNDS] = settings.soundsEnabled
            it[SettingsKeys.VOICE] = settings.voiceEnabled
            it[SettingsKeys.PET_MESSAGES] = settings.petMessagesEnabled
            it[SettingsKeys.PET_CALLS] = settings.petCallsEnabled
            it[SettingsKeys.BREATHING_GATE] = settings.breathingGateEnabled
            it[SettingsKeys.REMINDER_MINUTES] = settings.reminderMinutes
        }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings.DEFAULT
        return Settings(
            selectedMascotId = or(SettingsKeys.MASCOT, defaults.selectedMascotId),
            strictMode = or(SettingsKeys.STRICT, defaults.strictMode),
            dailySummaryEnabled = or(SettingsKeys.SUMMARY_ENABLED, defaults.dailySummaryEnabled),
            dailySummaryHour = or(SettingsKeys.SUMMARY_HOUR, defaults.dailySummaryHour),
            dynamicColor = or(SettingsKeys.DYNAMIC_COLOR, defaults.dynamicColor),
            language = AppLanguage.fromTag(this[SettingsKeys.LANGUAGE]),
            onboardingDone = or(SettingsKeys.ONBOARDING_DONE, defaults.onboardingDone),
            lastRolloverDate =
                this[SettingsKeys.LAST_ROLLOVER]?.let {
                    runCatching {
                        LocalDate.parse(
                            it,
                        )
                    }.getOrNull()
                },
            currentStreak = or(SettingsKeys.STREAK, defaults.currentStreak),
            bestStreak = or(SettingsKeys.BEST_STREAK, defaults.bestStreak),
            premium = or(SettingsKeys.PREMIUM, defaults.premium),
            reduceMotion = reduceMotion(defaults.reduceMotion),
            trackingEnabled = or(SettingsKeys.TRACKING, defaults.trackingEnabled),
            freeTierNoticeShown = or(SettingsKeys.FREE_NOTICE, defaults.freeTierNoticeShown),
            soundsEnabled = or(SettingsKeys.SOUNDS, defaults.soundsEnabled),
            voiceEnabled = or(SettingsKeys.VOICE, defaults.voiceEnabled),
            petMessagesEnabled = or(SettingsKeys.PET_MESSAGES, defaults.petMessagesEnabled),
            petCallsEnabled = or(SettingsKeys.PET_CALLS, defaults.petCallsEnabled),
            breathingGateEnabled = or(SettingsKeys.BREATHING_GATE, defaults.breathingGateEnabled),
            reminderMinutes = or(SettingsKeys.REMINDER_MINUTES, defaults.reminderMinutes),
            pinHash = this[SettingsKeys.PIN_HASH],
        )
    }

    private fun <T> Preferences.or(key: Preferences.Key<T>, default: T): T = this[key] ?: default

    private fun Preferences.reduceMotion(default: ReduceMotionMode): ReduceMotionMode =
        this[SettingsKeys.REDUCE_MOTION]?.let { mode -> runCatching { ReduceMotionMode.valueOf(mode) }.getOrNull() }
            ?: default
}

/** The preference keys, shared with [SettingsLockRepository]. */
internal object SettingsKeys {
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
    val VOICE = booleanPreferencesKey("voice_enabled")
    val PET_MESSAGES = booleanPreferencesKey("pet_messages_enabled")
    val PET_CALLS = booleanPreferencesKey("pet_calls_enabled")
    val BREATHING_GATE = booleanPreferencesKey("breathing_gate_enabled")
    val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    val PIN_HASH = stringPreferencesKey("pin_hash")

    /** The AI brain's API key; read and written only by [AiSettingsRepository]. */
    val AI_KEY = stringPreferencesKey("ai_key")
}
