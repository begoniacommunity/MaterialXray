package com.material.xray.core.data.repository

import com.material.xray.core.model.BackupData
import com.material.xray.core.model.Protocol
import com.material.xray.core.model.ServerConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkServerBackupTest {
    private val backup = BackupData(
        version = BackupData.CURRENT_VERSION,
        subscriptions = listOf(BackupData.BackupSubscription(key = "sub", name = "Provider", url = "https://example.com/sub")),
        servers = listOf(
            BackupData.BackupServer(
                key = "stable-server",
                subscriptionKey = "sub",
                subscriptionUrl = null,
                config = ServerConfig(protocol = Protocol.VLESS, name = "A", address = "example.com", port = 443, password = "uuid"),
            ),
        ),
        bypassedApps = emptyList(),
        settings = emptyMap(),
        networkServers = BackupData.BackupNetworkServers(true, "stable-server", "stable-server", mapOf(" Exact WiFi " to "stable-server"), "stable-server", "stable-server"),
    )

    @Test
    fun `network references survive JSON and backup planning with stable keys`() {
        val restored = Json.decodeFromString<BackupData>(Json.encodeToString(BackupData.serializer(), backup))
        val plan = BackupImportPlanner.create(restored)
        assertEquals(backup.networkServers, plan.source.networkServers)
        assertEquals("stable-server", plan.servers.single().key)
    }

    @Test
    fun `unresolved network targets are rejected before replacing data`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupImportPlanner.create(backup.copy(networkServers = backup.networkServers!!.copy(wifiServerKey = "missing")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupImportPlanner.create(backup.copy(networkServers = backup.networkServers!!.copy(lastWifiServerKey = "missing")))
        }
    }

    @Test
    fun `empty SSID is rejected but significant spaces are preserved`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupImportPlanner.create(backup.copy(networkServers = backup.networkServers!!.copy(ssidServers = mapOf("" to "stable-server"))))
        }
        assertEquals(setOf(" Exact WiFi "), BackupImportPlanner.create(backup).source.networkServers!!.ssidServers.keys)
    }
}
