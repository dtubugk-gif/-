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
 * Websites the user asked to block, as bare domains ("instagram.com"). A domain blocks itself and every
 * subdomain. Enforced only by the accessibility service, which reads the address bar of known browsers.
 */
@Singleton
class BlockedSitesRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val sites: Flow<Set<String>> = store.data.map { it[KEY].orEmpty() }

    suspend fun current(): Set<String> = sites.first()

    /** Adds [input] as a domain; returns the normalised domain, or null when the input is not one. */
    suspend fun add(input: String): String? {
        val domain = normalise(input) ?: return null
        store.edit { it[KEY] = it[KEY].orEmpty() + domain }
        return domain
    }

    suspend fun remove(domain: String) {
        store.edit { it[KEY] = it[KEY].orEmpty() - domain }
    }

    companion object {
        private val KEY = stringSetPreferencesKey("blocked_sites")
        private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

        /** "https://www.Instagram.com/reels/" → "instagram.com"; garbage → null. */
        fun normalise(input: String): String? {
            var host = input.trim().lowercase()
            host = host.substringAfter("://", host)
            host =
                host
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringBefore(':')
            host = host.removePrefix("www.")
            return host.takeIf { DOMAIN.matches(it) }
        }
    }
}
