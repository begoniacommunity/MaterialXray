package com.material.xray.core.data.repository

import com.material.xray.core.data.parser.FetchedSubscription
import com.material.xray.core.data.parser.ShareLinkParser
import com.material.xray.core.data.parser.SubscriptionFetcher
import com.material.xray.core.data.parser.SubscriptionHardwareIdRequiredException
import com.material.xray.core.data.parser.SubscriptionUrlReplacement
import com.material.xray.core.database.AppDatabase
import com.material.xray.core.database.dao.ServerDao
import com.material.xray.core.database.dao.SubscriptionDao
import com.material.xray.core.database.entity.ServerEntity
import com.material.xray.core.database.entity.SubscriptionEntity
import com.material.xray.core.database.withWriteTransaction
import com.material.xray.core.model.ProfileRoutingOverrideEngine
import com.material.xray.core.model.ServerConfig
import com.material.xray.core.model.SubscriptionAppRouting
import com.material.xray.core.model.SubscriptionRequestIdentity
import com.material.xray.core.model.SubscriptionRouting
import com.material.xray.core.model.SubscriptionUserAgentMode
import com.material.xray.core.model.parseSubscriptionHeaders
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

@Singleton
class SubscriptionRepository(
    private val database: AppDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val serverDao: ServerDao,
    private val fetcher: SubscriptionFetcher,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val shareLinkParser = ShareLinkParser()
    private val refreshState = SubscriptionRefreshState()
    val refreshingSubscriptionIds = refreshState.refreshingIds

    fun observeAll(): Flow<List<SubscriptionEntity>> = combine(
        subscriptionDao.observeAll(),
        settingsRepository.legacySubscriptionPreferJson,
    ) { subscriptions, legacyPreferJson ->
        subscriptions.map { subscription ->
            if (subscription.preferJson == null) {
                subscription.copy(preferJson = legacyPreferJson)
            } else {
                subscription
            }
        }
    }

    suspend fun getById(id: Long): SubscriptionEntity? = subscriptionDao.getById(id)

    fun observeRequiresHardwareIdForServer(serverId: Long): Flow<Boolean?> = subscriptionDao.observeRequiresHardwareIdForServer(serverId)

    fun observeUseProfileDnsOverrideForServer(serverId: Long): Flow<Boolean?> = subscriptionDao.observeUseProfileDnsOverrideForServer(serverId)

    suspend fun setUseProfileDnsOverrideForServer(serverId: Long, value: Boolean?) = subscriptionDao.setUseProfileDnsOverrideForServer(serverId, value)

    data class RefreshResult(
        val subscriptionId: Long,
        val serverIdByConfigJson: Map<String, Long>,
        val serverIdReplacements: Map<Long, Long>,
        val appRouting: SubscriptionAppRouting? = null,
        val routing: SubscriptionRouting? = null,
    )

    data class RefreshBatchResult(
        val successes: Map<Long, RefreshResult>,
        val failures: Map<Long, IOException>,
    )

    internal data class PreparedRefresh(
        val subscriptionId: Long,
        val fetched: FetchedSubscription,
        val servers: List<ServerEntity>,
    )

    suspend fun add(
        name: String,
        url: String,
        preferJson: Boolean = true,
        allowInsecureUpdates: Boolean = false,
        userAgentMode: SubscriptionUserAgentMode = SubscriptionUserAgentMode.default,
        customUserAgent: String = "",
        customHeaders: String = "",
        confirmHardwareId: suspend () -> Boolean = { false },
    ): Long? {
        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        parseShareLinks(trimmedUrl)?.let { configs ->
            val named = if (configs.size == 1 && trimmedName.isNotEmpty()) listOf(configs.single().copy(name = trimmedName)) else configs
            return addLocalServers(named)
        }

        val draft = SubscriptionEntity(
            name = trimmedName,
            url = trimmedUrl,
            preferJson = preferJson,
            allowInsecureUpdates = allowInsecureUpdates,
            userAgentMode = userAgentMode.value,
            customUserAgent = customUserAgent.trim().ifBlank { null },
            customHeaders = customHeaders.trim().ifBlank { null },
        )
        val sendHardwareId = settingsRepository.subscriptionSendHardwareId.first()
        val fetched = try {
            fetcher.fetchWithMetadata(trimmedUrl, draft.requestIdentity(sendHardwareId), preferJson, allowInsecureUpdates, requireHardwareIdConsent = true)
        } catch (_: SubscriptionHardwareIdRequiredException) {
            if (!confirmHardwareId()) return null
            // Consent belongs to this subscription. Keep the user's global preference intact.
            fetcher.fetchWithMetadata(trimmedUrl, draft.requestIdentity(true), preferJson, allowInsecureUpdates)
                .let { it.copy(metadata = it.metadata.copy(requiresHardwareId = true)) }
        }
        return database.withWriteTransaction {
            val id = subscriptionDao.insertAtEnd(draft.applyFetchedData(fetched))
            serverDao.insertAll(fetched.configs.mapIndexed { index, config -> config.toServerEntity(id, index) })
            id
        }
    }

    suspend fun addLink(link: String, confirmHardwareId: suspend () -> Boolean = { false }): Long? = add(name = "", url = link, confirmHardwareId = confirmHardwareId)

    /** Every separated piece must be a share link; anything else is a subscription URL. */
    private fun parseShareLinks(text: String): List<ServerConfig>? {
        val links = text.split(SHARE_LINK_SEPARATOR).map { it.trim().trimEnd(',', ';', '|') }.filter(String::isNotEmpty)
        if (links.isEmpty()) return null
        return links.map { shareLinkParser.parse(it) ?: return null }
    }

    private suspend fun addLocalServers(configs: List<ServerConfig>): Long = database.withWriteTransaction {
        val localId = subscriptionDao.getLocal()?.id ?: subscriptionDao.insertAtEnd(newLocalGroup())
        val firstSortOrder = (serverDao.getMaxSortOrder(localId) ?: -1) + 1
        serverDao.insertAll(configs.mapIndexed { index, config -> config.withFallbackName().toServerEntity(localId, firstSortOrder + index) })
        localId
    }

    /**
     * Share links used to become one never-refreshing subscription each. Moves their servers, ids
     * intact, into the local group and drops the emptied rows. Only rows that cannot be a real
     * subscription match: the URL is itself a share link, auto-update is off and at most one server.
     */
    suspend fun consolidateLocalServers() = database.withWriteTransaction {
        val legacy = subscriptionDao.getAll().filter { sub ->
            !sub.isLocal &&
                sub.autoUpdateIntervalHours == 0 &&
                shareLinkParser.parse(sub.url.trim()) != null &&
                serverDao.getBySubscription(sub.id).size <= 1
        }
        if (legacy.isEmpty()) return@withWriteTransaction
        val localId = subscriptionDao.getLocal()?.id
            ?: subscriptionDao.insert(newLocalGroup().copy(sortOrder = legacy.first().sortOrder))
        var sortOrder = (serverDao.getMaxSortOrder(localId) ?: -1) + 1
        legacy.forEach { sub ->
            serverDao.getBySubscription(sub.id).forEach { server ->
                serverDao.moveToSubscription(server.id, localId, sortOrder++)
                // The group title was the only name shown for the link; keep it unless it was automatic.
                val title = sub.name.trim().takeUnless { it.isEmpty() || it.isFallbackSubscriptionName() }
                val name = title ?: server.name.trim().ifEmpty { "${server.address}:${server.port}" }
                if (name != server.name) {
                    val configJson = runCatching { json.encodeToString(json.decodeFromString<ServerConfig>(server.configJson).copy(name = name)) }
                        .getOrDefault(server.configJson)
                    serverDao.rename(server.id, name, configJson)
                }
            }
            subscriptionDao.delete(sub)
        }
    }

    private fun ServerConfig.withFallbackName() = if (name.isBlank()) copy(name = "$address:$port") else this

    private fun newLocalGroup() = SubscriptionEntity(
        name = "",
        url = "",
        lastUpdated = System.currentTimeMillis(),
        autoUpdateIntervalHours = 0,
        isLocal = true,
    )

    internal suspend fun prepareRefresh(subId: Long, url: String): PreparedRefresh? {
        val existing = subscriptionDao.getById(subId)?.takeUnless { it.isLocal } ?: return null
        val identity = existing.requestIdentity(settingsRepository.subscriptionSendHardwareId.first() || existing.requiresHardwareId)
        val preferJson = existing.preferJson ?: settingsRepository.legacySubscriptionPreferJson.first()
        val fetched = fetchWithFallback(existing, url, identity, preferJson)
        val servers = fetched.configs.mapIndexed { index, config -> config.toServerEntity(subId, index) }

        return PreparedRefresh(
            subscriptionId = subId,
            fetched = fetched,
            servers = servers,
        )
    }

    /**
     * Fetches the subscription, retrying the provider-advertised backup URL once when the primary
     * URL fails. The stored subscription URL always stays the primary one: a backup mirror is only
     * ever a fetch target, so its redirects are not promoted.
     */
    private suspend fun fetchWithFallback(
        existing: SubscriptionEntity,
        url: String,
        identity: SubscriptionRequestIdentity,
        preferJson: Boolean,
    ): FetchedSubscription = try {
        fetcher.fetchWithMetadata(
            url = url,
            identity = identity,
            preferJson = preferJson,
            allowInsecureUpdates = existing.allowInsecureUpdates,
            requireHardwareIdConsent = true,
        )
    } catch (error: SubscriptionHardwareIdRequiredException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        val fallbackUrl = existing.fallbackRefreshUrl(url) ?: throw error
        try {
            fetcher.fetchWithMetadata(
                url = fallbackUrl,
                identity = identity,
                preferJson = preferJson,
                allowInsecureUpdates = existing.allowInsecureUpdates,
                requireHardwareIdConsent = true,
            )
        } catch (error: SubscriptionHardwareIdRequiredException) {
            throw error
        } catch (fallbackError: CancellationException) {
            throw fallbackError
        } catch (_: IOException) {
            // Both endpoints failed; the primary failure is the actionable one.
            throw error
        }.copy(resolvedUrl = url, permanentRedirectUrl = null)
            .withFallbackPolicyPreserved(existing)
    }

    internal suspend fun commitRefresh(prepared: PreparedRefresh): RefreshResult? {
        val subId = prepared.subscriptionId
        return database.withWriteTransaction {
            val current = subscriptionDao.getById(subId) ?: return@withWriteTransaction null
            val existingServers = serverDao.getBySubscription(subId)
            val refreshedServers = carryProfileRoutingOverridesInto(existingServers, prepared.servers, json)
            val finalServers = mergeGuardedServersInto(existingServers, refreshedServers)
            serverDao.deleteBySubscription(subId)
            val insertedIds = if (finalServers.isEmpty()) emptyList() else serverDao.insertAll(finalServers)
            subscriptionDao.update(current.applyFetchedData(prepared.fetched))
            val insertedServers = finalServers.zip(insertedIds).map { (server, id) -> server.copy(id = id) }
            RefreshResult(
                subscriptionId = subId,
                serverIdByConfigJson = insertedServers
                    .associate { server -> server.configJson to server.id },
                serverIdReplacements = buildServerIdReplacements(existingServers, insertedServers),
                appRouting = prepared.fetched.appRouting,
                routing = prepared.fetched.routing,
            )
        }
    }

    internal suspend fun getAllSubscriptions(): List<SubscriptionEntity> = subscriptionDao.getAll()

    internal suspend fun <T> withRefreshLock(subscriptionId: Long, block: suspend () -> T): T = refreshState.withRefreshLock(subscriptionId, block)

    internal suspend fun <T> withRefreshTracking(subscriptionId: Long, block: suspend () -> T): T = refreshState.withRefreshTracking(subscriptionId, block)

    suspend fun delete(sub: SubscriptionEntity) = withRefreshLock(sub.id) {
        subscriptionDao.delete(sub)
    }

    internal suspend fun deleteLocalServer(local: SubscriptionEntity, serverId: Long) = database.withWriteTransaction {
        serverDao.deleteById(serverId)
        if (serverDao.getMaxSortOrder(local.id) == null) subscriptionDao.delete(local)
    }

    suspend fun setAutoUpdateInterval(subId: Long, intervalHours: Int) {
        subscriptionDao.updateAutoUpdateInterval(subId, intervalHours.coerceAtLeast(0))
    }

    internal suspend fun recordAutoRefreshFailure(subId: Long, failedAt: Long) {
        subscriptionDao.recordAutoRefreshFailure(subId, failedAt)
    }

    suspend fun setDescriptionHidden(subId: Long, hidden: Boolean) {
        subscriptionDao.updateDescriptionHidden(subId, hidden)
    }

    suspend fun updateSortOrders(subscriptionIds: List<Long>) {
        subscriptionDao.updateSortOrders(subscriptionIds)
    }

    internal suspend fun updateBeforeRefresh(
        sub: SubscriptionEntity,
        name: String,
        url: String,
    ): SubscriptionEntity? = database.withWriteTransaction {
        val current = subscriptionDao.getById(sub.id) ?: return@withWriteTransaction null
        val updated = current.copy(
            name = name.trim().ifEmpty { nextFallbackName(excludingId = sub.id) },
            url = url.trim(),
            preferJson = sub.preferJson,
            autoUpdateIntervalHours = sub.autoUpdateIntervalHours,
            userAgentMode = sub.userAgentMode,
            customUserAgent = sub.customUserAgent,
            customHeaders = sub.customHeaders,
            allowInsecureUpdates = sub.allowInsecureUpdates,
        )
        subscriptionDao.update(updated)
        updated
    }

    private suspend fun SubscriptionEntity.applyFetchedData(fetched: FetchedSubscription): SubscriptionEntity {
        val providerName = fetched.metadata.profileTitle.trimToNull()
        return withSubscriptionMetadata(
            metadata = fetched.metadata.copy(requiresHardwareId = fetched.metadata.requiresHardwareId || requiresHardwareId),
            // A permanent redirect is the most current location; the provider's new-url/new-domain
            // directive is the next authority. Both move the subscription permanently.
            resolvedUrl = fetched.permanentRedirectUrl
                ?.takeIf { it.isNotBlank() }
                ?: SubscriptionUrlReplacement.resolve(fetched.metadata, url)
                ?: url,
            resolvedName = resolveDisplayName(providerName),
            lastUpdated = System.currentTimeMillis(),
        ).withSubscriptionAppRouting(fetched.appRouting)
            .withSubscriptionRouting(fetched.routing)
    }

    private suspend fun SubscriptionEntity.resolveDisplayName(providerName: String?): String {
        val currentName = name.trim()
        val previousProviderName = profileTitle.trimToNull()
        val automaticName = currentName.isEmpty() ||
            currentName == previousProviderName ||
            currentName.isFallbackSubscriptionName()

        return when {
            !automaticName -> currentName
            providerName != null -> providerName
            currentName.isNotEmpty() -> currentName
            else -> nextFallbackName(excludingId = id)
        }
    }

    private suspend fun nextFallbackName(excludingId: Long? = null): String {
        val usedNames = subscriptionDao.getAll()
            .asSequence()
            .filterNot { it.id == excludingId }
            .map { it.name.trim() }
            .toSet()
        var index = 1
        while ("Subscription $index" in usedNames) {
            index++
        }
        return "Subscription $index"
    }

    private fun ServerConfig.toServerEntity(subscriptionId: Long, sortOrder: Int) = ServerEntity(
        subscriptionId = subscriptionId,
        name = name,
        protocol = protocol.name,
        address = address,
        port = port,
        configJson = json.encodeToString(this),
        sortOrder = sortOrder,
    )

    private fun SubscriptionEntity.requestIdentity(sendHardwareId: Boolean): SubscriptionRequestIdentity = SubscriptionRequestIdentity(
        mode = SubscriptionUserAgentMode.fromValue(userAgentMode),
        sendHardwareId = sendHardwareId,
        customUserAgent = customUserAgent.orEmpty(),
        customHeaders = parseSubscriptionHeaders(customHeaders.orEmpty()),
    )

    private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.isFallbackSubscriptionName(): Boolean = matches(FALLBACK_NAME_PATTERN)

    private fun buildServerIdReplacements(
        oldServers: List<ServerEntity>,
        newServers: List<ServerEntity>,
    ): Map<Long, Long> {
        val newServerIdByConfigJson = newServers.associate { it.configJson to it.id }
        val uniqueNewServerIdByName = newServers.uniqueByTrimmedName()
        val uniqueOldNames = oldServers.uniqueTrimmedNames()

        return oldServers.mapNotNull { oldServer ->
            val replacementId = newServerIdByConfigJson[oldServer.configJson]
                ?: oldServer.name.trim()
                    .takeIf { it.isNotEmpty() && it in uniqueOldNames }
                    ?.let(uniqueNewServerIdByName::get)
                ?: return@mapNotNull null

            oldServer.id to replacementId
        }.toMap()
    }

    private fun List<ServerEntity>.uniqueByTrimmedName(): Map<String, Long> = asSequence()
        .map { it.name.trim() to it.id }
        .filter { (name, _) -> name.isNotEmpty() }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size == 1 }
        .mapValues { (_, ids) -> ids.single() }

    private fun List<ServerEntity>.uniqueTrimmedNames(): Set<String> = asSequence()
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .filterValues { it == 1 }
        .keys

    private companion object {
        val FALLBACK_NAME_PATTERN = Regex("""Subscription \d+""")

        /**
         * Separators count only right before the next scheme: a link may carry punctuation itself, as
         * in `alpn=h2,http/1.1`, and `ss://` and `hy2://` names may hold raw spaces.
         */
        val SHARE_LINK_SEPARATOR = Regex("""[\s,;|]+(?=[A-Za-z][A-Za-z0-9+.-]*://)""")
    }
}

