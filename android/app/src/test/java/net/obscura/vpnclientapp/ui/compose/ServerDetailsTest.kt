package net.obscura.vpnclientapp.ui.compose

import net.obscura.vpnclientapp.client.CoreVpnStatus
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay
import net.obscura.vpnclientapp.client.TunnelMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDetailsTest {
    private val exit =
        OneExit(
            id = "mullvad-br-sao-wg-202",
            countryCode = "br",
            cityCode = "sao",
            cityName = "Sao Paulo",
            providerId = "br-sao-wg-202",
            providerUrl = "https://mullvad.net/servers/br-sao-wg-202",
            providerName = "Mullvad VPN",
            providerHomepageUrl = "https://mullvad.net/servers",
            datacenterId = 3_030_133_085L,
            tier = 255,
        )
    private val relay =
        OneRelay(
            id = "GRU-001",
            countryCode = "br",
            cityCode = "sao",
            cityName = "São Paulo",
            ipV4 = "79.127.137.167",
            ports = listOf(53, 443, 50000),
        )

    @Test
    fun factsLinesAsInOmbra() {
        assertEquals("Mullvad VPN · Data centre 3030133085 · Tier 255", exitFacts(exit))
        assertEquals("São Paulo · 79.127.137.167", relayFacts(relay))
        assertEquals("", exitFacts(exit.copy(providerName = "", datacenterId = null, tier = null)))
    }

    @Test
    fun serverInfoListsExitEntryAndKeys() {
        val connected =
            CoreVpnStatus.ConnectedInfo(
                exit = exit,
                relay = relay,
                clientPublicKey = "client=",
                exitPublicKey = "exit=",
                transport = TunnelMode.QUIC,
            )
        val rows = serverInfoRows(connected).associateBy { it.label }
        assertEquals("br-sao-wg-202", rows.getValue("Exit server").value)
        assertEquals("https://mullvad.net/servers/br-sao-wg-202", rows.getValue("Exit server").link)
        assertEquals("79.127.137.167 · ports 53, 443, 50000", rows.getValue("Entry relay address").value)
        assertEquals("exit=", rows.getValue("Exit public key").value)
        assertEquals("client=", rows.getValue("Your public key").value)
        assertEquals(true, rows.getValue("Entry relay").value.startsWith("GRU-001 (São Paulo, "))
    }
}
