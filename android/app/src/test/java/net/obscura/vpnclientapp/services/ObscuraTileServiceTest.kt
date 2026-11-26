package net.obscura.vpnclientapp.services

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class ObscuraTileServiceTest {
    private val labels =
        ObscuraTileService.TileLabels(
            connected = "Connected",
            connecting = "Connecting",
            disconnected = "Disconnected",
            lockdown = "Lockdown active",
        )

    @Test
    fun mapsStatusToExpectedTileStates() {
        assertEquals(TileConnectionState.Disconnected, VpnServiceStateRegistry.mapStatus(null))

        val disconnected =
            ObscuraTileService.presentTile(
                VpnServiceRuntimeState(TileConnectionState.Disconnected, isAlwaysOn = false, isLockdownEnabled = false),
                labels,
            )
        assertEquals(Tile.STATE_INACTIVE, disconnected.tileState)
        assertEquals("Disconnected", disconnected.subtitle)

        val connecting =
            ObscuraTileService.presentTile(
                VpnServiceRuntimeState(TileConnectionState.Connecting, isAlwaysOn = false, isLockdownEnabled = false),
                labels,
            )
        assertEquals(Tile.STATE_ACTIVE, connecting.tileState)
        assertEquals("Connecting", connecting.subtitle)

        val connected =
            ObscuraTileService.presentTile(
                VpnServiceRuntimeState(TileConnectionState.Connected, isAlwaysOn = true, isLockdownEnabled = false),
                labels,
            )
        assertEquals(Tile.STATE_ACTIVE, connected.tileState)
        assertEquals("Connected", connected.subtitle)

        val lockdown =
            ObscuraTileService.presentTile(
                VpnServiceRuntimeState(TileConnectionState.Disconnected, isAlwaysOn = true, isLockdownEnabled = true),
                labels,
            )
        assertEquals(Tile.STATE_INACTIVE, lockdown.tileState)
        assertEquals("Lockdown active", lockdown.subtitle)
    }
}
