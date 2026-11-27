package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.CoreVpnStatus
import net.obscura.vpnclientapp.ui.RecentExit

private const val RECENTS_SHOWN = 3

/** The last few cities, as Proton lists them under its connect card: one tap connects (or switches) to one. */
@Composable
fun RecentsGroup(recents: List<RecentExit>, vpn: CoreVpnStatus, onConnect: (RecentExit) -> Unit) {
    val current = vpn.connected?.exit
    val shown =
        recents
            .filterNot { current != null && it.countryCode == current.countryCode && it.cityCode == current.cityCode }
            .take(RECENTS_SHOWN)
    if (shown.isEmpty()) return
    val verb = if (vpn.disconnected == null) "Switch" else "Connect"
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupLabel("Recent")
        RowGroup {
            shown.forEachIndexed { i, recent ->
                if (i > 0) GroupDivider(indent = 64.dp)
                GroupRow(
                    title = recent.cityName,
                    supporting = { SupportingText(AccountIdUtils.countryDisplayName(recent.countryCode)) },
                    onClick = { onConnect(recent) },
                    look = RowLook(leading = { FlagBadge(recent.countryCode) }),
                ) {
                    Text(
                        verb,
                        style = MaterialTheme.typography.labelLarge,
                        color = ObscuraPalette.Orange,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
            }
        }
    }
}
