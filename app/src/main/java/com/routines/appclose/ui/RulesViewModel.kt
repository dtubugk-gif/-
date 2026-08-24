package com.routines.appclose.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routines.appclose.data.AppDatabase
import com.routines.appclose.data.Rule
import com.routines.appclose.data.RuleAction
import com.routines.appclose.data.RuleWithActions
import com.routines.appclose.data.TriggerLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).rulesDao()

    val rules: StateFlow<List<RuleWithActions>> = dao.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<TriggerLog>> = dao.observeRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getRule(id: Long): RuleWithActions? = dao.getRule(id)

    fun saveRule(rule: Rule, actions: List<RuleAction>) {
        viewModelScope.launch { dao.saveRule(rule, actions) }
    }

    fun setEnabled(rule: Rule, enabled: Boolean) {
        viewModelScope.launch { dao.updateRule(rule.copy(enabled = enabled)) }
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch { dao.deleteRule(rule) }
    }
}
