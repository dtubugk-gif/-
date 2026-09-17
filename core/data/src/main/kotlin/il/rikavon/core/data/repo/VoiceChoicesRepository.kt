package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Which cloud voice the user picked for each pet; null means the one cast for its personality. */
@Singleton
class VoiceChoicesRepository @Inject constructor(private val store: DataStore<Preferences>) {
    fun choice(mascotId: String): Flow<String?> =
        store.data.map { prefs -> prefs[key(mascotId)]?.takeIf { it.isNotBlank() } }.distinctUntilChanged()

    suspend fun current(mascotId: String): String? = choice(mascotId).first()

    suspend fun set(mascotId: String, voiceId: String?) {
        store.edit { prefs ->
            if (voiceId.isNullOrBlank()) prefs.remove(key(mascotId)) else prefs[key(mascotId)] = voiceId
        }
    }

    private fun key(mascotId: String): Preferences.Key<String> = stringPreferencesKey("voice_choice_$mascotId")
}
