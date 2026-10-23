package com.material.xray.core.data.repository

import com.material.xray.core.model.BackupData
import com.material.xray.core.model.NetworkKind
import com.material.xray.core.model.NetworkServerHistory
import com.material.xray.core.model.NetworkServerPolicy
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NetworkServerRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val store by lazy { PreferenceDataStores.settings { File(folder.root, "settings.preferences_pb") } }
    private val repository by lazy { NetworkServerRepository(store) }

    @Test
    fun `history survives recreation backup restore and subscription replacement`() = runBlocking {
        repository.rememberServer(NetworkKind.Wifi, 10)
        repository.rememberServer(NetworkKind.Mobile, 20)
        assertEquals(NetworkServerHistory(10, 20), NetworkServerRepository(store).history.first())
        val settings = SettingsRepository(store) { }
        val backup = settings.getAllAsMap()
        repository.rememberServer(NetworkKind.Wifi, 30)
        settings.restoreFromMap(backup, sourceBackupVersion = BackupData.CURRENT_VERSION)
        assertEquals(NetworkServerHistory(10, 20), repository.history.first())
        repository.replaceAfterRefresh(mapOf(10L to 11L), setOf(11))
        assertEquals(NetworkServerHistory(wifiServerId = 11), repository.history.first())
        settings.restoreFromMap(emptyMap(), sourceBackupVersion = 5)
        assertEquals(NetworkServerHistory(), repository.history.first())
        assertEquals(NetworkServerHistory(), decodeNetworkServerHistory("malformed"))
    }

    @Test
    fun `assignments survive datastore recreation and settings restore`() = runBlocking {
        val policy = NetworkServerPolicy(true, 10, 20, mapOf(" Exact SSID " to 30))
        repository.update { policy }
        assertEquals(policy, NetworkServerRepository(store).policy.first())
        val settings = SettingsRepository(store) { }
        val backup = settings.getAllAsMap()
        repository.update { NetworkServerPolicy() }
        settings.restoreFromMap(backup, sourceBackupVersion = BackupData.CURRENT_VERSION)
        assertEquals(policy, repository.policy.first())
        settings.restoreFromMap(emptyMap(), sourceBackupVersion = 5)
        assertEquals(NetworkServerPolicy(), repository.policy.first())
    }

    @Test
    fun `malformed saved policy disables switching`() {
        assertEquals(NetworkServerPolicy(), decodeNetworkServerPolicy("not json"))
        assertEquals(NetworkServerPolicy(), decodeNetworkServerPolicy(null))
    }
}
