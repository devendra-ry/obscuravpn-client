package net.obscura.vpnclientapp.ui.compose

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import net.obscura.vpnclientapp.R
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay
import net.obscura.vpnclientapp.client.RelaySelector
import net.obscura.vpnclientapp.client.TrafficStats
import net.obscura.vpnclientapp.client.TunnelMode

data class ConnectionUiParams(
    val status: CoreStatus,
    val exits: List<OneExit>,
    val relays: List<OneRelay>,
    val stats: TrafficStats?,
    val internetAvailable: Boolean,
    val startError: String?,
)

data class ConnectionActions(
    val onConnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onChooseEntry: () -> Unit,
    val onChooseExit: () -> Unit,
    val onOpenAccount: () -> Unit,
    val onSwitchMode: (TunnelMode) -> Unit,
    val onUseFastestRelay: () -> Unit,
)

private data class ConnectionVisualState(
    val isConnected: Boolean,
    val isConnecting: Boolean,
    val offline: Boolean,
    val accountExpired: Boolean,
    val validating: Boolean,
    val frame: Int,
)

@Composable
fun ConnectionScreen(
    params: ConnectionUiParams,
    actions: ConnectionActions,
    contentPadding: PaddingValues,
) {
    val isDark = LocalExtraColors.current.isDark
    val status = params.status
    val vpn = status.vpnStatus
    val isConnected = vpn.connected != null
    val isConnecting = vpn.connecting != null
    val offline = !params.internetAvailable && !isConnected
    val accountExpired = status.account?.accountInfo?.active == false
    var showDetails by remember { mutableStateOf(false) }
    val rates = rememberComputedRates(params.stats)
    val frame = rememberTransitionFrame(isConnecting)
    val visual =
        ConnectionVisualState(isConnected, isConnecting, offline, accountExpired, status.account == null, frame)

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        DecoBackdrop(visual = visual, isDark = isDark, modifier = Modifier.align(Alignment.BottomCenter))
        ConnectionScrollBody(
            params = params,
            actions = actions,
            visual = visual,
            rates = rates,
            onOpenDetails = { showDetails = true },
        )
    }

    val connectedInfo = vpn.connected
    if (showDetails && connectedInfo != null) {
        ConnectionDetailsSheet(connectedInfo, onDismiss = { showDetails = false })
    }
}

@Composable
private fun ConnectionScrollBody(
    params: ConnectionUiParams,
    actions: ConnectionActions,
    visual: ConnectionVisualState,
    rates: RateSnapshot?,
    onOpenDetails: () -> Unit,
) {
    val context = LocalContext.current
    val status = params.status
    val vpn = status.vpnStatus
    Column(
        modifier =
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        MascotGraphic(visual)
        StatusHeadline(status, params.exits, visual.offline, visual.accountExpired)
        PrimaryActionSection(
            visual = visual,
            actions = actions,
            onCheckConnection = {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://obscura.com/check".toUri()))
            },
        )
        ConnectionNotice(
            params,
            actions,
            onOpenVpnSettings = { context.startActivity(Intent(AndroidSettings.ACTION_VPN_SETTINGS)) },
        )
        RouteCard(status, params.exits, params.relays, actions.onChooseEntry, actions.onChooseExit)
        if (visual.isConnected && vpn.connected != null) {
            TrafficStatsCard(params.stats, rates, vpn.connected.transport, onOpenDetails)
        }
        Spacer(Modifier.weight(1f, fill = true))
        ConnectionProgressBar(vpn, visual.offline)
    }
}

@Composable
private fun rememberComputedRates(stats: TrafficStats?): RateSnapshot? {
    var prev by remember { mutableStateOf<Pair<TrafficStats, Long>?>(null) }
    var rates by remember { mutableStateOf<RateSnapshot?>(null) }
    LaunchedEffect(stats) {
        if (stats == null) {
            prev = null
            rates = null
        } else {
            val now = System.nanoTime()
            val previous = prev
            if (previous != null && previous.first.connId == stats.connId && now > previous.second) {
                val secs = (now - previous.second) / 1_000_000_000.0
                if (secs > 0.1) {
                    val down = (stats.rxBytes - previous.first.rxBytes).coerceAtLeast(0L) * 8.0 / secs
                    val up = (stats.txBytes - previous.first.txBytes).coerceAtLeast(0L) * 8.0 / secs
                    rates = RateSnapshot(down, up)
                }
            }
            prev = stats to now
        }
    }
    return rates
}

@Composable
private fun rememberTransitionFrame(active: Boolean): Int {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) {
            frame = 0
        } else {
            while (true) {
                delay(240L)
                frame = (frame + 1) % 4
            }
        }
    }
    return frame
}

