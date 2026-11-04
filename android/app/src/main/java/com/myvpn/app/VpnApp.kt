package com.myvpn.app

import android.app.Application
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the single WireGuard backend and tunnel for the whole app process. */
class VpnApp : Application() {
    val backend: Backend by lazy { GoBackend(applicationContext) }
    val tunnel = AppTunnel()
    val configStore by lazy { ConfigStore(this) }
}

/** The one tunnel this app manages. The backend reports state changes here. */
class AppTunnel : Tunnel {
    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state.asStateFlow()

    override fun getName(): String = "myvpn"

    override fun onStateChange(newState: Tunnel.State) {
        _state.value = newState
    }
}
