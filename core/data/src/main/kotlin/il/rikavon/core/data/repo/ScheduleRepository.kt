package il.rikavon.core.data.repo

import il.rikavon.core.data.db.ScheduleDao
import il.rikavon.core.data.db.toEntity
import il.rikavon.core.data.db.toModel
import il.rikavon.core.data.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(private val dao: ScheduleDao) {
    val schedules: Flow<List<Schedule>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<Schedule> = dao.all().map { it.toModel() }

    suspend fun byId(id: Long): Schedule? = dao.byId(id)?.toModel()

    suspend fun save(schedule: Schedule): Long = dao.insert(schedule.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun replaceAll(schedules: List<Schedule>) {
        dao.clear()
        dao.upsertAll(schedules.map { it.toEntity() })
    }

    suspend fun clear() = dao.clear()
}
