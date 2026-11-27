package net.obscura.vpnclientapp.ui

import net.obscura.vpnclientapp.client.TunnelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FallbackTest {
    @Test
    fun speedThatGetsNoAnswerMovesToStealth() {
        assertEquals(
            Fallback.Protocol(TunnelMode.QUIC),
            nextFallback("udpHandshakeFailed", TunnelMode.WIREGUARD_UDP, anyRelay = true, tried = emptySet()),
        )
    }

    @Test
    fun anUnreachableChosenRelayMovesToTheFastestBeforeChangingProtocol() {
        assertEquals(
            Fallback.FastestRelay,
            nextFallback("noRelay", TunnelMode.QUIC, anyRelay = false, tried = setOf(TunnelMode.QUIC)),
        )
    }

    @Test
    fun noRelayAtAllMovesToTcpTlsOnceThenStops() {
        assertEquals(
            Fallback.Protocol(TunnelMode.TCP_TLS),
            nextFallback("noRelay", TunnelMode.QUIC, anyRelay = true, tried = setOf(TunnelMode.QUIC)),
        )
        assertNull(nextFallback("noRelay", TunnelMode.TCP_TLS, anyRelay = true, tried = setOf(TunnelMode.TCP_TLS)))
    }

    @Test
    fun errorsThatAreNotAboutTheNetworkChangeNothing() {
        assertNull(nextFallback("accountExpired", TunnelMode.QUIC, anyRelay = true, tried = emptySet()))
    }
}
