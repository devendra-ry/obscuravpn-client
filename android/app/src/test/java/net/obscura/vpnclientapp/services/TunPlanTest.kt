package net.obscura.vpnclientapp.services

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
