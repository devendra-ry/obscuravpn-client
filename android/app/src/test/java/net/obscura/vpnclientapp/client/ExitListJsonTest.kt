package net.obscura.vpnclientapp.client

import org.junit.Assert.assertEquals
import org.junit.Test

class ExitListJsonTest {
    @Test
    fun exitListDecodesFromRustsShape() {
        // `getExitList` answers `CachedValue<ExitList>`: the exits sit under `value.exits`.
        val raw =
            """{"version":"VZ0=","last_updated":1795768000,"value":{"exits":[""" +
                """{"id":"mullvad-br-sao-wg-202","country_code":"br","city_code":"sao","city_name":"Sao Paulo",""" +
                """"provider_name":"Mullvad VPN","datacenter_id":3030133085,"tier":255}]}}"""
        val list = jsonConfig.decodeFromString<CachedExitList>(raw)
        assertEquals("VZ0=", list.version)
        assertEquals(listOf("mullvad-br-sao-wg-202"), list.value.exits.map { it.id })
        assertEquals("Mullvad VPN", list.value.exits.single().providerName)
    }
}
