package com.k1adili.walkietalkie.engine

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

class NetInfo(val localIps: List<String>, val broadcasts: List<InetAddress>) {
    companion object {
        val EMPTY = NetInfo(emptyList(), emptyList())
    }
}

/**
 * Finds LAN-like interfaces (Wi-Fi, hotspot/AP, Wi-Fi Direct). Mobile data and VPN
 * interfaces are point-to-point and have no broadcast address, so they are skipped.
 */
fun scanNetwork(): NetInfo {
    val ips = ArrayList<String>()
    val broadcasts = ArrayList<InetAddress>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return NetInfo.EMPTY
        for (ni in interfaces.toList()) {
            val usable = try {
                ni.isUp && !ni.isLoopback
            } catch (e: SocketException) {
                false
            }
            if (!usable) continue
            for (ia in ni.interfaceAddresses) {
                val addr = ia.address
                val bc = ia.broadcast
                if (addr is Inet4Address && bc != null && !addr.isLoopbackAddress) {
                    addr.hostAddress?.let { ips.add(it) }
                    broadcasts.add(bc)
                }
            }
        }
    } catch (e: SocketException) {
        // Treat as "no network"; the next scan will try again.
    }
    return NetInfo(ips, broadcasts)
}
