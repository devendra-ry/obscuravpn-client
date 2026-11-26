package net.obscura.vpnclientapp.services

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.R

class ObscuraTileService : TileService() {
    internal data class TilePresentation(
        val tileState: Int,
        val subtitle: CharSequence,
    )

    internal data class TileLabels(
        val connected: String,
        val connecting: String,
        val disconnected: String,
        val lockdown: String,
    )

    private var listenScope: CoroutineScope? = null
    private var collectJob: Job? = null

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile(VpnServiceStateRegistry.resolveRuntimeState(this))
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile(VpnServiceStateRegistry.resolveRuntimeState(this))
        collectJob?.cancel()
        val scope =
            listenScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { listenScope = it }
        collectJob = scope.launch { VpnServiceStateRegistry.state.collect { runtime -> refreshTile(runtime) } }
    }

    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        collectJob?.cancel()
        listenScope?.cancel()
        listenScope = null
        super.onDestroy()
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun { toggle() }
        } else {
            toggle()
        }
    }

    private fun toggle() {
        VpnServiceStateRegistry.toggleTunnelOrLaunch(this) { pending -> startActivityAndCollapse(pending) }
    }

    private fun refreshTile(runtime: VpnServiceRuntimeState) {
        val tile = qsTile ?: return
        val presentation =
            presentTile(
                runtime = runtime,
                labels =
                    TileLabels(
                        connected = getString(R.string.notification_vpn_status_connected),
                        connecting = getString(R.string.notification_vpn_status_connecting),
                        disconnected = getString(R.string.notification_vpn_status_disconnected),
                        lockdown = getString(R.string.qs_tile_lockdown),
                    ),
            )
        tile.icon = Icon.createWithResource(this, R.drawable.icon_connection)
        tile.label = getString(R.string.qs_tile_label)
        tile.state = presentation.tileState
        tile.subtitle = presentation.subtitle
        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, ObscuraTileService::class.java)) }
        }

        internal fun presentTile(runtime: VpnServiceRuntimeState, labels: TileLabels): TilePresentation =
            when (runtime.connectionState) {
                TileConnectionState.Connected -> TilePresentation(Tile.STATE_ACTIVE, labels.connected)
                TileConnectionState.Connecting -> TilePresentation(Tile.STATE_ACTIVE, labels.connecting)
                TileConnectionState.Disconnected ->
                    TilePresentation(
                        Tile.STATE_INACTIVE,
                        if (runtime.isLockdownEnabled) labels.lockdown else labels.disconnected,
                    )
            }
    }
}
