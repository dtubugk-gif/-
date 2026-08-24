package com.routines.appclose.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RulesDao {

    @Transaction
    @Query("SELECT * FROM rules ORDER BY id DESC")
    fun observeRules(): Flow<List<RuleWithActions>>

    @Transaction
    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRule(id: Long): RuleWithActions?

    @Transaction
    @Query("SELECT * FROM rules WHERE watchedPackage = :pkg AND enabled = 1")
    suspend fun getEnabledRulesForPackage(pkg: String): List<RuleWithActions>

    @Insert
    suspend fun insertRule(rule: Rule): Long

    @Update
    suspend fun updateRule(rule: Rule)

    @Delete
    suspend fun deleteRule(rule: Rule)

    @Insert
    suspend fun insertActions(actions: List<RuleAction>)

    @Query("DELETE FROM actions WHERE ruleId = :ruleId")
    suspend fun deleteActionsForRule(ruleId: Long)

    @Transaction
    suspend fun saveRule(rule: Rule, actions: List<RuleAction>): Long {
        val ruleId = if (rule.id == 0L) {
            insertRule(rule)
        } else {
            updateRule(rule)
            deleteActionsForRule(rule.id)
            rule.id
        }
        insertActions(actions.map { it.copy(id = 0, ruleId = ruleId) })
        return ruleId
    }

    @Insert
    suspend fun insertLog(log: TriggerLog)

    @Query("SELECT * FROM trigger_log ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentLogs(): Flow<List<TriggerLog>>

    @Query("DELETE FROM trigger_log WHERE id NOT IN (SELECT id FROM trigger_log ORDER BY timestamp DESC LIMIT 100)")
    suspend fun trimLogs()
}
