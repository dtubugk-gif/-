package com.routines.appclose

import com.routines.appclose.engine.ExitDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitDetectorTest {

    private fun detector() = ExitDetector(
        ignoredPackages = setOf("com.android.systemui", "self.pkg"),
        minForegroundMs = 1000L,
        confirmMs = 1000L,
    )

    @Test
    fun `first foreground app does not create a pending exit`() {
        val d = detector()
        val r = d.onWindowChanged("com.youtube", 0L)
        assertFalse(r.pendingCreated)
        assertNull(r.confirmedExit)
    }

    @Test
    fun `leaving an app creates a pending exit that confirms after the delay`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        val r = d.onWindowChanged("com.launcher", 2000L)
        assertTrue(r.pendingCreated)
        assertNull(r.confirmedExit)
        // מוקדם מדי — עדיין לא מאושר.
        assertNull(d.confirmPending(2500L))
        // אחרי זמן האישור — היציאה מדווחת.
        assertEquals("com.youtube", d.confirmPending(3100L))
        // אישור חוזר לא מדווח שוב.
        assertNull(d.confirmPending(4000L))
    }

    @Test
    fun `returning to the app before confirmation cancels the exit`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        // "יציאה" לחלון זמני שאינו ברשימת ההתעלמות (למשל אפליקציה לרגע).
        assertTrue(d.onWindowChanged("com.other", 2000L).pendingCreated)
        // חזרה ליוטיוב לפני האישור — היציאה מבוטלת.
        val back = d.onWindowChanged("com.youtube", 2500L)
        assertNull(back.confirmedExit)
        assertNull(d.confirmPending(4000L))
    }

    @Test
    fun `quick switch below threshold is not an exit`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        val r = d.onWindowChanged("com.launcher", 500L)
        assertFalse(r.pendingCreated)
        assertNull(d.confirmPending(2000L))
    }

    @Test
    fun `ignored packages are neither exit nor new foreground`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        // מעבר ל-SystemUI (למשל צל התראות) שקוף לחלוטין.
        assertEquals(ExitDetector.WindowResult.NONE, d.onWindowChanged("com.android.systemui", 2000L))
        // חזרה מה-SystemUI לאותה אפליקציה — עדיין אין יציאה.
        assertEquals(ExitDetector.WindowResult.NONE, d.onWindowChanged("com.youtube", 2100L))
        assertNull(d.confirmPending(5000L))
    }

    @Test
    fun `same package repeated is not an exit`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        assertEquals(ExitDetector.WindowResult.NONE, d.onWindowChanged("com.youtube", 3000L))
    }

    @Test
    fun `null or blank package is ignored`() {
        val d = detector()
        assertEquals(ExitDetector.WindowResult.NONE, d.onWindowChanged(null, 0L))
        assertEquals(ExitDetector.WindowResult.NONE, d.onWindowChanged("", 100L))
    }

    @Test
    fun `sequential exits are each reported`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        d.onWindowChanged("com.maps", 2000L)
        assertEquals("com.youtube", d.confirmPending(3100L))
        d.onWindowChanged("com.launcher", 5000L)
        assertEquals("com.maps", d.confirmPending(6100L))
    }

    @Test
    fun `a mature pending exit is confirmed inline by the next transition instead of being lost`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        // יציאה מיוטיוב — ממתינה.
        assertTrue(d.onWindowChanged("com.maps", 2000L).pendingCreated)
        // מעבר נוסף אחרי שהיציאה כבר בשלה — היא מאושרת כאן ולא נמחקת.
        val r = d.onWindowChanged("com.launcher", 3500L)
        assertEquals("com.youtube", r.confirmedExit)
        assertTrue(r.pendingCreated) // וגם יציאת maps נרשמה כממתינה.
        assertEquals("com.maps", d.confirmPending(4600L))
    }

    @Test
    fun `time spent in a transient window does not cancel a real exit`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        d.onWindowChanged("com.launcher", 5000L)
        // המשתמש נשאר במסך הבית — הטיימר מאשר את היציאה.
        assertEquals("com.youtube", d.confirmPending(6100L))
    }
}
