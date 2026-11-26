package net.obscura.vpnclientapp.services

import android.net.LinkProperties
import java.net.NetworkInterface
import java.net.SocketException
import net.obscura.lib.util.Logger

private val log = Logger("NetworkFacts")

private const val CLAT_INTERFACE_PREFIX = "v4-"

data class RustNetwork(
    val name: String,
    val index: Int,
    val networkHandle: Long,
    val mtuHint: Int,
)

fun clatAwareMtu(interfaceMtu: Int, clatMtu: Int): Int =
    listOf(interfaceMtu, clatMtu).filter { it > 0 }.minOrNull() ?: 0

fun readRustNetwork(networkHandle: Long, linkProperties: LinkProperties): RustNetwork? {
    val name = linkProperties.interfaceName ?: return null
    val interfaces = interfacesNamed(name)
    val base = interfaces[name]
    val index = base?.index ?: -1
    if (index < 1) return null
    val clat = interfaces[CLAT_INTERFACE_PREFIX + name]
    val baseMtu = mtuOf(base).takeIf { it > 0 } ?: linkProperties.mtu
    val mtuHint = clatAwareMtu(baseMtu, clat?.let(::mtuOf) ?: 0)
    return RustNetwork(name, index, networkHandle, mtuHint)
}

private fun interfacesNamed(name: String): Map<String, NetworkInterface> =
    try {
        val wanted = setOf(name, CLAT_INTERFACE_PREFIX + name)
        NetworkInterface.getNetworkInterfaces()
            ?.asSequence()
            .orEmpty()
            .filter { it.name in wanted }
            .associateBy { it.name }
    } catch (_: SocketException) {
        log.warn("cannot list network interfaces")
        emptyMap()
    }

private fun mtuOf(networkInterface: NetworkInterface?): Int =
    try {
        networkInterface?.mtu ?: 0
    } catch (_: SocketException) {
        0
    }
