package il.rikavon.feature.blocker.ui.common

import il.rikavon.core.data.security.PinVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinGateTest {
    private var hash: String? = null
    private var chosen: String? = null
    private val gate = PinGate(pinHash = { hash }, onPinChosen = { chosen = it })

    @Test
    fun `without a lock the action runs at once`() {
        var ran = false
        gate.require { ran = true }
        assertTrue(ran)
        assertFalse(gate.state.value.asking)
    }

    @Test
    fun `with a lock the action waits for the right PIN`() {
        hash = PinVerifier.hash("2468")
        var ran = false
        gate.require { ran = true }
        assertTrue(gate.state.value.asking)
        gate.submit("1111")
        assertTrue(gate.state.value.wrong)
        assertFalse(ran)
        gate.submit("2468")
        assertTrue(ran)
        assertEquals(PinGateState(), gate.state.value)
    }

    @Test
    fun `cancelling drops the action`() {
        hash = PinVerifier.hash("2468")
        var ran = false
        gate.require { ran = true }
        gate.cancel()
        gate.submit("2468")
        assertFalse(ran)
    }

    @Test
    fun `removing the lock needs the PIN and then reports null`() {
        hash = PinVerifier.hash("2468")
        chosen = "unchanged"
        gate.removePin()
        assertTrue(gate.state.value.asking)
        gate.submit("2468")
        assertNull(chosen)
    }

    @Test
    fun `choosing a PIN takes two matching entries of four to eight digits`() {
        gate.startSetup()
        assertEquals(PinSetup.CHOOSE, gate.state.value.setup)
        gate.submit("12")
        assertTrue(gate.state.value.wrong)
        gate.submit("1234")
        assertEquals(PinSetup.CONFIRM, gate.state.value.setup)
        gate.submit("1235")
        assertTrue(gate.state.value.wrong)
        assertNull(chosen)
        gate.submit("1234")
        assertEquals(PinVerifier.hash("1234"), chosen)
        assertEquals(PinGateState(), gate.state.value)
    }
}