internal fun SubscriptionEntity.isDueForRefresh(nowMillis: Long): Boolean {
    if (lastAutoRefreshFailureAt > lastUpdated &&
        nowMillis - lastAutoRefreshFailureAt < AUTO_REFRESH_FAILURE_RETRY_INTERVAL_MILLIS
    ) {
        return false
    }
    if (lastUpdated <= 0L) return true

    val interval = autoUpdateIntervalHours
    if (interval <= 0) return false

    val intervalMillis = interval * MILLIS_PER_HOUR
    return nowMillis - lastUpdated >= intervalMillis
}

/**
 * The provider-advertised backup URL when it differs from the primary URL; null otherwise.
 */
internal fun SubscriptionEntity.fallbackRefreshUrl(primaryUrl: String): String? {
    val fallback = fallbackUrl?.trim().orEmpty()
    if (fallback.isEmpty()) return null
    if (fallback.equals(primaryUrl.trim(), ignoreCase = true)) return null
    return fallback
}

/**
 * A plain backup mirror rarely repeats the provider's metadata. Keeping the policy the primary
 * URL advertised (its fallback URL and hardware ID requirement) stops one successful fallback
 * refresh from silently erasing it while the primary endpoint is down.
 */
internal fun FetchedSubscription.withFallbackPolicyPreserved(existing: SubscriptionEntity): FetchedSubscription = copy(
    metadata = metadata.copy(
        fallbackUrl = metadata.fallbackUrl ?: existing.fallbackUrl,
        requiresHardwareId = metadata.requiresHardwareId || existing.requiresHardwareId,
    ),
)