@Composable
private fun DecoBackdrop(
    visual: ConnectionVisualState,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val res =
        when {
            visual.offline -> if (isDark) R.drawable.deco_offline_dark else R.drawable.deco_offline_light
            visual.isConnecting -> connectingDecoRes(isDark, visual.frame % 3)
            visual.isConnected -> R.drawable.deco_connected
            else -> if (isDark) R.drawable.deco_disconnected_dark else R.drawable.deco_disconnected_light
        }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        alpha = 0.25f,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun connectingDecoRes(isDark: Boolean, index: Int): Int =
    if (isDark) {
        when (index) {
            0 -> R.drawable.deco_connecting_dark_1
            1 -> R.drawable.deco_connecting_dark_2
            else -> R.drawable.deco_connecting_dark_3
        }
    } else {
        when (index) {
            0 -> R.drawable.deco_connecting_light_1
            1 -> R.drawable.deco_connecting_light_2
            else -> R.drawable.deco_connecting_light_3
        }
    }

@Composable
private fun MascotGraphic(visual: ConnectionVisualState) {
    val res =
        when {
            visual.isConnecting ->
                when (visual.frame) {
                    0 -> R.drawable.mascot_connecting_1
                    1 -> R.drawable.mascot_connecting_2
                    2 -> R.drawable.mascot_connecting_3
                    else -> R.drawable.mascot_connecting_4
                }
            visual.offline || visual.accountExpired -> R.drawable.mascot_dead
            visual.isConnected -> R.drawable.mascot_connected
            visual.validating -> R.drawable.mascot_validating
            else -> R.drawable.mascot_not_connected
        }
    Image(painter = painterResource(res), contentDescription = null, modifier = Modifier.size(128.dp))
}

@Composable
private fun StatusHeadline(
    status: CoreStatus,
    exits: List<OneExit>,
    offline: Boolean,
    accountExpired: Boolean,
) {
    val vpn = status.vpnStatus
    val title =
        when {
            accountExpired -> "Your account has expired"
            vpn.connecting != null -> {
                val city = vpn.connecting.tunnelArgs.exit.city
                val cityName = city?.let { c ->
                    exits.find { it.cityCode == c.cityCode && it.countryCode == c.countryCode }?.cityName
                }
                if (vpn.connecting.reconnecting) "Reconnecting to Obscura" else "Connecting to ${cityName ?: "Obscura"}"
            }
            vpn.connected != null -> "Connected to Obscura"
            offline -> "Disconnected"
            status.account == null -> "Validating Account..."
            else -> "Not connected to Obscura"
        }
    val subtitle =
        when {
            vpn.connected != null -> "Enjoy the free and open internet!"
            accountExpired -> "Top-up or subscribe to continue using Obscura."
            vpn.connecting != null -> "Please wait a moment..."
            offline -> "Connect to the internet to enable Obscura"
            status.account == null -> ""
            else -> "Connect to enjoy seamless privacy protection"
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalExtraColors.current.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PrimaryActionSection(
    visual: ConnectionVisualState,
    actions: ConnectionActions,
    onCheckConnection: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!visual.isConnected && visual.accountExpired) {
            ObscuraGradientButton(
                text = "Manage Account",
                onClick = actions.onOpenAccount,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        } else {
            val busy = visual.isConnecting || visual.isConnected
            val label =
                when {
                    visual.isConnecting -> "Cancel"
                    visual.isConnected -> "Disconnect"
                    else -> "Connect"
                }
            ObscuraGradientButton(
                text = label,
                onClick = if (busy) actions.onDisconnect else actions.onConnect,
                enabled = busy || !visual.offline,
                isDanger = busy,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
        }
        if (visual.isConnected) {
            TextButton(onClick = onCheckConnection) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Check my Connection",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(AppIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ConnectionNotice(
    params: ConnectionUiParams,
    actions: ConnectionActions,
    onOpenVpnSettings: () -> Unit,
) {
    val err = params.startError ?: params.status.vpnStatus.connecting?.connectError ?: return
    val (message, fixLabel, fixAction) = resolveErrorNotice(err, params.status, actions, onOpenVpnSettings)
    NoticeBanner(
        text = message,
        kind = NoticeKind.ERROR,
        actionLabel = fixLabel,
        onAction = fixAction,
    )
}

private fun resolveErrorNotice(
    code: String,
    status: CoreStatus,
    actions: ConnectionActions,
    onOpenVpnSettings: () -> Unit,
): Triple<String, String?, (() -> Unit)?> {
    val mode = status.tunnelArgs.mode
    return when (code) {
        "udpHandshakeFailed" ->
            Triple(
                "Speed mode got no answer from the exit server. " +
                    "This network may block WireGuard®. Stealth works on most networks.",
                "Switch to Stealth",
                { actions.onSwitchMode(TunnelMode.QUIC) },
            )
        "noRelay" ->
            if (status.tunnelArgs.relay != RelaySelector.Any) {
                Triple("Cannot reach the chosen entry relay.", "Use fastest relay", actions.onUseFastestRelay)
            } else if (mode != TunnelMode.TCP_TLS) {
                Triple(
                    "Unable to connect to relay servers. Try TCP/TLS on networks that block UDP.",
                    "Use TCP/TLS",
                    { actions.onSwitchMode(TunnelMode.TCP_TLS) },
                )
            } else {
                Triple("Unable to connect to relay servers.", null, null)
            }
        "accountExpired" -> Triple("Your account has expired.", "Manage Account", actions.onOpenAccount)
        "errorOtherAppAlwaysOn",
        "errorLegacyAlwaysOn" ->
            Triple(
                "Another VPN is set to Always-On in Android settings. Turn it off there to use Obscura VPN.",
                "Open VPN settings",
                onOpenVpnSettings,
            )
        "errorPermissionNotGranted" ->
            Triple("Permission must be granted to run as a VPN on your device.", "Connect", actions.onConnect)
        "noInternet" -> Triple("Not connected to the internet.", null, null)
        "apiUnreachable" -> Triple("Unable to connect to the Obscura API.", null, null)
        "apiRateLimitExceeded" -> Triple("API rate-limit exceeded. Please try again later.", null, null)
        "noSlotsLeft" ->
            Triple("All 5 device slots are in use. Disconnect another device or manage configs.", null, null)
        "invalidAccountId" -> Triple("Account number invalid.", "Manage Account", actions.onOpenAccount)
        "noLongerSupported" -> Triple("This client is too old; update to connect.", null, null)
        else -> Triple("Connection issue ($code). Retrying...", null, null)
    }
}
