package net.obscura.vpnclientapp.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.obscura.lib.util.Logger

private val log = Logger(AutoConnectBootReceiver::class)

class AutoConnectBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val store = VpnRestoreStore(context)
        val shouldStart =
            when (action) {
                Intent.ACTION_BOOT_COMPLETED -> store.shouldStartOnBoot()
                Intent.ACTION_MY_PACKAGE_REPLACED -> store.shouldStartOnUpgrade()
                else -> false
            }
        if (!shouldStart) return
        if (context.prepareVpnService() != PrepareResult.Ready) {
            log.info("skipping $action auto-start: VPN permission not ready")
            return
        }
        log.info("starting VPN service on $action")
        context.startVpnService(store.lastTunnelArgs)
    }
}
