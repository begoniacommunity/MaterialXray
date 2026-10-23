package com.material.xray.core.runtime

import android.content.Context
import com.material.xray.core.common.connection.ConnectionStateCoordinator
import com.material.xray.core.common.log.LogBuffer
import com.material.xray.core.common.log.LogSource
import com.material.xray.core.data.repository.NetworkServerRepository
import com.material.xray.core.data.repository.ProviderRoutingActiveUpdate
import com.material.xray.core.data.repository.ProviderRoutingCoordinator
import com.material.xray.core.data.repository.ServerRepository
import com.material.xray.core.data.repository.ServerSelectionCoordinator
import com.material.xray.core.data.repository.SettingsRepository
import com.material.xray.core.model.ConnectionState
import com.material.xray.core.model.NetworkEnvironment
import com.material.xray.core.model.NetworkServerPolicy
import com.material.xray.core.model.ServerConfig
import com.material.xray.core.network.NetworkEnvironmentMonitor
import com.material.xray.core.network.NetworkServerSelectionTracker
import com.material.xray.core.network.isNetworkSwitchBusy
import com.material.xray.core.xray.ActiveConfigOverrideStore
import com.material.xray.core.xray.XrayActiveCore
import com.material.xray.service.XrayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Singleton

enum class NetworkServerStatus { Idle, Applied, EditedConfig, IncompatibleCore, UnavailableServer }

@Singleton
class NetworkServerManager(
    private val context: Context,
    private val networks: NetworkEnvironmentMonitor,
    private val policies: NetworkServerRepository,
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    private val selectionLock: ServerSelectionCoordinator,
    private val providerRouting: ProviderRoutingCoordinator,
    private val overrideStore: ActiveConfigOverrideStore,
    private val connection: ConnectionStateCoordinator,
    private val routingChanges: RoutingChangeManager,
    private val activeCore: XrayActiveCore,
    private val log: LogBuffer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableStatus = MutableStateFlow(NetworkServerStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private var started = false

    @Volatile private var latestNetwork = NetworkEnvironment()
    private val decisions = NetworkServerSelectionTracker()

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch {
            combine(
                policies.policy,
                networks.environments.onEach { latestNetwork = it },
                connection.state,
                settings.lastServerId,
            ) { policy, network, state, selected -> Triple(policy, network, state) to selected }
                .debounce(400)
                .distinctUntilChanged()
                .collect { (snapshot, _) ->
                    val (policy, network, state) = snapshot
                    if (state.isNetworkSwitchBusy()) return@collect
                    try {
                        selectionLock.withSelectionLock {
                            if (latestNetwork != network || policies.policy.first() != policy) return@withSelectionLock
                            if (decisions.isSettled(policy, network)) {
                                if (policy.enabled) policies.rememberServer(network.kind, settings.lastServerId.first())
                                return@withSelectionLock
                            }
                            if (select(policy, network, reconfigure = true)) decisions.settle(policy, network)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        log.append(LogSource.APP, "Network server selection failed: ${error.javaClass.simpleName}")
                    }
                }
        }
    }

    /** Settle selection on cold tile/always-on starts before the service reads its config. */
    @OptIn(FlowPreview::class)
    suspend fun selectForConnection(): ServerConfig? = selectionLock.withSelectionLock {
        val policy = policies.policy.first()
        if (!policy.enabled) return@withSelectionLock null
        val network = networks.environments.distinctUntilChanged().debounce(400).first()
        if (!decisions.isSettled(policy, network) && select(policy, network, reconfigure = false)) decisions.settle(policy, network)
        servers.getById(settings.lastServerId.first())?.let { runCatching { servers.parseConfig(it) }.getOrNull() }
    }

    private suspend fun select(policy: NetworkServerPolicy, network: NetworkEnvironment, reconfigure: Boolean): Boolean {
        decisions.departingNetwork(policy.enabled, network)?.let { kind ->
            policies.rememberServer(kind, settings.lastServerId.first())
        }
        val explicitTarget = policy.serverFor(network)
        val target = policies.history.first().serverFor(policy, network)
        if (target == null) {
            mutableStatus.value = NetworkServerStatus.Idle
            return true
        }
        if (settings.lastServerId.first() == target) {
            mutableStatus.value = NetworkServerStatus.Applied
            return true
        }
        if (overrideStore.exists()) {
            mutableStatus.value = NetworkServerStatus.EditedConfig
            return false
        }
        val config = servers.getById(target)?.let { runCatching { servers.parseConfig(it) }.getOrNull() }
        if (config == null) {
            // A removed historical server must not block an otherwise unassigned network.
            mutableStatus.value = if (explicitTarget == null) NetworkServerStatus.Idle else NetworkServerStatus.UnavailableServer
            return explicitTarget == null
        }
        val version = withContext(ioDispatcher) { activeCore.version() }
        if (version != null && config.subscriptionCoreRequirement?.accepts(version) == false) {
            mutableStatus.value = NetworkServerStatus.IncompatibleCore
            return false
        }
        if (policies.policy.first() != policy || reconfigure && latestNetwork != network) return false
        if (reconfigure && connection.state.value.isNetworkSwitchBusy()) return false
        settings.setLastServerId(target)
        providerRouting.refreshSelectedServer(ProviderRoutingActiveUpdate.DEFER)
        mutableStatus.value = NetworkServerStatus.Applied
        if (reconfigure && connection.state.value is ConnectionState.Connected) {
            routingChanges.clearPendingChanges()
            XrayService.switchServer(context)
        }
        log.append(LogSource.APP, "Selected server for ${network.kind.name.lowercase()} network")
        return true
    }
}
