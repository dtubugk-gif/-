package il.rikavon.core.data.repo

import il.rikavon.core.data.db.AppLimitDao
import il.rikavon.core.data.db.toEntity
import il.rikavon.core.data.db.toModel
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LimitsRepository @Inject constructor(private val dao: AppLimitDao, private val time: TimeSource) {
    val limits: Flow<List<AppLimit>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<AppLimit> = dao.all().map { it.toModel() }

    suspend fun byPackage(packageName: String): AppLimit? = dao.byPackage(packageName)?.toModel()

    suspend fun save(limit: AppLimit) {
        val existing = dao.byPackage(limit.packageName)
        val createdAt = existing?.createdAt ?: time.nowMillis()
        dao.upsert(limit.copy(createdAt = createdAt).toEntity())
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        val existing = dao.byPackage(packageName) ?: return
        dao.upsert(existing.copy(enabled = enabled))
    }

    suspend fun remove(packageName: String) = dao.deleteByPackage(packageName)

    suspend fun replaceAll(limits: List<AppLimit>) {
        dao.clear()
        dao.upsertAll(limits.map { it.toEntity() })
    }

    suspend fun clear() = dao.clear()
}
