package com.routines.appclose.engine

/**
 * לוגיקה טהורה לזיהוי "יציאה מאפליקציה" מתוך רצף אירועי החלפת חלון.
 *
 * אירוע נחשב ליציאה כשהחבילה שבחזית מתחלפת בחבילה אחרת, בתנאי
 * שהאפליקציה הקודמת הייתה בחזית לפחות [minForegroundMs] (כדי לא לירות
 * על מעברים חטופים כמו פתיחת התראה ורגע חזרה).
 *
 * חבילות ב-[ignoredPackages] (SystemUI, מקלדות, האפליקציה שלנו) לא נחשבות
 * לא כ"אפליקציה שנסגרה" ולא כ"אפליקציה חדשה בחזית".
 */
class ExitDetector(
    private val ignoredPackages: Set<String>,
    private val minForegroundMs: Long = 1500L,
) {
    private var currentPackage: String? = null
    private var foregroundSince: Long = 0L

    /**
     * מדווח על אירוע חלון חדש. מחזיר את שם החבילה שממנה יצאו,
     * או null אם האירוע אינו יציאה מאפליקציה.
     */
    fun onWindowChanged(packageName: String?, now: Long): String? {
        if (packageName.isNullOrBlank()) return null
        if (packageName in ignoredPackages) return null
        if (packageName == currentPackage) return null

        val previous = currentPackage
        val wasLongEnough = previous != null && (now - foregroundSince) >= minForegroundMs

        currentPackage = packageName
        foregroundSince = now

        return if (wasLongEnough) previous else null
    }

    fun reset() {
        currentPackage = null
        foregroundSince = 0L
    }
}
