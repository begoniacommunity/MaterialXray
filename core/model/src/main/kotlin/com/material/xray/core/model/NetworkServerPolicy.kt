package com.material.xray.core.model

import kotlinx.serialization.Serializable

enum class NetworkKind { Wifi, Mobile, Other, Offline }

data class NetworkEnvironment(val kind: NetworkKind = NetworkKind.Offline, val networkId: Long = 0, val ssid: String? = null)

@Serializable
data class NetworkServerPolicy(
    val enabled: Boolean = false,
    val wifiServerId: Long? = null,
    val mobileServerId: Long? = null,
    val ssidServers: Map<String, Long> = emptyMap(),
) {
    fun serverFor(network: NetworkEnvironment): Long? {
        if (!enabled) return null
        return when (network.kind) {
            NetworkKind.Wifi -> network.ssid?.let(ssidServers::get) ?: wifiServerId
            NetworkKind.Mobile -> mobileServerId
            NetworkKind.Other, NetworkKind.Offline -> null
        }
    }

    fun remap(replacements: Map<Long, Long>, available: Set<Long>): NetworkServerPolicy {
        fun target(id: Long?): Long? = id?.let { (replacements[it] ?: it).takeIf(available::contains) }
        return copy(
            wifiServerId = target(wifiServerId),
            mobileServerId = target(mobileServerId),
            ssidServers = ssidServers.mapNotNull { (ssid, id) -> target(id)?.let { ssid to it } }.toMap(),
        )
    }
}

/** Android quotes ordinary SSIDs. Spaces and letter case are part of the network name. */
fun normalizedWifiSsid(value: String?): String? = value
    ?.removeSurrounding("\"")
    ?.takeUnless { it.isEmpty() || it == "<unknown ssid>" || it == "0x" }
