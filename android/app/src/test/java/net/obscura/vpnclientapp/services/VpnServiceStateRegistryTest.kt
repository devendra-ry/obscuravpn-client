package net.obscura.vpnclientapp.services

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnServiceStateRegistryTest {
    private val disconnected = VpnServiceRuntimeState(TileConnectionState.Disconnected)

    private fun resolve(serviceRunning: Boolean, wantConnected: Boolean, loggedIn: Boolean = true) =
        VpnServiceStateRegistry.resolve(disconnected, serviceRunning, wantConnected, loggedIn).connectionState

    @Test
    fun runningServiceStatusWinsOverTheStoredWish() {
        // Rust stopped the tunnel on its own while the user still wanted it: show the truth, not "connecting".
        assertEquals(TileConnectionState.Disconnected, resolve(serviceRunning = true, wantConnected = true))
    }

    @Test
    fun storedWishStandsInOnlyWhileTheServiceIsNotRunning() {
        assertEquals(TileConnectionState.Connecting, resolve(serviceRunning = false, wantConnected = true))
        assertEquals(TileConnectionState.Disconnected, resolve(serviceRunning = false, wantConnected = false))
        assertEquals(
            TileConnectionState.Disconnected,
            resolve(serviceRunning = false, wantConnected = true, loggedIn = false),
        )
    }

    @Test
    fun liveConnectedOrConnectingIsKept() {
        val connected = VpnServiceRuntimeState(TileConnectionState.Connected)
        assertEquals(connected, VpnServiceStateRegistry.resolve(connected, false, false, false))
    }
}
