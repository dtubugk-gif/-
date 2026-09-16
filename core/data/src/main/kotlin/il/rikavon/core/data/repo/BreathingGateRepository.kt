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

/**
 * Packages whose next open must wait behind the breathing pause because their limit was just changed.
 * A package stays pending until the user sits through the pause once; leaving early keeps it pending.
 */
@Singleton
class BreathingGateRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val pending: Flow<Set<String>> = store.data.map { it[KEY].orEmpty() }

    suspend fun current(): Set<String> = pending.first()

    suspend fun mark(packageName: String) {
        store.edit { it[KEY] = it[KEY].orEmpty() + packageName }
    }

    suspend fun clear(packageName: String) {
        store.edit { it[KEY] = it[KEY].orEmpty() - packageName }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("breathing_gate_pending")
    }
}
