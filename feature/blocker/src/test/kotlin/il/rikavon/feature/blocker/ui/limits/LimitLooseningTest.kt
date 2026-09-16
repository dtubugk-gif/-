package il.rikavon.feature.blocker.ui.limits

import il.rikavon.core.data.model.AppLimit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitLooseningTest {
    private val base =
        AppLimit("a", 30, fullBlock = false, enabled = true, createdAt = 0, maxOpens = 10, sessionMinutes = 20)

    @Test
    fun `more minutes, a cap switched off or raised, disabling, and dropping a full block are looser`() {
        assertTrue(LimitLoosening.isLooser(base, base.copy(limitMinutes = 45)))
        assertTrue(LimitLoosening.isLooser(base, base.copy(maxOpens = 0)))
        assertTrue(LimitLoosening.isLooser(base, base.copy(maxOpens = 12)))
        assertTrue(LimitLoosening.isLooser(base, base.copy(sessionMinutes = 0)))
        assertTrue(LimitLoosening.isLooser(base, base.copy(enabled = false)))
        assertTrue(LimitLoosening.isLooser(base.copy(fullBlock = true), base))
        assertTrue(LimitLoosening.isLooser(base.copy(callOnOpen = true), base))
    }

    @Test
    fun `tightening, a new limit, and no change are not`() {
        assertFalse(LimitLoosening.isLooser(base, base.copy(limitMinutes = 15)))
        assertFalse(LimitLoosening.isLooser(base, base.copy(maxOpens = 5, sessionMinutes = 10)))
        assertFalse(LimitLoosening.isLooser(base, base.copy(fullBlock = true, limitMinutes = 240)))
        assertFalse(LimitLoosening.isLooser(base, base))
        assertFalse(LimitLoosening.isLooser(null, base.copy(limitMinutes = 240)))
        assertFalse(LimitLoosening.isLooser(base.copy(maxOpens = 0), base.copy(maxOpens = 0)))
    }
}
