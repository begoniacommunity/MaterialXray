package com.material.xray.feature.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.material.xray.core.common.connection.ConnectionStateCoordinator
import com.material.xray.core.common.connection.PendingRoutingChange
import com.material.xray.core.data.parser.ProfileRouting
import com.material.xray.core.data.parser.ProfileRoutingInspector
import com.material.xray.core.data.parser.ProfileRoutingRule
import com.material.xray.core.data.repository.ProviderGeoDataManager
import com.material.xray.core.data.repository.ProviderRoutingAvailability
import com.material.xray.core.data.repository.ServerRepository
import com.material.xray.core.data.repository.SettingsRepository
import com.material.xray.core.data.repository.SubscriptionRoutingRepository
import com.material.xray.core.data.repository.selectedProviderRoutingAvailability
import com.material.xray.core.data.repository.toSubscriptionRouting
import com.material.xray.core.database.dao.SubscriptionDao
import com.material.xray.core.model.ConnectionState
import com.material.xray.core.model.ProfileRoutingOverride
import com.material.xray.core.model.ProfileRoutingOverrideEngine
import com.material.xray.core.model.RoutingPolicyControl
import com.material.xray.core.model.RoutingRule
import com.material.xray.core.model.RoutingRuleCatalog
import com.material.xray.core.model.RoutingRuleScope
import com.material.xray.core.model.SubscriptionRouting
import com.material.xray.core.model.reorderedBy
import com.material.xray.core.runtime.RoutingChangeManager
import com.material.xray.core.xray.ProviderGeoDataNotice
import com.material.xray.core.xray.providerGeoDataNotice
import com.material.xray.core.xray.providerGeoDataUrls
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.KoinViewModel

internal data class RoutingSubscriptionSource(val id: Long, val name: String)

