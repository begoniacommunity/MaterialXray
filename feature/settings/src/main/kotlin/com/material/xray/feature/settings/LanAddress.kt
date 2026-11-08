package com.material.xray.feature.settings

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * The address other devices on the local network reach this one at: the private IPv4 address of
 * Wi-Fi, Ethernet, or an interface this device shares its connection over, or null when there is none.
 */
internal fun deviceLanAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
        .map { it.name to it.inetAddresses.toList() }
}.getOrNull()?.let(::pickLanAddress)

/**
 * Picks by interface name, because a mobile data interface often holds a private address too,
 * one that nothing on the local network can reach.
 */
internal fun pickLanAddress(interfaces: List<Pair<String, List<InetAddress>>>): String? = interfaces
    .mapNotNull { (name, addresses) ->
        val rank = LAN_INTERFACE_PREFIXES.indexOfFirst { name.startsWith(it) }.takeIf { it >= 0 }
        val address = addresses.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        if (rank == null || address == null) null else rank to address.hostAddress
    }
    .minByOrNull { (rank, _) -> rank }
    ?.second

// Wi-Fi first, then hotspot, Ethernet, USB and Bluetooth tethering.
private val LAN_INTERFACE_PREFIXES = listOf("wlan", "swlan", "ap", "softap", "eth", "ncm", "rndis", "usb", "bt-pan")
