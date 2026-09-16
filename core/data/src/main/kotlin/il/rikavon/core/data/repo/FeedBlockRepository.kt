package il.rikavon.core.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import il.rikavon.core.data.model.FeedFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A feed the user switched off, and until when ([FeedBlock.FOREVER] means until they switch it back on). */
data class FeedBlock(val feature: FeedFeature, val untilMillis: Long) {
    val forever: Boolean get() = untilMillis == FOREVER

    companion object {
        const val FOREVER = Long.MAX_VALUE
    }
}

/**
 * Which in-app feeds (Shorts, Reels) are blocked, each for as long as the user chose. Opt-in, per feed;
 * enforced only by the accessibility service. Kept as `FEATURE|until` entries.
 */
@Singleton
class FeedBlockRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val blocks: Flow<Map<FeedFeature, FeedBlock>> =
        store.data.map { prefs -> prefs[KEY].orEmpty().mapNotNull(::decode).associateBy { it.feature } }

    /** The feeds blocked at [nowMillis]. */
    suspend fun current(nowMillis: Long): Set<FeedFeature> =
        blocks
            .first()
            .values
            .filter { it.untilMillis > nowMillis }
            .map { it.feature }
            .toSet()

    suspend fun block(feature: FeedFeature, untilMillis: Long) {
        store.edit { prefs ->
            val kept = prefs[KEY].orEmpty().mapNotNull(::decode).filter { it.feature != feature }
            prefs[KEY] = (kept + FeedBlock(feature, untilMillis)).map(::encode).toSet()
        }
    }

    suspend fun unblock(feature: FeedFeature) {
        store.edit { prefs ->
            prefs[KEY] =
                prefs[KEY]
                    .orEmpty()
                    .mapNotNull(::decode)
                    .filter { it.feature != feature }
                    .map(::encode)
                    .toSet()
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("blocked_feeds")
        const val SEPARATOR = '|'

        fun encode(block: FeedBlock): String = "${block.feature.name}$SEPARATOR${block.untilMillis}"

        fun decode(raw: String): FeedBlock? {
            val parts = raw.split(SEPARATOR)
            if (parts.size != 2) return null
            val feature = runCatching { FeedFeature.valueOf(parts[0]) }.getOrNull() ?: return null
            val until = parts[1].toLongOrNull() ?: return null
            return FeedBlock(feature, until)
        }
    }
}
