package com.routines.appclose.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.routines.appclose.data.AppDatabase
import com.routines.appclose.data.TriggerLog
import com.routines.appclose.engine.ActionExecutor
import com.routines.appclose.engine.ExitDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppExitAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var detector: ExitDetector
    private lateinit var executor: ActionExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        detector = ExitDetector(buildIgnoredPackages())
        executor = ActionExecutor(this)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::detector.isInitialized) return
        val pkg = event.packageName?.toString() ?: return

        val exitedPackage = detector.onWindowChanged(pkg, System.currentTimeMillis()) ?: return

        scope.launch {
            runRulesFor(exitedPackage)
        }
    }

    private suspend fun runRulesFor(exitedPackage: String) {
        try {
            val dao = AppDatabase.get(applicationContext).rulesDao()
            val rules = dao.getEnabledRulesForPackage(exitedPackage)
            for (rule in rules) {
                Log.i(TAG, "Running rule '${rule.rule.name}' for $exitedPackage")
                executor.execute(rule.actions)
                dao.insertLog(
                    TriggerLog(
                        ruleName = rule.rule.name,
                        appLabel = rule.rule.watchedAppLabel,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
            if (rules.isNotEmpty()) dao.trimLogs()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed running rules for $exitedPackage", t)
        }
    }

    /** SystemUI, מקלדות והאפליקציה שלנו — חלונות שאינם "אפליקציה" מבחינת המשתמש. */
    private fun buildIgnoredPackages(): Set<String> {
        val ignored = mutableSetOf(
            packageName,
            "com.android.systemui",
        )
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.inputMethodList.forEach { ignored.add(it.packageName) }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not list input methods", t)
        }
        return ignored
    }

    override fun onInterrupt() {
        // אין מה לבטל — הפעולות קצרות.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppExitService"
    }
}