@KoinViewModel
class RoutingViewModel(
    private val settingsRepository: SettingsRepository,
    private val routingChangeManager: RoutingChangeManager,
    private val serverRepository: ServerRepository,
    private val subscriptionDao: SubscriptionDao,
    private val subscriptionRoutingRepository: SubscriptionRoutingRepository,
    private val providerGeoDataManager: ProviderGeoDataManager,
    connectionStateCoordinator: ConnectionStateCoordinator,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutationMutex = Mutex()

    private fun mutateRouting(block: suspend () -> Unit) {
        viewModelScope.launch { mutationMutex.withLock { block() } }
    }

    val rules: StateFlow<List<RoutingRule>> = settingsRepository.customRoutingRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val scopeOrder = settingsRepository.routingScopeOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutingRuleScope.entries)
    internal val profileRouting: StateFlow<ProfileRouting?> = combine(
        settingsRepository.lastServerId,
        serverRepository.observeAll(),
    ) { selectedServerId, servers ->
        val entity = servers.firstOrNull { it.id == selectedServerId } ?: return@combine null
        val config = runCatching { serverRepository.parseConfig(entity) }.getOrNull() ?: return@combine null
        ProfileRoutingInspector.inspect(config)?.copy(serverId = entity.id)
    }.flowOn(defaultDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val selectedProviderRouting: StateFlow<ProviderRoutingAvailability?> = combine(
        settingsRepository.lastServerId,
        serverRepository.observeAll(),
        subscriptionDao.observeAll(),
        ::selectedProviderRoutingAvailability,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null,
    )
    val routingPolicyControl: StateFlow<RoutingPolicyControl> = combine(
        settingsRepository.routingPolicyControl,
        selectedProviderRouting,
        profileRouting,
    ) { policy, provider, profile ->
        val providerRulesAvailable = provider?.xrayRoutingProvided == true || profile?.rules?.any { !it.orphaned } == true
        if (policy == RoutingPolicyControl.SubscriptionProvider && providerRulesAvailable) {
            RoutingPolicyControl.SubscriptionProvider
        } else {
            RoutingPolicyControl.User
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutingPolicyControl.User)
    val automaticRoutingProviderName: StateFlow<String?> = selectedProviderRouting
        .map { it?.providerName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val subscriptionRules: StateFlow<List<RoutingRule>> = settingsRepository.subscriptionRoutingRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    internal val subscriptionRoutingSourceId = settingsRepository.subscriptionRoutingState.map { it.subscriptionId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    internal val subscriptionRoutingSources = subscriptionDao.observeAll().map { subscriptions ->
        subscriptions.filter { it.toSubscriptionRouting() != null }.map { RoutingSubscriptionSource(it.id, it.name) }
    }.flowOn(defaultDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val providerGeoDataNotice: StateFlow<ProviderGeoDataNotice?> = combine(
        subscriptionRules,
        providerGeoDataManager.state,
        connectionStateCoordinator.state,
    ) { rules, geoDataState, connectionState ->
        providerGeoDataNotice(
            urls = rules.providerGeoDataUrls(),
            state = geoDataState,
            connected = connectionState is ConnectionState.Connected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onProviderGeoDataNoticeClick() {
        when (providerGeoDataNotice.value) {
            ProviderGeoDataNotice.CompatibilityMode -> providerGeoDataManager.refreshInBackground(force = true)
            ProviderGeoDataNotice.ReadyToApply ->
                routingChangeManager.requestActiveConnectionUpdate(PendingRoutingChange.XRAY_ROUTING)
            ProviderGeoDataNotice.Downloading, null -> Unit
        }
    }

    fun updateRule(rule: RoutingRule) {
        mutateRouting {
            settingsRepository.setRoutingRule(rule)
            routingChangeManager.markPendingChanges()
        }
    }

    fun addRule(rule: RoutingRule) {
        mutateRouting {
            settingsRepository.setRoutingRules(settingsRepository.customRoutingRules.first() + rule)
            routingChangeManager.markPendingChanges()
        }
    }

    fun deleteRules(ruleIds: Set<String>) {
        if (ruleIds.isEmpty()) return
        mutateRouting {
            val current = settingsRepository.customRoutingRules.first()
            settingsRepository.setRoutingRules(current.filterNot { it.id in ruleIds })
            routingChangeManager.markPendingChanges()
        }
    }

    fun setAllRulesEnabled(enabled: Boolean) {
        mutateRouting {
            val current = settingsRepository.customRoutingRules.first()
            val updatedRules = current.map { it.copy(enabled = enabled) }
            if (updatedRules == current) return@mutateRouting
            settingsRepository.setRoutingRules(updatedRules)
            routingChangeManager.markPendingChanges()
        }
    }

    fun resetRulesToDefaults() {
        mutateRouting {
            settingsRepository.setCustomRouting(SubscriptionRouting(RoutingRuleCatalog.defaults()))
            routingChangeManager.markPendingChanges()
        }
    }

    fun updateSubscriptionRule(rule: RoutingRule) {
        mutateRouting {
            settingsRepository.setSubscriptionRoutingRule(rule)
            routingChangeManager.markPendingChanges()
        }
    }

    internal fun setProfileRuleEnabled(rule: ProfileRoutingRule, enabled: Boolean) {
        updateProfileRule(
            rule = rule,
            replacement = rule.editableRule.takeIf { rule.locallyEdited },
            enabled = enabled,
        )
    }

    internal fun updateProfileRule(
        rule: ProfileRoutingRule,
        replacement: RoutingRule,
    ) {
        updateProfileRule(rule, replacement, enabled = rule.enabled)
    }

    private fun updateProfileRule(
        rule: ProfileRoutingRule,
        replacement: RoutingRule?,
        enabled: Boolean,
    ) {
        if (rule.orphaned) return
        val serverId = profileRouting.value?.serverId ?: return
        mutateRouting {
            if (settingsRepository.lastServerId.first() != serverId) return@mutateRouting
            settingsRepository.setRoutingPolicyControl(RoutingPolicyControl.User)
            val changed = serverRepository.updateProfileRouting(serverId) { config ->
                ProfileRoutingOverrideEngine.replace(
                    overrides = config.profileRoutingOverrides,
                    originalRuleJson = rule.originalRuleJson,
                    originalIndex = rule.originalIndex,
                    replacement = replacement,
                    enabled = enabled,
                )
            }
            if (changed) routingChangeManager.markPendingChanges(PendingRoutingChange.XRAY_CONFIG)
        }
    }

    fun applyPendingChangesIfNeeded() {
        mutateRouting { routingChangeManager.maybeReloadActiveConnection() }
    }

    fun reorderRules(scope: RoutingRuleScope, ids: List<String>) {
        mutateRouting {
            settingsRepository.reorderRoutingRules(scope, ids)
            routingChangeManager.markPendingChanges()
        }
    }

    fun reorderScopes(scopes: List<RoutingRuleScope>) {
        mutateRouting {
            settingsRepository.setRoutingScopeOrder(scopes)
            routingChangeManager.markPendingChanges()
        }
    }

    internal fun reorderProfileRules(serverId: Long, orderedRules: List<ProfileRoutingRule>) {
        mutateRouting {
            if (settingsRepository.lastServerId.first() != serverId) return@mutateRouting
            settingsRepository.setRoutingPolicyControl(RoutingPolicyControl.User)
            val changed = serverRepository.updateProfileRouting(serverId) { config ->
                val current = ProfileRoutingInspector.inspect(config)?.rules.orEmpty().filterNot { it.orphaned }
                val order = orderedRules.map { it.originalRuleJson to it.originalIndex }
                val ordered = current.reorderedBy(order) { it.originalRuleJson to it.originalIndex }
                if (ordered == current) {
                    config.profileRoutingOverrides
                } else {
                    ProfileRoutingOverrideEngine.reorder(
                        config.rawConfigJson,
                        config.profileRoutingOverrides,
                        ordered.map { ProfileRoutingOverride(it.originalRuleJson, it.originalIndex, enabled = it.enabled) },
                    )
                }
            }
            if (changed) routingChangeManager.markPendingChanges(PendingRoutingChange.XRAY_CONFIG)
        }
    }

    internal fun resetServerRules(serverId: Long) {
        mutateRouting {
            if (settingsRepository.lastServerId.first() != serverId) return@mutateRouting
            if (serverRepository.updateProfileRouting(serverId) { emptyList() }) {
                routingChangeManager.markPendingChanges(PendingRoutingChange.XRAY_CONFIG)
                routingChangeManager.maybeReloadActiveConnection()
            }
        }
    }

    internal fun resetSubscriptionRules(subscriptionId: Long, expectedSourceId: Long?) {
        mutateRouting {
            if (subscriptionRoutingRepository.resetForSubscription(subscriptionId, expectedSourceId)) {
                routingChangeManager.markPendingChanges(PendingRoutingChange.XRAY_ROUTING)
                routingChangeManager.maybeReloadActiveConnection()
            }
        }
    }

    fun switchToManualRouting() {
        mutateRouting {
            settingsRepository.setRoutingPolicyControl(RoutingPolicyControl.User)
        }
    }
}
