package net.obscura.vpnclientapp.ui.compose

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import net.obscura.vpnclientapp.client.CoreVpnStatus
import net.obscura.vpnclientapp.client.TrafficStats

/**
 * Obscura's status card: a shield and "Connected to <city>" in green (red otherwise), what that means for traffic, and
 * Connect or Disconnect. While connected: the session time and the server info, folded, as in Obscura and Ombra.
 */
@Composable
fun VpnStatusCard(
    vpn: CoreVpnStatus,
    stats: TrafficStats?,
    internetAvailable: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = vpn.connected
    val connecting = vpn.connecting != null
    val extra = LocalExtraColors.current
    val color =
        when {
            connected != null -> extra.success
            vpn.connecting?.reconnecting == true -> MaterialTheme.colorScheme.error
            connecting -> ObscuraPalette.Orange
            else -> MaterialTheme.colorScheme.error
        }
    val title =
        when {
            connected != null -> "Connected to ${connected.exit.cityName}"
            connecting -> if (vpn.connecting?.reconnecting == true) "Connection lost" else "Connecting…"
            !internetAvailable -> "Offline"
            else -> "Disconnected"
        }
    val subtitle =
        when {
            connected != null -> "Your traffic is protected"
            vpn.connecting?.reconnecting == true -> "Reconnecting; traffic is held until it's back"
            !internetAvailable -> "Connect to the internet"
            else -> "Obscura is not connected"
        }
    ObscuraCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (connected != null) AppIcons.ShieldCheck else AppIcons.ShieldOff,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extra.muted)
            }
            Spacer(Modifier.width(10.dp))
            StatusButton(
                connected = connected != null,
                connecting = connecting,
                internetAvailable,
                onConnect,
                onDisconnect,
            )
        }
        if (connected != null) {
            SectionDivider()
            SessionAndServerInfo(connected, stats)
        }
    }
}

@Composable
private fun StatusButton(
    connected: Boolean,
    connecting: Boolean,
    internetAvailable: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    if (connected || connecting) {
        val red = MaterialTheme.colorScheme.error
        OutlinedButton(
            onClick = onDisconnect,
            border = BorderStroke(1.dp, red.copy(alpha = 0.5f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(if (connecting) "Cancel" else "Disconnect", color = red)
        }
    } else {
        ObscuraGradientButton(text = "Connect", onClick = onConnect, enabled = internetAvailable)
    }
}

@Composable
private fun SessionAndServerInfo(connected: CoreVpnStatus.ConnectedInfo, stats: TrafficStats?) {
    var open by rememberSaveable { mutableStateOf(false) }
    val muted = LocalExtraColors.current.muted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Current session",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        Text(stats?.let { formatSession(it.connectedMs) } ?: "–", style = MaterialTheme.typography.bodyMedium)
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Server info",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        FoldButton(open = open, label = "Server info", onToggle = { open = !open })
    }
    AnimatedVisibility(visible = open) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            serverInfoRows(connected).forEach { InfoLine(it) }
        }
    }
}

/** One server-info fact: a muted label over its selectable value; [InfoRow.link] values open the page. */
@Composable
fun InfoLine(row: InfoRow) {
    val context = LocalContext.current
    val valueColor = if (row.link != null) ObscuraPalette.Orange else Color.Unspecified
    Column {
        Text(row.label, style = MaterialTheme.typography.labelMedium, color = LocalExtraColors.current.muted)
        SelectionContainer {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    if (row.link != null) {
                        Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, row.link.toUri())) }
                    } else {
                        Modifier
                    },
            ) {
                Text(
                    row.value,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (row.mono) FontMono else MaterialTheme.typography.bodyMedium.fontFamily,
                        ),
                    color = valueColor,
                )
                if (row.link != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        AppIcons.ExternalLink,
                        contentDescription = null,
                        tint = valueColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
