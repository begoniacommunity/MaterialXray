package com.material.xray.feature.routing.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.material.xray.core.data.repository.NetworkServerRepository
import com.material.xray.core.data.repository.ServerRepository
import com.material.xray.core.data.repository.SettingsRepository
import com.material.xray.core.database.dao.SubscriptionDao
import com.material.xray.core.model.NetworkEnvironment
import com.material.xray.core.model.NetworkKind
import com.material.xray.core.model.NetworkServerPolicy
import com.material.xray.core.network.NetworkEnvironmentMonitor
import com.material.xray.core.runtime.LocationPermissionSettings
import com.material.xray.core.runtime.NetworkServerManager
import com.material.xray.core.runtime.NetworkServerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

internal data class NetworkServerChoice(val id: Long, val name: String, val subscription: String, val subscriptionId: Long)

@KoinViewModel
class NetworkRoutingViewModel(
    private val repository: NetworkServerRepository,
    servers: ServerRepository,
    subscriptions: SubscriptionDao,
    private val monitor: NetworkEnvironmentMonitor,
    manager: NetworkServerManager,
    settings: SettingsRepository,
    private val permissionSettings: LocationPermissionSettings,
) : ViewModel() {
    val policy = repository.policy.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkServerPolicy())
    val network = monitor.environments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetworkEnvironment())
    val status = manager.status
    val activeOverrideSsid = combine(repository.policy, monitor.environments, settings.lastServerId, manager.status) { policy, network, selected, status ->
        if (!policy.enabled || network.kind != NetworkKind.Wifi || status != NetworkServerStatus.Applied) {
            null
        } else {
            network.ssid?.takeIf { policy.ssidServers[it] == selected }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val choices = combine(servers.observeAll(), subscriptions.observeAll()) { items, groups ->
        val names = groups.associate { it.id to it.name }
        items.map { NetworkServerChoice(it.id, it.name, names[it.subscriptionId].orEmpty(), it.subscriptionId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshNetwork() = monitor.refresh()
    fun openLocationPermissions() {
        viewModelScope.launch { permissionSettings.open() }
    }
    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setWifi(id: Long?) = update { it.copy(wifiServerId = id) }
    fun setMobile(id: Long?) = update { it.copy(mobileServerId = id) }
    fun setSsid(ssid: String, id: Long) = update { it.copy(ssidServers = it.ssidServers + (ssid to id)) }
    fun removeSsid(ssid: String) = update { it.copy(ssidServers = it.ssidServers - ssid) }
    private fun update(change: (NetworkServerPolicy) -> NetworkServerPolicy) {
        viewModelScope.launch { repository.update(change) }
    }
}
