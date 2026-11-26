package net.obscura.vpnclientapp.services

import java.net.InetAddress

data class Cidr(val address: String, val prefix: Int)

sealed interface AppScope {
    data class AllExcept(val packages: List<String>) : AppScope

    data class Only(val packages: List<String>) : AppScope
}

data class TunPlan(
    val mtu: Int,
    val addresses: List<Cidr>,
    val routes: List<Cidr>,
    val excludedRoutes: List<Cidr>,
    val dnsServers: List<String>,
    val apps: AppScope,
) {
    companion object {
        private const val IPV4_HOST_PREFIX = 32
        private const val IPV6_HOST_PREFIX = 128
        private val IP_LITERAL = Regex("[0-9.]+|[0-9a-fA-F:.]*:[0-9a-fA-F:.]*")

        fun of(
            config: OsNetworkConfig,
            settings: AndroidVpnSettings,
            ownPackage: String,
            isUsableDns: (String) -> Boolean,
            isInstalled: (String) -> Boolean,
        ): TunPlan {
            val routes = config.routes.map { Cidr(it.address, it.prefix) }
            val dns = dnsServers(config, settings, isUsableDns)
            val excludedRoutes = settings.excludedCidrs.mapNotNull(::parseExcludedCidr).distinct()
            val plan =
                TunPlan(
                    mtu = config.mtu,
                    addresses =
                        listOf(
                            cidr(config.ipv4, IPV4_HOST_PREFIX),
                            cidr(config.ipv6, IPV6_HOST_PREFIX),
                        ),
                    routes = routes + dnsHostRoutes(dns, routes),
                    excludedRoutes = excludedRoutes,
                    dnsServers = dns,
                    apps = appScope(settings, ownPackage, isInstalled),
                )
            require(routes.isNotEmpty()) { "no routes" }
            require(plan.dnsServers.isNotEmpty() || config.useSystemDns) { "no usable DNS server" }
            return plan
        }

        fun parseExcludedCidr(raw: String): Cidr? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split('/', limit = 2)
            val addrBytes = ipLiteral(parts[0]) ?: return null
            val maxPrefix = addrBytes.size * Byte.SIZE_BITS
            val prefix =
                if (parts.size == 1) {
                    maxPrefix
                } else {
                    parts[1].toIntOrNull()?.takeIf { it in 1..maxPrefix } ?: return null
                }
            val masked = maskNetworkBytes(addrBytes, prefix)
            val canonical = InetAddress.getByAddress(masked).hostAddress ?: return null
            return Cidr(canonical, prefix)
        }

        private fun maskNetworkBytes(bytes: ByteArray, prefix: Int): ByteArray {
            val out = bytes.copyOf()
            var remaining = prefix
            for (i in out.indices) {
                if (remaining <= 0) {
                    out[i] = 0
                } else if (remaining < Byte.SIZE_BITS) {
                    val mask = (0xFF shl (Byte.SIZE_BITS - remaining)) and 0xFF
                    out[i] = (out[i].toInt() and mask).toByte()
                    remaining = 0
                } else {
                    remaining -= Byte.SIZE_BITS
                }
            }
            return out
        }

        private fun dnsServers(
            config: OsNetworkConfig,
            settings: AndroidVpnSettings,
            isUsableDns: (String) -> Boolean,
        ): List<String> {
            if (config.useSystemDns) return emptyList()
            return settings.customDns.filter(isUsableDns).ifEmpty { config.dns.filter(isUsableDns) }
        }

        private fun dnsHostRoutes(dns: List<String>, routes: List<Cidr>): List<Cidr> = dns.mapNotNull { server ->
            val address = ipLiteral(server) ?: return@mapNotNull null
            val covered = routes.any { route ->
                ipLiteral(route.address)?.let { covers(it, route.prefix, address) } == true
            }
            if (covered) null else Cidr(server, address.size * Byte.SIZE_BITS)
        }

        private fun ipLiteral(text: String): ByteArray? {
            if (!IP_LITERAL.matches(text) || (':' !in text && text.count { it == '.' } != 3)) return null
            return runCatching { InetAddress.getByName(text).address }.getOrNull()
        }

        private fun covers(network: ByteArray, prefix: Int, address: ByteArray): Boolean {
            if (network.size != address.size) return false
            var bits = prefix
            for (i in network.indices) {
                if (bits <= 0) return true
                val mask = (0xFF shl (Byte.SIZE_BITS - bits.coerceAtMost(Byte.SIZE_BITS))) and 0xFF
                if ((network[i].toInt() and mask) != (address[i].toInt() and mask)) return false
                bits -= Byte.SIZE_BITS
            }
            return true
        }

        private fun appScope(
            settings: AndroidVpnSettings,
            ownPackage: String,
            isInstalled: (String) -> Boolean,
        ): AppScope {
            val listed = (settings.splitApps - ownPackage).sorted()
            val installed = listed.filter(isInstalled)
            return when (settings.splitMode) {
                SplitMode.Off -> AppScope.AllExcept(listOf(ownPackage))
                SplitMode.Exclude -> AppScope.AllExcept(listOf(ownPackage) + installed)
                SplitMode.Include ->
                    if (installed.isEmpty()) AppScope.AllExcept(listOf(ownPackage)) else AppScope.Only(listed)
            }
        }

        private fun cidr(value: String, defaultPrefix: Int): Cidr {
            val parts = value.split('/', limit = 2)
            return Cidr(parts[0], parts.getOrNull(1)?.toInt() ?: defaultPrefix)
        }
    }
}
