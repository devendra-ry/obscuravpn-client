package net.obscura.vpnclientapp.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import net.obscura.vpnclientapp.R
import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.CoreVpnStatus
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay
import net.obscura.vpnclientapp.client.TrafficStats
import net.obscura.vpnclientapp.client.TunnelArgs
import net.obscura.vpnclientapp.client.TunnelMode

/** One hop of the route: its country (null for "fastest"), the place, and what else is known about it. */
data class HopDisplay(
    val countryCode: String?,
    val title: String,
    val subtitle: String?,
)

/** The route as two rows of the location picker's kind: where traffic enters Obscura and where it leaves. */
/** The route as a line from this device through the entry relay and the exit to the internet. */
@Composable
fun RouteCard(
    status: CoreStatus,
    exits: List<OneExit>,
    relays: List<OneRelay>,
    onChooseEntry: () -> Unit,
    onChooseExit: () -> Unit,
) {
    val connected = status.vpnStatus.connected
    val shown = status.vpnStatus.connecting?.tunnelArgs ?: status.tunnelArgs
    val entry = resolveEntryHop(connected, shown, relays)
    val exit = resolveExitHop(connected, shown, exits)
    val lineColor = if (connected != null) LocalExtraColors.current.success else LocalExtraColors.current.border

    ObscuraCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        EndNodeRow(icon = AppIcons.Phone, label = "This device", topLine = null, bottomLine = lineColor)
        HopNodeRow("Entry relay · sees your IP", entry, lineColor, onChooseEntry)
        HopNodeRow("Exit · sees your traffic", exit, lineColor, onChooseExit)
        EndNodeRow(icon = AppIcons.Globe, label = "Internet", topLine = lineColor, bottomLine = null)
    }
}

@Composable
private fun EndNodeRow(
    icon: ImageVector,
    label: String,
    topLine: Color?,
    bottomLine: Color?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailSegment(topLine, bottomLine, width = 36.dp) {
            Icon(
                icon,
                contentDescription = null,
                tint = LocalExtraColors.current.muted,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.muted)
    }
}

