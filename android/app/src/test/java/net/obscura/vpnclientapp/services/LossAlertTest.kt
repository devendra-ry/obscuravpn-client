package net.obscura.vpnclientapp.services

import net.obscura.vpnclientapp.client.ManagerCmdOk.GetStatus.VpnStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LossAlertTest {
    @Test
    fun aDroppedTunnelWarnsWhileReconnecting() {
        assertEquals(
            LossAlert.RECONNECTING,
            lossAlertFor(
                VpnStatus.Connected(),
                VpnStatus.Connecting(reconnecting = true),
                wantConnected = true,
                wasConnected = true,
            ),
        )
    }

    @Test
    fun aLostNetworkWhileConnectedIsALossButAFailedFirstConnectIsNot() {
        val noInternet = VpnStatus.Connecting(connectError = "noInternet")
        assertEquals(
            LossAlert.RECONNECTING,
            lossAlertFor(VpnStatus.Connecting(), noInternet, wantConnected = true, wasConnected = true),
        )
        assertEquals(
            LossAlert.KEEP,
            lossAlertFor(VpnStatus.Disconnected(), noInternet, wantConnected = true, wasConnected = false),
        )
    }

    @Test
    fun aNewConnectOrLocationChangeIsNotALoss() {
        assertEquals(
            LossAlert.KEEP,
            lossAlertFor(VpnStatus.Connected(), VpnStatus.Connecting(), wantConnected = true, wasConnected = true),
        )
    }

    @Test
    fun stoppingWithoutBeingAskedWarnsAndStoppingOnPurposeDoesNot() {
        assertEquals(
            LossAlert.DISCONNECTED,
            lossAlertFor(VpnStatus.Connected(), VpnStatus.Disconnected(), wantConnected = true, wasConnected = true),
        )
        assertEquals(
            LossAlert.CLEAR,
            lossAlertFor(VpnStatus.Connected(), VpnStatus.Disconnected(), wantConnected = false, wasConnected = true),
        )
    }

    @Test
    fun connectingAgainClearsTheWarning() {
        assertEquals(
            LossAlert.CLEAR,
            lossAlertFor(
                VpnStatus.Connecting(reconnecting = true),
                VpnStatus.Connected(),
                wantConnected = true,
                wasConnected = true,
            ),
        )
    }
}
