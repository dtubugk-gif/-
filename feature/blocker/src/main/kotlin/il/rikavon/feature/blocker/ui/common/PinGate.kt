package il.rikavon.feature.blocker.ui.common

import il.rikavon.core.data.security.PinVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Choosing a PIN takes two entries. */
enum class PinSetup { CHOOSE, CONFIRM }

data class PinGateState(
    /** A PIN is being asked for, to run something that loosens enforcement. */
    val asking: Boolean = false,
    val wrong: Boolean = false,
    /** A new PIN is being chosen. */
    val setup: PinSetup? = null,
)

/**
 * The settings lock as a screen sees it: [require] parks an action behind a PIN prompt and runs it once the
 * right PIN comes in; [startSetup] walks through choosing a PIN twice. Plain Kotlin, shared by the limit
 * editor and the settings screen. [onPinChosen] gets the new hash, or null when the lock is removed.
 */
class PinGate(private val pinHash: () -> String?, private val onPinChosen: (hash: String?) -> Unit = {}) {
    private val _state = MutableStateFlow(PinGateState())
    val state: StateFlow<PinGateState> = _state.asStateFlow()
    private var pending: (() -> Unit)? = null
    private var first: String? = null

    /** Runs [action] now when settings are not locked, otherwise after the PIN. */
    fun require(action: () -> Unit) {
        if (pinHash() == null) {
            action()
            return
        }
        pending = action
        _state.update { it.copy(asking = true, wrong = false) }
    }

    /** Removing the lock asks for the PIN one last time. */
    fun removePin() = require { onPinChosen(null) }

    fun startSetup() {
        first = null
        _state.value = PinGateState(setup = PinSetup.CHOOSE)
    }

    fun submit(pin: String) {
        when (_state.value.setup) {
            PinSetup.CHOOSE -> choose(pin)
            PinSetup.CONFIRM -> confirm(pin)
            null -> verify(pin)
        }
    }

    fun cancel() {
        pending = null
        first = null
        _state.value = PinGateState()
    }

    private fun choose(pin: String) {
        if (PinVerifier.isValid(pin)) {
            first = pin
            _state.value = PinGateState(setup = PinSetup.CONFIRM)
        } else {
            _state.update { it.copy(wrong = true) }
        }
    }

    private fun confirm(pin: String) {
        if (pin == first) {
            onPinChosen(PinVerifier.hash(pin))
            cancel()
        } else {
            _state.update { it.copy(wrong = true) }
        }
    }

    private fun verify(pin: String) {
        if (PinVerifier.matches(pin, pinHash())) {
            val action = pending
            cancel()
            action?.invoke()
        } else {
            _state.update { it.copy(wrong = true) }
        }
    }
}
