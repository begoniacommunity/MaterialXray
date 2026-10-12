package com.material.xray.feature.home

import android.content.Context
import android.content.res.Resources
import com.material.xray.core.android.locale.appLocaleChanges
import com.material.xray.core.android.locale.forAppLanguage
import com.material.xray.core.android.locale.localizedString
import com.material.xray.core.common.di.ApplicationScope
import com.material.xray.core.data.repository.ProviderRoutingAvailability
import com.material.xray.core.data.repository.ServerRepository
import com.material.xray.core.data.repository.SettingsRepository
import com.material.xray.core.data.repository.SubscriptionRepository
import com.material.xray.core.data.repository.providerRoutingAvailability
import com.material.xray.core.data.repository.toSubscriptionRouting
import com.material.xray.core.database.entity.ServerEntity
import com.material.xray.core.database.entity.SubscriptionEntity
import com.material.xray.core.model.ServerConfig
import com.material.xray.core.model.endpointSummary
import com.material.xray.core.model.proxyOutboundCount
import com.material.xray.core.ui.R
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

/**
 * Everything the Home screen needs to render its first frame, produced as one atomic snapshot so
 * the subscription list, the server rows, and the selected server title all appear together.
 */
data class HomeData(
    val subscriptions: List<SubscriptionEntity>,
    val servers: List<ServerEntity>,
    val serverItems: List<ServerListItem>,
    val selectedServerId: Long,
    val selectedServer: ServerConfig?,
    val providerRoutingBySubscription: Map<Long, ProviderRoutingAvailability>,
    val selectedProviderRouting: ProviderRoutingAvailability?,
)

/**
 * Process-wide holder for the Home screen data.
 *
 * The snapshot is shared eagerly in the application scope, which serves two purposes:
 * - Warm-up: [com.material.xray.MainActivity] injects this holder before its first composition,
 *   so a UI launch opens the database and builds its initial snapshot while the splash screen
 *   is visible. Headless worker starts do not load the Home screen's server list.
 * - Readiness: [data] is `null` until the first snapshot is built, which lets
 *   [com.material.xray.MainActivity] keep the splash screen visible until the Home screen can
 *   render fully populated, and lets the UI distinguish "not loaded yet" from "no subscriptions".
 */
@Singleton
class HomeDataState(
    private val context: Context,
    subscriptionRepository: SubscriptionRepository,
    serverRepository: ServerRepository,
    settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Only touched from the combine transform below, which runs sequentially for the single
    // stateIn collector, so no synchronization is needed. Rebuilt on every emission so entries
    // for servers that no longer exist do not accumulate for the lifetime of the process.
    private var endpointSummaryCache = mapOf<String, ServerPresentation>()

    // Routing JSON belongs in the background snapshot, including for collapsed cards. Rebuild
    // it only when subscriptions change, rather than on every server latency update or tab entry.
    private val subscriptionsWithRouting = subscriptionRepository.observeAll().map { subscriptions ->
        subscriptions to subscriptions.associate { it.id to it.providerRoutingAvailability() }
    }

    val data: StateFlow<HomeData?> = combine(
        subscriptionsWithRouting,
        serverRepository.observeAll(),
        settingsRepository.lastServerId,
        settingsRepository.subscriptionRoutingState,
        appLocaleChanges.onStart { emit(Unit) },
    ) { (subscriptions, providerRouting), servers, selectedServerId, subscriptionRouting, _ ->
        val selectedEntity = servers.find { it.id == selectedServerId }
        val source = subscriptions.firstOrNull { it.id == subscriptionRouting.subscriptionId }
        val modifiedSubscription = source?.takeIf { subscriptionRouting.routing != it.toSubscriptionRouting() }?.id
        HomeData(
            subscriptions = subscriptions,
            servers = servers,
            serverItems = buildServerItems(servers, modifiedSubscription),
            selectedServerId = selectedServerId,
            selectedServer = selectedEntity?.let { entity ->
                runCatching { serverRepository.parseConfig(entity) }.getOrNull()
            },
            providerRoutingBySubscription = providerRouting,
            selectedProviderRouting = selectedEntity?.subscriptionId?.let(providerRouting::get),
        )
    }
        // Building the list items decodes every server config JSON, which grows with the number
        // of servers; keep that work off the main dispatcher.
        .flowOn(defaultDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun buildServerItems(servers: List<ServerEntity>, modifiedSubscription: Long?): List<ServerListItem> {
        val resources = context.forAppLanguage().resources
        val localeKey = resources.configuration.locales.toLanguageTags()
        val previousCache = endpointSummaryCache
        val cache = HashMap<String, ServerPresentation>()
        val items = servers.map { entity ->
            val key = "$localeKey\u0000${entity.edited}\u0000${entity.configJson}"
            val presentation = previousCache[key] ?: cache[key] ?: computeServerPresentation(resources, entity)
            cache[key] = presentation
            ServerListItem(
                entity = entity,
                endpointSummary = presentation.summary,
                latency = null,
                configurationEdited = presentation.configurationEdited,
                routingEdited = presentation.routingEdited || entity.subscriptionId == modifiedSubscription,
            )
        }
        endpointSummaryCache = cache
        return items
    }

    private fun computeServerPresentation(resources: Resources, entity: ServerEntity): ServerPresentation = runCatching {
        val config = json.decodeFromString<ServerConfig>(entity.configJson)
        val outboundCount = config.proxyOutboundCount()
        val summary = if (outboundCount == null) {
            config.endpointSummary()
        } else {
            resources.getQuantityString(
                R.plurals.home_server_multiconnect_summary,
                outboundCount,
                outboundCount,
            )
        }
        ServerPresentation(summary, config.configurationEdited ?: entity.edited, config.profileRoutingOverrides.isNotEmpty())
    }.getOrElse {
        val unknown = context.localizedString(R.string.home_server_endpoint_unknown)
        ServerPresentation("${entity.protocol.lowercase(Locale.ROOT)} • $unknown • $unknown", entity.edited, false)
    }

    private data class ServerPresentation(val summary: String, val configurationEdited: Boolean, val routingEdited: Boolean)
}
