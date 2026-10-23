package com.material.xray.core.network

import com.material.xray.core.model.NetworkKind

data class PhysicalNetworkCandidate(
    val id: Long,
    val kind: NetworkKind,
    val isVpn: Boolean,
    val hasInternet: Boolean,
    val validated: Boolean,
)

fun List<PhysicalNetworkCandidate>.preferredPhysicalNetwork(defaultId: Long?): PhysicalNetworkCandidate? {
    val physical = filter { !it.isVpn && it.hasInternet }
    return physical.firstOrNull { it.id == defaultId } ?: physical.maxByOrNull {
        (if (it.validated) 10 else 0) + when (it.kind) {
            NetworkKind.Wifi -> 2
            NetworkKind.Other -> 1
            else -> 0
        }
    }
}