@Composable
private fun HopNodeRow(
    caption: String,
    hop: HopDisplay,
    lineColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailSegment(lineColor, lineColor, width = 36.dp) {
            if (hop.countryCode != null) {
                Text(AccountIdUtils.countryFlagEmoji(hop.countryCode), fontSize = 18.sp)
            } else {
                Icon(
                    AppIcons.Speedometer,
                    contentDescription = null,
                    tint = ObscuraPalette.Orange,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = LocalExtraColors.current.muted)
            Text(
                hop.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!hop.subtitle.isNullOrBlank()) {
                Text(
                    hop.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            tint = LocalExtraColors.current.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RailSegment(
    topColor: Color?,
    bottomColor: Color?,
    width: androidx.compose.ui.unit.Dp,
    node: @Composable () -> Unit,
) {
    Box(modifier = Modifier.width(width).height(44.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.matchParentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.width(2.dp).weight(1f).background(topColor ?: Color.Transparent),
            )
            Box(
                modifier = Modifier.width(2.dp).weight(1f).background(bottomColor ?: Color.Transparent),
            )
        }
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            node()
        }
    }
}

private fun resolveEntryHop(
    connected: CoreVpnStatus.ConnectedInfo?,
    args: TunnelArgs,
    relays: List<OneRelay>,
): HopDisplay {
    val activeRelay = connected?.relay
    if (activeRelay != null) {
        return HopDisplay(
            countryCode = activeRelay.countryCode,
            title = activeRelay.cityName,
            subtitle =
                listOfNotNull(placeSubtitle(activeRelay.countryCode, activeRelay.cityName), activeRelay.id)
                    .joinToString(" · "),
        )
    }
    val r = args.relay
    return when {
        r.relay != null -> {
            val found = relays.find { it.id == r.relay.id }
            if (found != null) {
                HopDisplay(
                    countryCode = found.countryCode,
                    title = "${found.cityName} (${found.id})",
                    subtitle = placeSubtitle(found.countryCode, found.cityName),
                )
            } else {
                HopDisplay(null, r.relay.id, null)
            }
        }
        r.city != null -> {
            val found = relays.find { it.countryCode == r.city.countryCode && it.cityCode == r.city.cityCode }
            val city = found?.cityName ?: r.city.cityCode.uppercase(Locale.ROOT)
            HopDisplay(
                countryCode = r.city.countryCode,
                title = city,
                subtitle = placeSubtitle(r.city.countryCode, city),
            )
        }
        r.country != null ->
            HopDisplay(
                countryCode = r.country.countryCode,
                title = AccountIdUtils.countryDisplayName(r.country.countryCode),
                subtitle = "Any city",
            )
        else -> HopDisplay(null, "Fastest entry relay", "Picked when you connect")
    }
}

private fun resolveExitHop(
    connected: CoreVpnStatus.ConnectedInfo?,
    args: TunnelArgs,
    exits: List<OneExit>,
): HopDisplay {
    if (connected != null) {
        val e = connected.exit
        val sub =
            listOfNotNull(placeSubtitle(e.countryCode, e.cityName), e.providerName.ifBlank { null }, e.id)
                .joinToString(" · ")
                .ifBlank { null }
        return HopDisplay(e.countryCode, e.cityName, sub)
    }
    val e = args.exit
    return when {
        e.exit != null -> {
            val found = exits.find { it.id == e.exit.id }
            if (found != null) {
                HopDisplay(
                    found.countryCode,
                    found.cityName,
                    listOfNotNull(placeSubtitle(found.countryCode, found.cityName), found.id).joinToString(" · "),
                )
            } else {
                HopDisplay(null, e.exit.id, null)
            }
        }
        e.city != null -> {
            val found = exits.find { it.countryCode == e.city.countryCode && it.cityCode == e.city.cityCode }
            val city = found?.cityName ?: e.city.cityCode.uppercase(Locale.ROOT)
            HopDisplay(
                e.city.countryCode,
                city,
                placeSubtitle(e.city.countryCode, city),
            )
        }
        e.country != null ->
            HopDisplay(
                e.country.countryCode,
                AccountIdUtils.countryDisplayName(e.country.countryCode),
                "Any city",
            )
        else -> HopDisplay(null, "Fastest exit", "Picked when you connect")
    }
}

fun placeSubtitle(countryCode: String, cityName: String?): String? {
    val country = AccountIdUtils.countryDisplayName(countryCode)
    return if (cityName != null && cityName.equals(country, ignoreCase = true)) null else country
}

data class RateSnapshot(
    val downBps: Double,
    val upBps: Double,
)

@Composable
fun TrafficStatsCard(
    stats: TrafficStats?,
    rates: RateSnapshot?,
    mode: TunnelMode,
    onDetails: () -> Unit,
) {
    ObscuraCard(
        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onDetails),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RateCell(AppIcons.ArrowDown, "Download", rates?.downBps, Modifier.weight(1f))
            RateCell(AppIcons.ArrowUp, "Upload", rates?.upBps, Modifier.weight(1f))
        }
        SectionDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallStat("Latency", stats?.let { "${it.latestLatencyMs.toInt()} ms" } ?: "–", Modifier.weight(1f))
            SmallStat("Protocol", modeLabel(mode), Modifier.weight(1f))
            SmallStat("Session", stats?.let { formatSession(it.connectedMs) } ?: "–", Modifier.weight(1f))
        }
        // The whole card opens the sheet; this says so.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Connection details", style = MaterialTheme.typography.labelLarge, color = ObscuraPalette.Orange)
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = ObscuraPalette.Orange,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RateCell(icon: ImageVector, label: String, bps: Double?, modifier: Modifier = Modifier) {
    val (value, unit) = formatRate(bps)
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = ObscuraPalette.Orange, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.muted)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.muted,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailsSheet(connected: CoreVpnStatus.ConnectedInfo, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Connection details", style = MaterialTheme.typography.titleLarge)
            serverInfoRows(connected).forEach { InfoLine(it) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ConnectionProgressBar(
    vpnStatus: CoreVpnStatus,
    offline: Boolean,
) {
    val isConnected = vpnStatus.connected != null
    val isConnecting = vpnStatus.connecting != null
    val extra = LocalExtraColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ProgressEndNode(AppIcons.Phone, "Your device", MaterialTheme.colorScheme.onSurface)
            when {
                offline -> OfflineMiddleSegment()
                !isConnected && !isConnecting -> DisconnectedMiddleSegment()
                else -> ConnectedOrConnectingMiddle(isConnected, isConnecting)
            }
            val globeTint =
                when {
                    offline -> MaterialTheme.colorScheme.error
                    isConnected -> MaterialTheme.colorScheme.onSurface
                    else -> extra.muted
                }
            ProgressEndNode(AppIcons.Globe, "Internet", globeTint)
        }
    }
}

@Composable
private fun ProgressEndNode(icon: ImageVector, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun OfflineMiddleSegment() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(32.dp).height(2.dp).background(LocalExtraColors.current.border))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text("No internet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Box(Modifier.width(32.dp).height(2.dp).background(LocalExtraColors.current.border))
    }
}

@Composable
private fun DisconnectedMiddleSegment() {
    Column(
        modifier = Modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.height(4.dp))
        Text(
            "Obscura not connected",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ConnectedOrConnectingMiddle(isConnected: Boolean, isConnecting: Boolean) {
    val pulse by
        rememberInfiniteTransition(label = "barPulse")
            .animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                label = "pulseAlpha",
            )
    val lineColor =
        when {
            isConnected -> ObscuraPalette.Orange
            isConnecting -> ObscuraPalette.Orange.copy(alpha = pulse)
            else -> LocalExtraColors.current.border
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(24.dp).height(2.dp).background(lineColor))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.obscura_icon_happy),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            )
            Text("Obscura", style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.width(24.dp).height(2.dp).background(lineColor))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                AppIcons.EyeOff,
                contentDescription = null,
                tint = if (isConnected) MaterialTheme.colorScheme.onSurface else LocalExtraColors.current.muted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Blind Relay",
                style = MaterialTheme.typography.labelSmall,
                color = if (isConnected) MaterialTheme.colorScheme.onSurface else LocalExtraColors.current.muted,
            )
        }
        Box(
            Modifier.width(24.dp)
                .height(2.dp)
                .background(if (isConnected) ObscuraPalette.Orange else LocalExtraColors.current.border),
        )
    }
}

fun modeLabel(mode: TunnelMode): String =
    when (mode) {
        TunnelMode.QUIC -> "Stealth"
        TunnelMode.WIREGUARD_UDP -> "Speed"
        TunnelMode.TCP_TLS -> "TCP/TLS"
    }

fun transportName(mode: TunnelMode): String =
    when (mode) {
        TunnelMode.QUIC -> "WireGuard® in QUIC"
        TunnelMode.WIREGUARD_UDP -> "WireGuard® over UDP"
        TunnelMode.TCP_TLS -> "WireGuard® in TCP/TLS"
    }

private fun formatRate(bps: Double?): Pair<String, String> {
    if (bps == null) return "–" to ""
    val mbps = bps / 1e6
    return when {
        mbps >= 100.0 -> String.format(Locale.US, "%.0f", mbps) to "Mbit/s"
        mbps >= 10.0 -> String.format(Locale.US, "%.1f", mbps) to "Mbit/s"
        mbps >= 1.0 -> String.format(Locale.US, "%.2f", mbps) to "Mbit/s"
        else -> String.format(Locale.US, "%.0f", bps / 1e3) to "kbit/s"
    }
}

fun formatSession(ms: Long): String {
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
