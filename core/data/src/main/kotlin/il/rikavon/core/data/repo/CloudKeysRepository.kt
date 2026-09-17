package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The two optional cloud features, each unlocked by a key the user owns. */
enum class CloudKey(internal val pref: Preferences.Key<String>) {
    /** The AI brain: Claude writes the pet's lines. */
    BRAIN(SettingsKeys.AI_KEY),

    /** The realistic voice: Azure Speech or OpenAI says them. */
    VOICE(SettingsKeys.SPEECH_KEY),
}

/**
 * The user's own API keys for the optional cloud features. Kept out of [il.rikavon.core.data.model.Settings],
 * so they never reach a backup file or a log line; a missing key means the feature is off.
 */
@Singleton
class CloudKeysRepository @Inject constructor(private val store: DataStore<Preferences>) {
    fun key(kind: CloudKey): Flow<String?> =
        store.data.map { prefs -> prefs[kind.pref]?.takeIf { it.isNotBlank() } }.distinctUntilChanged()

    suspend fun current(kind: CloudKey): String? = key(kind).first()

    /** The Azure region of the realistic voice's resource ("westeurope"); meaningless for an OpenAI key. */
    val voiceRegion: Flow<String?> =
        store.data.map { prefs -> prefs[SettingsKeys.SPEECH_REGION]?.takeIf { it.isNotBlank() } }.distinctUntilChanged()

    suspend fun currentVoiceRegion(): String? = voiceRegion.first()

    suspend fun setVoiceRegion(value: String?) {
        val clean = value?.trim().orEmpty()
        store.edit { prefs ->
            if (clean.isEmpty()) prefs.remove(SettingsKeys.SPEECH_REGION) else prefs[SettingsKeys.SPEECH_REGION] = clean
        }
    }

    /** Stores a trimmed key; blank or null removes it. */
    suspend fun set(kind: CloudKey, value: String?) {
        val clean = value?.trim().orEmpty()
        store.edit { prefs -> if (clean.isEmpty()) prefs.remove(kind.pref) else prefs[kind.pref] = clean }
    }
}
