package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import il.rikavon.core.data.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Intraday facts that are not derivable from usage stats alone (score extremes, schedule outcomes). */
data class DayState(
    val date: LocalDate,
    val minScore: Int,
    val lastScore: Int,
    val recoveredFromLow: Boolean,
    val schedulesKept: Int,
    val schedulesBroken: Int,
) {
    companion object {
        const val LOW_THRESHOLD = 20
        const val RECOVERED_THRESHOLD = 80

        fun fresh(date: LocalDate, initialScore: Int) =
            DayState(
                date = date,
                minScore = initialScore,
                lastScore = initialScore,
                recoveredFromLow = false,
                schedulesKept = 0,
                schedulesBroken = 0,
            )
    }
}

@Singleton
class DayStateRepository @Inject constructor(private val store: DataStore<Preferences>, private val time: TimeSource) {
    private object Keys {
        val DATE = stringPreferencesKey("day_state_date")
        val MIN_SCORE = intPreferencesKey("day_state_min_score")
        val LAST_SCORE = intPreferencesKey("day_state_last_score")
        val RECOVERED = booleanPreferencesKey("day_state_recovered")
        val KEPT = intPreferencesKey("day_state_kept")
        val BROKEN = intPreferencesKey("day_state_broken")
    }

    val state: Flow<DayState?> = store.data.map { it.toState() }

    suspend fun current(): DayState? = state.first()

    /** Records a new score observation for today, tracking the minimum and a recovery. */
    suspend fun observeScore(score: Int) {
        val today = time.today()
        store.edit { prefs ->
            val existing = prefs.toState()?.takeIf { it.date == today } ?: DayState.fresh(today, score)
            val min = minOf(existing.minScore, score)
            val recovered =
                existing.recoveredFromLow ||
                    (existing.minScore <= DayState.LOW_THRESHOLD && score >= DayState.RECOVERED_THRESHOLD)
            prefs.write(existing.copy(minScore = min, lastScore = score, recoveredFromLow = recovered))
        }
    }

    suspend fun recordScheduleOutcome(kept: Boolean) {
        val today = time.today()
        store.edit { prefs ->
            val existing =
                prefs.toState()?.takeIf { it.date == today } ?: DayState.fresh(today, existingScore(prefs))
            prefs.write(
                if (kept) {
                    existing.copy(schedulesKept = existing.schedulesKept + 1)
                } else {
                    existing.copy(schedulesBroken = existing.schedulesBroken + 1)
                },
            )
        }
    }

    suspend fun stateFor(date: LocalDate): DayState? = current()?.takeIf { it.date == date }

    private fun existingScore(prefs: Preferences): Int = prefs[Keys.LAST_SCORE] ?: 0

    private fun androidx.datastore.preferences.core.MutablePreferences.write(state: DayState) {
        this[Keys.DATE] = state.date.toString()
        this[Keys.MIN_SCORE] = state.minScore
        this[Keys.LAST_SCORE] = state.lastScore
        this[Keys.RECOVERED] = state.recoveredFromLow
        this[Keys.KEPT] = state.schedulesKept
        this[Keys.BROKEN] = state.schedulesBroken
    }

    private fun Preferences.toState(): DayState? {
        val date = this[Keys.DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return DayState(
            date = date,
            minScore = this[Keys.MIN_SCORE] ?: 0,
            lastScore = this[Keys.LAST_SCORE] ?: 0,
            recoveredFromLow = this[Keys.RECOVERED] ?: false,
            schedulesKept = this[Keys.KEPT] ?: 0,
            schedulesBroken = this[Keys.BROKEN] ?: 0,
        )
    }
}
