package net.obscura.vpnclientapp.services

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunPlanTest {
    @Test
    fun parseExcludedCidrNormalizesHostAndSubnet() {
        assertEquals(Cidr("192.168.1.0", 24), TunPlan.parseExcludedCidr("192.168.1.42/24"))
        assertEquals(Cidr("10.20.30.40", 32), TunPlan.parseExcludedCidr("10.20.30.40"))

        val ipv6 = TunPlan.parseExcludedCidr("fd00::1234/64")
        assertNotNull(ipv6)
        assertEquals(64, ipv6?.prefix)
        assertEquals(InetAddress.getByName("fd00::"), InetAddress.getByName(ipv6?.address))

        assertNull(TunPlan.parseExcludedCidr("0.0.0.0/0"))
        assertNull(TunPlan.parseExcludedCidr("not-an-ip/24"))
        assertNull(TunPlan.parseExcludedCidr("192.168.1.1/33"))
    }

    private fun config(useSystemDns: Boolean) =
        OsNetworkConfig(
            dns = listOf("10.64.0.99"),
            ipv4 = "10.75.0.2",
            ipv6 = "fc00:bbbb:bbbb:bb01::1:2",
            // Local network access on: the LAN (and with it the Wi-Fi router) is not in the tunnel routes.
            routes = listOf(Route("0.0.0.0", 1), Route("128.0.0.0", 1), Route("::", 0)),
            mtu = 1280,
            useSystemDns = useSystemDns,
        )

    private fun plan(
        useSystemDns: Boolean,
        networkDns: List<String>,
        settings: AndroidVpnSettings = AndroidVpnSettings(),
    ) =
        TunPlan.of(
            config = config(useSystemDns),
            settings = settings,
            device =
                DeviceFacts(
                    ownPackage = "net.obscura.vpnclientapp",
                    networkDns = networkDns,
                    isUsableDns = { !it.startsWith("127.") },
                    isInstalled = { true },
                ),
        )

    @Test
    fun networkDnsServersAreUsedAndLeaveTheTunnel() {
        val plan = plan(useSystemDns = true, networkDns = listOf("192.168.1.1", "fe80::1%wlan0", "192.168.1.1"))
        assertEquals(listOf("192.168.1.1", "fe80::1"), plan.dnsServers)
        assertTrue(Cidr("192.168.1.1", 32) in plan.excludedRoutes)
        assertEquals(1, plan.excludedRoutes.count { it.prefix == 128 })
        // No host route that would pull the router back into the tunnel.
        assertEquals(config(true).routes.map { Cidr(it.address, it.prefix) }, plan.routes)
    }

    @Test
    fun networkDnsIgnoresCustomDnsAndTheExitResolver() {
        val plan = plan(true, listOf("8.8.8.8"), AndroidVpnSettings(customDns = listOf("1.1.1.1")))
        assertEquals(listOf("8.8.8.8"), plan.dnsServers)
        assertTrue(Cidr("1.1.1.1", 32) !in plan.excludedRoutes)
    }

    @Test
    fun networkWithoutDnsServersStillBuildsATun() {
        val plan = plan(useSystemDns = true, networkDns = emptyList())
        assertTrue(plan.dnsServers.isEmpty())
        assertTrue(plan.excludedRoutes.isEmpty())
    }

    @Test
    fun tunnelDnsKeepsNetworkResolversInTheTunnel() {
        val plan = plan(useSystemDns = false, networkDns = listOf("192.168.1.1"))
        assertEquals(listOf("10.64.0.99"), plan.dnsServers)
        assertTrue(plan.excludedRoutes.isEmpty())
    }
}
