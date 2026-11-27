package net.obscura.vpnclientapp.ui

import net.obscura.vpnclientapp.client.TunnelMode

/** What to change after a connection attempt failed. */
sealed interface Fallback {
    data class Protocol(val mode: TunnelMode) : Fallback

    /** The chosen entry relay is unreachable: let the core pick the fastest one. */
    data object FastestRelay : Fallback
}

/**
 * What to try after [code] stopped a connection in [mode], as Proton's "smart protocol" and reconnection do. Speed
 * (plain WireGuard® over UDP) that gets no answer moves to Stealth. A chosen entry relay that cannot be reached moves
 * to the fastest relay, keeping the protocol; when no relay at all can be reached, the network is blocking UDP, so the
 * protocol moves to TCP/TLS. [tried] holds the protocols already tried in this attempt, so nothing loops. Null when
 * there is nothing better to try or the error is not about the network.
 */
fun nextFallback(code: String, mode: TunnelMode, anyRelay: Boolean, tried: Set<TunnelMode>): Fallback? =
    when {
        code == "udpHandshakeFailed" && mode == TunnelMode.WIREGUARD_UDP && TunnelMode.QUIC !in tried ->
            Fallback.Protocol(TunnelMode.QUIC)
        code == "noRelay" && !anyRelay -> Fallback.FastestRelay
        code == "noRelay" && mode != TunnelMode.TCP_TLS && TunnelMode.TCP_TLS !in tried ->
            Fallback.Protocol(TunnelMode.TCP_TLS)
        else -> null
    }
