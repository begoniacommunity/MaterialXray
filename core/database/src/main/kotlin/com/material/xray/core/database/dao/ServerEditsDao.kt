package com.material.xray.core.database.dao

import androidx.room.Dao
import androidx.room.Query

/** Atomic local edits without changing the existing server-list DAO contract. */
@Dao
interface ServerEditsDao {
    @Query(
        "UPDATE servers SET name = :name, protocol = :protocol, address = :address, " +
            "port = :port, configJson = :configJson, edited = 1, latencyMs = -1 " +
            "WHERE id = :id AND configJson = :previousConfigJson",
    )
    suspend fun updateEditedConfig(id: Long, name: String, protocol: String, address: String, port: Int, configJson: String, previousConfigJson: String): Int

    /** Routing changes preserve endpoint metadata, lock and measured latency. */
    @Query("UPDATE servers SET configJson = :configJson, edited = :edited WHERE id = :id AND configJson = :previousConfigJson")
    suspend fun updateRoutingConfig(id: Long, configJson: String, edited: Boolean, previousConfigJson: String): Int
}
