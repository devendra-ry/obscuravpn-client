package net.obscura.vpnclientapp.ui.compose

import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.CoreVpnStatus
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay

/** What is known about an exit, as Ombra lists it under the server: provider, data centre and tier. */
fun exitFacts(exit: OneExit): String =
    listOfNotNull(
            exit.providerName.ifBlank { null },
            exit.datacenterId?.let { "Data centre $it" },
            exit.tier?.let { "Tier $it" },
        )
        .joinToString(" · ")

/** An entry relay as Ombra lists it: its city and address. */
fun relayFacts(relay: OneRelay): String =
    listOf(relay.cityName, relay.ipV4).filter { it.isNotBlank() }.joinToString(" · ")

/** One line of the status card's server info; [link] makes the value open a page. */
data class InfoRow(val label: String, val value: String, val mono: Boolean = false, val link: String? = null)

/**
 * The status card's server info, as in Obscura (exit server, provider, exit key) with Ombra's additions (entry relay,
 * its address, the protocol and this device's key).
 */
fun serverInfoRows(c: CoreVpnStatus.ConnectedInfo): List<InfoRow> {
    val exit = c.exit
    val relay = c.relay
    return listOfNotNull(
        InfoRow(
            "Exit server",
            exit.providerId.ifBlank { exit.id },
            mono = true,
            link = exit.providerUrl.ifBlank { null },
        ),
        exit.providerName
            .ifBlank { null }
            ?.let { InfoRow("Provider", it, link = exit.providerHomepageUrl.ifBlank { null }) },
        relay?.let {
            InfoRow("Entry relay", "${it.id} (${it.cityName}, ${AccountIdUtils.countryDisplayName(it.countryCode)})")
        },
        relay
            ?.takeIf { it.ipV4.isNotBlank() }
            ?.let { r ->
                val ports =
                    if (r.ports.isEmpty()) ""
                    else " · ${if (r.ports.size == 1) "port" else "ports"} ${r.ports.joinToString(", ")}"
                InfoRow("Entry relay address", r.ipV4 + ports, mono = true)
            },
        InfoRow("Protocol", transportName(c.transport)),
        c.exitPublicKey.ifBlank { null }?.let { InfoRow("Exit public key", it, mono = true) },
        c.clientPublicKey.ifBlank { null }?.let { InfoRow("Your public key", it, mono = true) },
    )
}
