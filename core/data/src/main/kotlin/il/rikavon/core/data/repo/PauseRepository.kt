package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Why a package is paused: the user hit "block now", or a session ran long and this is the break. */
enum class PauseReason { MANUAL, BREAK }

/** A package blocked until a point in time, whatever its daily limit says. */
data class Pause(val packageName: String, val untilMillis: Long, val reason: PauseReason)

/**
 * Temporary blocks: "block now" for a while, and the forced break after a long session. Kept in
 * preferences as `package|until|reason` entries; expired entries are dropped on the next read.
 */
@Singleton
class PauseRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val pauses: Flow<List<Pause>> = store.data.map { prefs -> prefs[KEY].orEmpty().mapNotNull(::decode) }

    /** The pauses still in force at [nowMillis], keyed by package. */
    suspend fun current(nowMillis: Long): Map<String, Pause> =
        pauses.first().filter { it.untilMillis > nowMillis }.associateBy { it.packageName }

    suspend fun pause(packageName: String, untilMillis: Long, reason: PauseReason) {
        store.edit { prefs ->
            val kept = prefs[KEY].orEmpty().mapNotNull(::decode).filter { it.packageName != packageName }
            prefs[KEY] = (kept + Pause(packageName, untilMillis, reason)).map(::encode).toSet()
        }
    }

    suspend fun lift(packageName: String) {
        store.edit { prefs ->
            prefs[KEY] =
                prefs[KEY]
                    .orEmpty()
                    .mapNotNull(::decode)
                    .filter { it.packageName != packageName }
                    .map(::encode)
                    .toSet()
        }
    }

    /** Drops what has expired, so the set does not grow forever. */
    suspend fun prune(nowMillis: Long) {
        store.edit { prefs ->
            prefs[KEY] =
                prefs[KEY]
                    .orEmpty()
                    .mapNotNull(::decode)
                    .filter { it.untilMillis > nowMillis }
                    .map(::encode)
                    .toSet()
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("pauses")
        const val SEPARATOR = '|'
        const val PARTS = 3

        fun encode(pause: Pause): String =
            listOf(
                pause.packageName,
                pause.untilMillis.toString(),
                pause.reason.name,
            ).joinToString(SEPARATOR.toString())

        fun decode(raw: String): Pause? {
            val parts = raw.split(SEPARATOR)
            if (parts.size != PARTS) return null
            val until = parts[1].toLongOrNull() ?: return null
            val reason = runCatching { PauseReason.valueOf(parts[2]) }.getOrNull() ?: return null
            return Pause(parts[0], until, reason)
        }
    }
}
