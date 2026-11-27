package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One explained term: a name and what it does. */
data class LearnPoint(val term: String, val text: String)

/** A short explanation sheet, as Proton's "Learn more" sheets: a title, an introduction and the terms. */
enum class LearnMore(val title: String, val intro: String, val points: List<LearnPoint>) {
    PROTOCOL(
        "Protocols",
        "Every protocol carries the same encrypted WireGuard® tunnel. They differ in how it looks to the network.",
        listOf(
            LearnPoint(
                "Stealth",
                "WireGuard® inside QUIC, the protocol behind HTTP/3, on port 443. It looks like ordinary web traffic " +
                    "and gets through most firewalls. The default.",
            ),
            LearnPoint(
                "Speed",
                "Plain WireGuard® over UDP. The least overhead, but easy to recognise, and some networks block it.",
            ),
            LearnPoint(
                "TCP/TLS",
                "The tunnel wrapped in TLS over TCP, like an HTTPS connection. The slowest, but it works where UDP " +
                    "is blocked altogether.",
            ),
            LearnPoint(
                "Switch automatically",
                "When a connection fails, Obscura moves from an unreachable chosen entry relay to the fastest one, " +
                    "from Speed to Stealth, and from Stealth to TCP/TLS, and tells you what it changed.",
            ),
        ),
    ),
    DNS(
        "DNS",
        "DNS turns names like example.com into addresses. Whoever answers sees every name you look up.",
        listOf(
            LearnPoint(
                "Obscura DNS",
                "Lookups go through the tunnel, so your network cannot see them. Blocking lists apply here.",
            ),
            LearnPoint(
                "Network's DNS",
                "Lookups go to your network's resolver outside the tunnel. Your provider sees them, and blocking " +
                    "and custom servers do not apply.",
            ),
            LearnPoint("Custom servers", "Lookups go, through the tunnel, to the addresses you add instead."),
            LearnPoint(
                "Private DNS",
                "Android's own Private DNS setting overrides all of this. Set it to Automatic or Off for these " +
                    "choices to apply.",
            ),
        ),
    ),
    SPLIT(
        "Split tunnelling",
        "Choose what bypasses Obscura. Anything outside the tunnel uses your normal connection and your real IP.",
        listOf(
            LearnPoint("All apps", "Everything goes through Obscura."),
            LearnPoint("Exclude", "The apps you choose use your normal connection; the rest use Obscura."),
            LearnPoint("Only", "Only the apps you choose use Obscura."),
            LearnPoint(
                "Excluded IPs and subnets",
                "Addresses every app reaches directly, such as a home server or a work network.",
            ),
            LearnPoint(
                "Lockdown",
                "With Android's \"Block connections without VPN\" on, apps and addresses outside the tunnel get no " +
                    "network at all.",
            ),
        ),
    ),
    KEY(
        "WireGuard® key",
        "This device has its own WireGuard® key pair. The private key never leaves the device; Obscura's servers " +
            "only see the public key.",
        listOf(
            LearnPoint(
                "Why rotate",
                "A new key from time to time makes it harder to link your sessions to each other.",
            ),
            LearnPoint("Every connection", "The most private choice; each connect takes a little longer."),
            LearnPoint("Rotate key now", "Replaces the key at once. While connected, the tunnel reconnects with it."),
        ),
    ),
}

/** A group caption with an ⓘ that opens [topic]'s sheet. */
@Composable
fun LearnMoreLabel(text: String, topic: LearnMore) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GroupLabel(text, modifier = Modifier.weight(1f))
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                AppIcons.Info,
                contentDescription = "About ${topic.title}",
                tint = LocalExtraColors.current.muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    if (open) LearnMoreSheet(topic) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearnMoreSheet(topic: LearnMore, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(topic.intro, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.muted)
            topic.points.forEach { point ->
                Column {
                    Text(point.term, style = MaterialTheme.typography.titleSmall, color = ObscuraPalette.Orange)
                    Text(point.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
