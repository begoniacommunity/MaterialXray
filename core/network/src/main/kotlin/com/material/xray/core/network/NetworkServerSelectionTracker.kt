package com.material.xray.core.network

import com.material.xray.core.model.ConnectionState
import com.material.xray.core.model.NetworkEnvironment
import com.material.xray.core.model.NetworkKind
import com.material.xray.core.model.NetworkServerPolicy

/** A successful network decision stays settled, so a later manual pick is not immediately undone. */
class NetworkServerSelectionTracker {
    private var evaluated: Pair<NetworkServerPolicy, NetworkEnvironment>? = null
    private var activeKind: NetworkKind? = null

    /** Capture the outgoing selection before automatic selection can replace it. */
    fun departingNetwork(enabled: Boolean, network: NetworkEnvironment): NetworkKind? {
        val previous = activeKind
        activeKind = network.kind.takeIf { enabled }
        return previous?.takeIf { enabled && it != network.kind && it in listOf(NetworkKind.Wifi, NetworkKind.Mobile) }
    }

    fun isSettled(policy: NetworkServerPolicy, network: NetworkEnvironment): Boolean = evaluated == (policy to network)

    fun settle(policy: NetworkServerPolicy, network: NetworkEnvironment) {
        evaluated = policy to network
    }
}

fun ConnectionState.isNetworkSwitchBusy(): Boolean = this == ConnectionState.Connecting ||
    this == ConnectionState.ApplyingRoutingChanges ||
    this == ConnectionState.Disconnecting ||
    this == ConnectionState.UpdatingRoutingData
