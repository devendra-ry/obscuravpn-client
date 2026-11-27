package net.obscura.vpnclientapp.services

import net.obscura.vpnclientapp.client.ManagerCmdOk.GetStatus.VpnStatus

/** What the "connection lost" alert should do after a status change. */
internal enum class LossAlert {
    /** The tunnel dropped and the core is reconnecting: warn if that lasts (a network handover is quick). */
    RECONNECTING,

    /** The tunnel stopped although nobody asked it to: warn now. */
    DISCONNECTED,

    /** Connected again, or stopped on purpose: take any warning down. */
    CLEAR,

    /** Nothing new (a first connect, a location change). */
    KEEP,
}

/**
 * Whether a tunnel that was up is now down: the core is bringing a dropped tunnel back ([reconnecting]), or retrying
 * after an error such as `noInternet` (it reports a lost network that way, without [reconnecting]).
 */
fun connectionLost(wasConnected: Boolean, reconnecting: Boolean, connectError: String?): Boolean =
    reconnecting || (wasConnected && connectError != null)

/**
 * Proton-style protection warning. [wasConnected] says the tunnel was up since the last connect, so a first connect
 * that fails is not a loss; [wantConnected] is false once the user (or the system revoking the VPN) stopped the tunnel,
 * so only a stop nobody asked for counts.
 */
internal fun lossAlertFor(prev: VpnStatus?, now: VpnStatus, wantConnected: Boolean, wasConnected: Boolean): LossAlert =
    when (now) {
        is VpnStatus.Connected -> LossAlert.CLEAR
        is VpnStatus.Connecting ->
            if (connectionLost(wasConnected, now.reconnecting, now.connectError)) LossAlert.RECONNECTING
            else LossAlert.KEEP
        is VpnStatus.Disconnected ->
            if (wantConnected && prev != null && prev !is VpnStatus.Disconnected) {
                LossAlert.DISCONNECTED
            } else {
                LossAlert.CLEAR
            }
    }
