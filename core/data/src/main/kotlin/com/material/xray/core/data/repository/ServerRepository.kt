package com.material.xray.core.data.repository

import com.material.xray.core.database.dao.ServerDao
import com.material.xray.core.database.dao.ServerEditsDao
import com.material.xray.core.database.entity.ServerEntity
import com.material.xray.core.model.ProfileRoutingOverride
import com.material.xray.core.model.ProfileRoutingOverrideEngine
import com.material.xray.core.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class ServerRepository(
    private val serverDao: ServerDao,
    private val serverEditsDao: ServerEditsDao?,
) {
    /** Retains the existing constructor for callers that only read servers. */
    constructor(serverDao: ServerDao) : this(serverDao, null)

    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<ServerEntity>> = serverDao.observeAll()

    fun observeBySubscription(subId: Long): Flow<List<ServerEntity>> = serverDao.observeBySubscription(subId)

    suspend fun getById(id: Long): ServerEntity? = serverDao.getById(id)

    fun parseConfig(entity: ServerEntity): ServerConfig = json.decodeFromString(entity.configJson)

    suspend fun updateLatency(id: Long, latencyMs: Int) {
        serverDao.updateLatency(id, latencyMs)
    }

    /** Persists a locally edited config and marks the server as edited. */
    suspend fun saveEditedConfig(id: Long, config: ServerConfig): Boolean {
        while (true) {
            val entity = serverDao.getById(id) ?: return false
            val current = parseConfig(entity)
            val edited = config.copy(
                configurationEdited = true,
                profileRoutingOverrides = ProfileRoutingOverrideEngine.reconcile(config.rawConfigJson, current.profileRoutingOverrides),
            )
            val updated = checkNotNull(serverEditsDao).updateEditedConfig(
                id = id,
                name = edited.name,
                protocol = edited.protocol.name,
                address = edited.address,
                port = edited.port,
                configJson = json.encodeToString(ServerConfig.serializer(), edited),
                previousConfigJson = entity.configJson,
            )
            if (updated > 0) return true
        }
    }

    /** Changes only routing deltas, keeping config edits independent and retrying concurrent saves. */
    suspend fun updateProfileRouting(id: Long, transform: (ServerConfig) -> List<ProfileRoutingOverride>): Boolean {
        while (true) {
            val entity = serverDao.getById(id) ?: return false
            val current = parseConfig(entity)
            val overrides = transform(current)
            if (overrides == current.profileRoutingOverrides) return false
            val edited = current.copy(
                profileRoutingOverrides = overrides,
                configurationEdited = current.configurationEdited ?: entity.edited,
            )
            val updated = checkNotNull(serverEditsDao).updateRoutingConfig(
                id,
                json.encodeToString(ServerConfig.serializer(), edited),
                edited.configurationEdited == true || overrides.isNotEmpty(),
                entity.configJson,
            )
            if (updated > 0) return true
        }
    }

    suspend fun setGuarded(id: Long, guarded: Boolean) {
        serverDao.updateGuarded(id, guarded)
    }

    suspend fun updateSortOrders(changes: Map<Long, Int>) {
        serverDao.updateSortOrders(changes)
    }
}
