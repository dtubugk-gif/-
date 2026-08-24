package com.routines.appclose

import com.routines.appclose.engine.ExitDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExitDetectorTest {

    private fun detector() = ExitDetector(
        ignoredPackages = setOf("com.android.systemui", "self.pkg"),
        minForegroundMs = 1000L,
    )

    @Test
    fun `first foreground app does not count as exit`() {
        val d = detector()
        assertNull(d.onWindowChanged("com.youtube", 0L))
    }

    @Test
    fun `leaving an app after enough time reports that app`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        val exited = d.onWindowChanged("com.launcher", 2000L)
        assertEquals("com.youtube", exited)
    }

    @Test
    fun `quick switch below threshold is not an exit`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        val exited = d.onWindowChanged("com.launcher", 500L)
        assertNull(exited)
    }

    @Test
    fun `ignored packages are neither exit nor new foreground`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        // מעבר ל-SystemUI (למשל צל התראות) לא נחשב יציאה ולא מחליף חזית.
        assertNull(d.onWindowChanged("com.android.systemui", 2000L))
        // וכשחוזרים מ-SystemUI לאותה אפליקציה — עדיין אין יציאה.
        assertNull(d.onWindowChanged("com.youtube", 2100L))
    }

    @Test
    fun `same package repeated is not an exit`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        assertNull(d.onWindowChanged("com.youtube", 3000L))
    }

    @Test
    fun `null or blank package is ignored`() {
        val d = detector()
        assertNull(d.onWindowChanged(null, 0L))
        assertNull(d.onWindowChanged("", 100L))
    }

    @Test
    fun `sequential exits are each reported`() {
        val d = detector()
        d.onWindowChanged("com.youtube", 0L)
        assertEquals("com.youtube", d.onWindowChanged("com.maps", 2000L))
        assertEquals("com.maps", d.onWindowChanged("com.launcher", 4000L))
    }
}
