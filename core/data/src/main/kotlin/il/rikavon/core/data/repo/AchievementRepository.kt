package il.rikavon.core.data.repo

import il.rikavon.core.data.db.AchievementDao
import il.rikavon.core.data.db.AchievementEntity
import il.rikavon.core.data.db.toModel
import il.rikavon.core.data.model.Achievement
import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementRepository @Inject constructor(private val dao: AchievementDao, private val time: TimeSource) {
    /** Every achievement, unlocked or not, in canonical order. */
    val achievements: Flow<List<Achievement>> =
        dao.observeAll().map { rows ->
            val unlocked = rows.mapNotNull { it.toModel() }.associateBy { it.id }
            AchievementId.entries.map { unlocked[it] ?: Achievement(it, unlockedAt = null) }
        }

    val unlockedIds: Flow<Set<AchievementId>> =
        achievements.map { list ->
            list.filter { it.unlocked }.map { it.id }.toSet()
        }

    suspend fun unlockedNow(): Set<AchievementId> = dao.all().mapNotNull { it.toModel()?.id }.toSet()

    /** Persists newly earned ids; returns the ones that were not unlocked before. */
    suspend fun unlock(ids: Set<AchievementId>): Set<AchievementId> {
        val already = unlockedNow()
        val fresh = ids - already
        if (fresh.isNotEmpty()) {
            dao.insertAll(fresh.map { AchievementEntity(it.key, time.nowMillis()) })
        }
        return fresh
    }

    suspend fun exportAll(): List<Achievement> = dao.all().mapNotNull { it.toModel() }

    suspend fun importAll(list: List<Achievement>) {
        dao.insertAll(list.mapNotNull { a -> a.unlockedAt?.let { AchievementEntity(a.id.key, it) } })
    }

    suspend fun clear() = dao.clear()
}
