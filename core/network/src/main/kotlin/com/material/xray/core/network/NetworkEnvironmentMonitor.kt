package com.material.xray.core.network

import com.material.xray.core.model.NetworkEnvironment
import kotlinx.coroutines.flow.Flow

interface NetworkEnvironmentMonitor {
    val environments: Flow<NetworkEnvironment>
    fun refresh()
}
