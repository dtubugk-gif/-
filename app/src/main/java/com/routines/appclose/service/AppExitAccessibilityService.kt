package com.routines.appclose.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var detector: ExitDetector
    private lateinit var executor: ActionExecutor
    private lateinit var appLabelLookup: (String) -> String

    // כל הגישה ל-detector נשארת על ה-main thread (גם האירועים וגם האישור המושהה).
    private val confirmRunnable = Runnable {
        val exitedPackage = detector.confirmPending(System.currentTimeMillis()) ?: return@Runnable
        onExit(exitedPackage)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        detector = ExitDetector(buildIgnoredPackages(), MIN_FOREGROUND_MS, CONFIRM_MS)
        executor = ActionExecutor(this)
        appLabelLookup = { pkg -> labelFor(pkg) }
        ServiceStatus.connected.value = true
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::detector.isInitialized) return
        val pkg = event.packageName?.toString() ?: return

        val result = detector.onWindowChanged(pkg, System.currentTimeMillis())
        result.confirmedExit?.let { onExit(it) }
        if (result.pendingCreated) {
            // יציאה ממתינה — מאשרים רק אחרי שהחלון החדש החזיק מעמד,
            // כדי שחלונות זמניים (share sheet, דיאלוגים) לא יפעילו שגרות בטעות.
            handler.removeCallbacks(confirmRunnable)
            handler.postDelayed(confirmRunnable, CONFIRM_MS + CONFIRM_SLACK_MS)
        }
    }

    /** יציאה אושרה — מריצים כללים ומדווחים למסך האבחון. */
    private fun onExit(exitedPackage: String) {
        scope.launch {
            var ruleRan = false
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
                    ruleRan = true
                }
                if (rules.isNotEmpty()) dao.trimLogs()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed running rules for $exitedPackage", t)
            } finally {
                ServiceStatus.addDetection(
                    ServiceStatus.Detection(
                        packageName = exitedPackage,
                        label = if (::appLabelLookup.isInitialized) appLabelLookup(exitedPackage) else exitedPackage,
                        ruleRan = ruleRan,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private fun labelFor(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    /**
     * חלונות שאינם "אפליקציה" מבחינת המשתמש: SystemUI, מקלדות, האפליקציה שלנו,
     * וחלונות מערכת זמניים (share sheet, דיאלוגי הרשאות, ומעטפת One UI של סמסונג).
     */
    private fun buildIgnoredPackages(): Set<String> {
        val ignored = mutableSetOf(
            packageName,
            "android",
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            // מעטפת One UI של סמסונג — חלונות מערכת שאינם "אפליקציה".
            "com.samsung.android.mtpapplication",
            "com.samsung.android.honeyboard",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.rubin.app",
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

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ServiceStatus.connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceStatus.connected.value = false
        handler.removeCallbacks(confirmRunnable)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppExitService"
        private const val MIN_FOREGROUND_MS = 1500L
        private const val CONFIRM_MS = 1500L
        private const val CONFIRM_SLACK_MS = 100L
    }
}
