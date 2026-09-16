package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton

/** The settings lock's one piece of state: the PIN's salted hash. The PIN itself is never stored. */
@Singleton
class SettingsLockRepository @Inject constructor(private val store: DataStore<Preferences>) {
    /** Locks (a hash) or unlocks (null). */
    suspend fun setPinHash(hash: String?) {
        store.edit { prefs ->
            if (hash == null) {
                prefs.remove(SettingsKeys.PIN_HASH)
            } else {
                prefs[SettingsKeys.PIN_HASH] = hash
            }
        }
    }
}
