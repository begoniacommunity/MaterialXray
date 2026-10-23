package com.material.xray.core.model

import kotlinx.serialization.Serializable

/** Last selected server before leaving each supported network type. */
@Serializable
data class NetworkServerHistory(val wifiServerId: Long? = null, val mobileServerId: Long? = null) {
    fun serverFor(policy: NetworkServerPolicy, network: NetworkEnvironment): Long? = if (policy.enabled) policy.serverFor(network) ?: serverFor(network) else null

    private fun serverFor(network: NetworkEnvironment): Long? = when (network.kind) {
        NetworkKind.Wifi -> wifiServerId
        NetworkKind.Mobile -> mobileServerId
        NetworkKind.Other, NetworkKind.Offline -> null
    }

    fun remember(kind: NetworkKind, serverId: Long): NetworkServerHistory {
        if (serverId < 0) return this
        return when (kind) {
            NetworkKind.Wifi -> copy(wifiServerId = serverId)
            NetworkKind.Mobile -> copy(mobileServerId = serverId)
            NetworkKind.Other, NetworkKind.Offline -> this
        }
    }

    fun remap(replacements: Map<Long, Long>, available: Set<Long>): NetworkServerHistory {
        fun target(id: Long?): Long? = id?.let { (replacements[it] ?: it).takeIf(available::contains) }
        return copy(wifiServerId = target(wifiServerId), mobileServerId = target(mobileServerId))
    }
}
