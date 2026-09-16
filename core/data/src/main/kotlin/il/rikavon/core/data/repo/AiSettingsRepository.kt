package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The optional AI brain's one secret: the user's own API key. Kept out of [il.rikavon.core.data.model.Settings],
 * so it never reaches a backup file or a log line; null means the pet answers from its script.
 */
@Singleton
class AiSettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val key: Flow<String?> = store.data.map { prefs -> prefs[SettingsKeys.AI_KEY]?.takeIf { it.isNotBlank() } }

    suspend fun current(): String? = key.first()

    /** Stores a trimmed key; blank or null removes it. */
    suspend fun setKey(key: String?) {
        val clean = key?.trim().orEmpty()
        store.edit { prefs ->
            if (clean.isEmpty()) prefs.remove(SettingsKeys.AI_KEY) else prefs[SettingsKeys.AI_KEY] = clean
        }
    }
}