/**
 * Keeps guarded servers verbatim while every other server is replaced by the fetched set.
 *
 * A guarded server carries its existing non-zero id, so re-inserting it inside the same transaction
 * that deleted it restores the same primary key: the selected server, the app bypass rows and the
 * id replacement map all keep pointing at it without any extra work.
 *
 * Matching is by exact trimmed name, the same identity the id replacement map falls back to. A
 * guarded server the provider renamed or dropped is appended at the end rather than lost, because
 * guarding means updates to that outbound are ignored, not that it may disappear.
 */
internal fun mergeGuardedServersInto(
    existingServers: List<ServerEntity>,
    fetchedServers: List<ServerEntity>,
): List<ServerEntity> {
    val unmatchedGuarded = existingServers.filter { it.guarded }.toMutableList()
    if (unmatchedGuarded.isEmpty()) return fetchedServers

    // ponytail: linear scan per fetched server. A subscription holds tens of servers, so an index
    // is not worth the duplicate-name bookkeeping it would need.
    val slotted = fetchedServers.map { fetched ->
        val name = fetched.name.trim()
        // A blank name identifies nothing, so it must not claim the first blank-named slot. The id
        // replacement map skips blank names for the same reason.
        val matchIndex = if (name.isEmpty()) -1 else unmatchedGuarded.indexOfFirst { it.name.trim() == name }
        if (matchIndex < 0) fetched else unmatchedGuarded.removeAt(matchIndex)
    }

    return (slotted + unmatchedGuarded).mapIndexed { index, server -> server.copy(sortOrder = index) }
}

