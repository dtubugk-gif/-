package com.routines.appclose.engine

/**
 * לוגיקה טהורה לזיהוי "יציאה מאפליקציה" מתוך רצף אירועי החלפת חלון.
 *
 * יציאה מדווחת בשני שלבים כדי לא לירות בטעות על חלונות זמניים
 * (share sheet, דיאלוג הרשאות, הצצה קצרה לאפליקציה אחרת):
 *
 * 1. כשהחבילה בחזית מתחלפת, ואם האפליקציה הקודמת הייתה בחזית לפחות
 *    [minForegroundMs] — נרשמת יציאה ממתינה ([WindowResult.pendingCreated]),
 *    והקורא מתזמן קריאה ל-[confirmPending] אחרי [confirmMs].
 * 2. היציאה מאושרת רק אם עברו [confirmMs] מאז ההחלפה והאפליקציה שממנה
 *    יצאו לא חזרה בינתיים לחזית — או דרך [confirmPending] (הטיימר), או
 *    ישירות מתוך [onWindowChanged] כשמעבר חדש מגיע אחרי שהיציאה כבר בשלה
 *    ([WindowResult.confirmedExit]), כדי שרצף מעברים לא ימחק יציאה אמיתית.
 *
 * חבילות ב-[ignoredPackages] (SystemUI, מקלדות, האפליקציה שלנו) שקופות:
 * לא נחשבות לא כ"אפליקציה שנסגרה" ולא כ"אפליקציה חדשה בחזית".
 */
class ExitDetector(
    private val ignoredPackages: Set<String>,
    private val minForegroundMs: Long = 1500L,
    private val confirmMs: Long = 1500L,
) {
    /**
     * תוצאת אירוע חלון: [confirmedExit] — יציאה שאושרה עכשיו (אם יש),
     * [pendingCreated] — האם נרשמה יציאה ממתינה חדשה שדורשת תזמון אישור.
     */
    data class WindowResult(val confirmedExit: String?, val pendingCreated: Boolean) {
        companion object {
            val NONE = WindowResult(null, false)
        }
    }

    private data class Pending(val exitedPackage: String, val since: Long)

    private var currentPackage: String? = null
    private var foregroundSince: Long = 0L
    private var pending: Pending? = null

    fun onWindowChanged(packageName: String?, now: Long): WindowResult {
        if (packageName.isNullOrBlank()) return WindowResult.NONE
        if (packageName in ignoredPackages) return WindowResult.NONE
        if (packageName == currentPackage) return WindowResult.NONE

        val previous = currentPackage
        val stayedLongEnough = previous != null && (now - foregroundSince) >= minForegroundMs

        currentPackage = packageName
        foregroundSince = now

        var confirmed: String? = null
        pending?.let { p ->
            if (p.exitedPackage == packageName) {
                // האפליקציה חזרה לפני שהיציאה אושרה — חלון זמני, לא יציאה.
                pending = null
            } else if (now - p.since >= confirmMs) {
                // היציאה הקודמת כבר בשלה — מאשרים אותה כאן כדי שלא תימחק.
                confirmed = p.exitedPackage
                pending = null
            }
        }

        if (stayedLongEnough) {
            pending = Pending(previous!!, now)
        }
        return WindowResult(confirmed, stayedLongEnough)
    }

    /**
     * מנסה לאשר יציאה ממתינה (נקרא מהטיימר). מחזירה את חבילת האפליקציה
     * שממנה יצאו, או null אם אין יציאה בשלה לאישור.
     */
    fun confirmPending(now: Long): String? {
        val p = pending ?: return null
        if (now - p.since < confirmMs) return null
        pending = null
        return if (currentPackage == p.exitedPackage) null else p.exitedPackage
    }

    fun reset() {
        currentPackage = null
        foregroundSince = 0L
        pending = null
    }
}
