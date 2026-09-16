package il.rikavon.feature.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenDetectorTest {
    private val detector = OpenDetector(self = SELF)
    private val pleading = setOf(INSTAGRAM, TIKTOK)

    @Test
    fun `the first observation never counts, even inside a begged-about app`() {
        assertNull(detector.opened(INSTAGRAM, pleading))
    }

    @Test
    fun `arriving from the launcher is an open`() {
        detector.opened(LAUNCHER, pleading)
        assertEquals(INSTAGRAM, detector.opened(INSTAGRAM, pleading))
    }

    @Test
    fun `staying in the app is not another open`() {
        detector.opened(LAUNCHER, pleading)
        detector.opened(INSTAGRAM, pleading)
        assertNull(detector.opened(INSTAGRAM, pleading))
    }

    @Test
    fun `coming back from the pet's own call is not an open`() {
        detector.opened(LAUNCHER, pleading)
        detector.opened(INSTAGRAM, pleading)
        assertNull(detector.opened(SELF, pleading))
        assertNull(detector.opened(INSTAGRAM, pleading))
    }

    @Test
    fun `a gap with nothing in front is not a departure`() {
        detector.opened(LAUNCHER, pleading)
        detector.opened(INSTAGRAM, pleading)
        assertNull(detector.opened(null, pleading))
        assertNull(detector.opened(INSTAGRAM, pleading))
    }

    @Test
    fun `leaving and coming back is an open again`() {
        detector.opened(LAUNCHER, pleading)
        detector.opened(INSTAGRAM, pleading)
        assertNull(detector.opened(LAUNCHER, pleading))
        assertEquals(INSTAGRAM, detector.opened(INSTAGRAM, pleading))
    }

    @Test
    fun `switching between two begged-about apps opens the second`() {
        detector.opened(LAUNCHER, pleading)
        detector.opened(INSTAGRAM, pleading)
        assertEquals(TIKTOK, detector.opened(TIKTOK, pleading))
    }

    @Test
    fun `apps nobody begs about are ignored`() {
        detector.opened(LAUNCHER, pleading)
        assertNull(detector.opened(LAUNCHER, pleading))
        assertNull(detector.opened("com.whatsapp", pleading))
    }

    private companion object {
        const val SELF = "il.rikavon"
        const val LAUNCHER = "com.android.launcher"
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
    }
}
