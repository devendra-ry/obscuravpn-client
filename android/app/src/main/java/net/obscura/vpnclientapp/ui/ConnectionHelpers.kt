package net.obscura.vpnclientapp.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.obscura.vpnclientapp.client.TunnelMode

/** A city connected to recently, newest first in [PreferencesManager.Preferences.recentExits]. */
@Serializable
data class RecentExit(
    @SerialName("country_code") val countryCode: String,
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
)

const val MAX_RECENT_EXITS = 5

/** [recents] with [exit] moved (or added) to the front, at most [MAX_RECENT_EXITS] long. */
fun pushRecent(recents: List<RecentExit>, exit: RecentExit): List<RecentExit> =
    (listOf(exit) + recents.filterNot { it.countryCode == exit.countryCode && it.cityCode == exit.cityCode }).take(
        MAX_RECENT_EXITS,
    )

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