/** Carries local per-rule deltas onto refreshed profiles without guarding the entire server. */
internal fun carryProfileRoutingOverridesInto(
    existingServers: List<ServerEntity>,
    fetchedServers: List<ServerEntity>,
    json: Json = Json { ignoreUnknownKeys = true },
): List<ServerEntity> {
    data class ExistingOverride(
        val index: Int,
        val entity: ServerEntity,
        val config: ServerConfig,
        val baseConfigJson: String,
    )

    val existingOverrides = existingServers.mapIndexedNotNull { index, entity ->
        if (entity.guarded) return@mapIndexedNotNull null
        val config = runCatching { json.decodeFromString<ServerConfig>(entity.configJson) }.getOrNull()
            ?: return@mapIndexedNotNull null
        if (config.profileRoutingOverrides.isEmpty()) return@mapIndexedNotNull null
        ExistingOverride(
            index = index,
            entity = entity,
            config = config,
            baseConfigJson = json.encodeToString(config.copy(profileRoutingOverrides = emptyList(), configurationEdited = null)),
        )
    }
    if (existingOverrides.isEmpty()) return fetchedServers

    fun uniqueNames(servers: List<ServerEntity>): Set<String> = servers.asSequence()
        .map { it.name.trim() }
        .filter(String::isNotEmpty)
        .groupingBy { it }
        .eachCount()
        .filterValues { it == 1 }
        .keys

    val uniqueExistingNames = uniqueNames(existingServers)
    val uniqueFetchedNames = uniqueNames(fetchedServers)
    val claimedExistingIndices = mutableSetOf<Int>()
    return fetchedServers.map { fetched ->
        val fetchedConfig = runCatching { json.decodeFromString<ServerConfig>(fetched.configJson) }.getOrNull()
            ?: return@map fetched
        val fetchedBaseJson = json.encodeToString(fetchedConfig.copy(profileRoutingOverrides = emptyList(), configurationEdited = null))
        val exact = existingOverrides.firstOrNull {
            it.index !in claimedExistingIndices && it.baseConfigJson == fetchedBaseJson
        }
        val name = fetched.name.trim()
        val matched = exact ?: existingOverrides.firstOrNull {
            it.index !in claimedExistingIndices &&
                name.isNotEmpty() &&
                name in uniqueExistingNames &&
                name in uniqueFetchedNames &&
                it.entity.name.trim() == name
        } ?: return@map fetched
        claimedExistingIndices += matched.index

        val reconciled = ProfileRoutingOverrideEngine.reconcile(
            fetchedConfig.rawConfigJson,
            matched.config.profileRoutingOverrides,
        )
        fetched.copy(
            configJson = json.encodeToString(fetchedConfig.copy(profileRoutingOverrides = reconciled, configurationEdited = false)),
            edited = true,
        )
    }
}

private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
private const val AUTO_REFRESH_FAILURE_RETRY_INTERVAL_MILLIS = MILLIS_PER_HOUR
