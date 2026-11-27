package net.obscura.vpnclientapp.client

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectorJsonTest {
    @Test
    fun selectorsSendOnlyTheirOneField() {
        // Rust reads the selectors as externally tagged enums: one key, no `null` siblings.
        assertEquals(
            """{"exit":{"any":{}},"relay":{"any":{}},"mode":"quic","udpMtu":null}""",
            jsonConfig.encodeToString(TunnelArgs()),
        )
        assertEquals(
            """{"city":{"country_code":"de","city_code":"ber"}}""",
            jsonConfig.encodeToString(ExitSelector.forCity("de", "ber")),
        )
        assertEquals("""{"relay":{"id":"r1"}}""", jsonConfig.encodeToString(RelaySelector.forRelay("r1")))
    }

    @Test
    fun argsStoredByOlderVersionsRoundTripToTheCurrentForm() {
        // What `VpnRestoreStore` kept before the fix; the service round-trips it through `TunnelArgs`.
        val stored =
            """{"exit":{"any":{},"exit":null,"country":null,"city":null},""" +
                """"relay":{"any":{},"relay":null,"country":null,"city":null},"mode":"quic","udpMtu":null}"""
        assertEquals(
            """{"exit":{"any":{}},"relay":{"any":{}},"mode":"quic","udpMtu":null}""",
            jsonConfig.encodeToString(jsonConfig.decodeFromString<TunnelArgs>(stored)),
        )
    }

    @Test
    fun selectorsStillDecode() {
        val args = jsonConfig.decodeFromString<TunnelArgs>("""{"exit":{"country":{"country_code":"nl"}}}""")
        assertEquals(ExitSelector.forCountry("nl"), args.exit)
        assertEquals(RelaySelector.Any, args.relay)
    }
}
