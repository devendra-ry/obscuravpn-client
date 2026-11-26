package net.obscura.vpnclientapp.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.activities.MainActivity
import net.obscura.vpnclientapp.client.ManagerCmdOk

private val log = Logger("VpnServiceStateRegistry")

enum class TileConnectionState {
    Disconnected,
    Connecting,
    Connected,
}

data class VpnServiceRuntimeState(
    val connectionState: TileConnectionState = TileConnectionState.Disconnected,
    val isAlwaysOn: Boolean = false,
    val isLockdownEnabled: Boolean = false,
)

/** In-process observable runtime state shared across [ObscuraVpnService], QS Tile, Widget, and Compose UI. */
object VpnServiceStateRegistry {
    private val _state = MutableStateFlow(VpnServiceRuntimeState())
    val state: StateFlow<VpnServiceRuntimeState> = _state.asStateFlow()

    fun updateFromStatus(
        vpnStatus: ManagerCmdOk.GetStatus.VpnStatus?,
        isAlwaysOn: Boolean,
        isLockdownEnabled: Boolean,
    ) {
        _state.value =
            VpnServiceRuntimeState(
                connectionState = mapStatus(vpnStatus),
                isAlwaysOn = isAlwaysOn,
                isLockdownEnabled = isLockdownEnabled,
            )
    }

    fun updateSystemFlags(isAlwaysOn: Boolean, isLockdownEnabled: Boolean) {
        _state.value =
            _state.value.copy(
                isAlwaysOn = isAlwaysOn,
                isLockdownEnabled = isLockdownEnabled,
            )
    }

    fun markDisconnected(isAlwaysOn: Boolean = false, isLockdownEnabled: Boolean = false) {
        _state.value =
            VpnServiceRuntimeState(
                connectionState = TileConnectionState.Disconnected,
                isAlwaysOn = isAlwaysOn,
                isLockdownEnabled = isLockdownEnabled,
            )
    }

    fun resolveRuntimeState(context: Context): VpnServiceRuntimeState {
        val live = _state.value
        if (live.connectionState != TileConnectionState.Disconnected) return live
        val store = VpnRestoreStore(context)
        return if (store.wantConnected && store.loggedIn) {
            live.copy(connectionState = TileConnectionState.Connecting)
        } else {
            live
        }
    }

    fun notifySystemSurfaces(context: Context) {
        ObscuraTileService.requestTileUpdate(context)
        ObscuraWidgetProvider.updateAll(context)
    }

    fun openMainAppPendingIntent(context: Context): PendingIntent {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun toggleTunnelOrLaunch(context: Context, launchApp: (PendingIntent) -> Unit) {
        val runtime = resolveRuntimeState(context)
        when (runtime.connectionState) {
            TileConnectionState.Connected,
            TileConnectionState.Connecting -> {
                log.info("surface requesting VPN disconnect")
                val stopIntent = Intent(context, ObscuraVpnService::class.java).apply { action = ACTION_STOP_TUNNEL }
                runCatching { context.startService(stopIntent) }
                    .onFailure { log.error("failed to stop service from surface: ${it.message}", tr = it) }
            }
            TileConnectionState.Disconnected -> {
                val store = VpnRestoreStore(context)
                if (!store.loggedIn || context.prepareVpnService() != PrepareResult.Ready) {
                    log.info("surface opening app for setup/login")
                    launchApp(openMainAppPendingIntent(context))
                } else {
                    log.info("surface requesting VPN connect")
                    context.startVpnService(null)
                }
            }
        }
    }

    internal fun mapStatus(vpnStatus: ManagerCmdOk.GetStatus.VpnStatus?): TileConnectionState =
        when (vpnStatus) {
            is ManagerCmdOk.GetStatus.VpnStatus.Connected -> TileConnectionState.Connected
            is ManagerCmdOk.GetStatus.VpnStatus.Connecting -> TileConnectionState.Connecting
            is ManagerCmdOk.GetStatus.VpnStatus.Disconnected,
            null -> TileConnectionState.Disconnected
        }
}
