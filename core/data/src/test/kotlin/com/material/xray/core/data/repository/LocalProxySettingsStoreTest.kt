package com.material.xray.core.data.repository

import com.material.xray.core.model.BackupData
import com.material.xray.core.model.LocalProxySettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalProxySettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val file by lazy { File(folder.root, "settings.preferences_pb") }
    private val repository by lazy { SettingsRepository(PreferenceDataStores.settings { file }) { } }

    @Test
    fun `credentials are generated once and kept`() = runBlocking {
        val first = repository.settingsSnapshot.first().localProxy
        assertTrue(LocalProxySettings.isValidCredential(first.username))
        assertTrue(LocalProxySettings.isValidCredential(first.password))
        assertEquals(8, first.username.length)
        assertEquals(8, first.password.length)
        assertNotEquals(first.username, first.password)

        repository.setLocalProxyPort(20000)
        repository.setLocalProxyUsername("alice")

        val again = repository.settingsSnapshot.first().localProxy
        assertEquals(first.copy(port = 20000, username = "alice"), again)
    }

    @Test
    fun `the runtime gets the proxy only while it is enabled`() = runBlocking {
        val defaults = repository.runtimeSettingsSnapshot()
        assertTrue(defaults.deviceTunnel)
        assertNull(defaults.localProxy)

        repository.setLocalProxyEnabled(true)
        repository.setDeviceTunnel(false)

        val proxyOnly = repository.runtimeSettingsSnapshot()
        assertFalse(proxyOnly.deviceTunnel)
        assertEquals(repository.settingsSnapshot.first().localProxy, proxyOnly.localProxy)
    }

    @Test
    fun `backups restore the credentials and replace missing ones`() = runBlocking {
        repository.setLocalProxyEnabled(true)
        val original = repository.settingsSnapshot.first().localProxy
        val backup = repository.getAllAsMap()

        repository.restoreFromMap(backup, sourceBackupVersion = BackupData.CURRENT_VERSION)
        assertEquals(original, repository.settingsSnapshot.first().localProxy)

        repository.restoreFromMap(
            backup - "local_proxy_username" - "local_proxy_password",
            sourceBackupVersion = BackupData.CURRENT_VERSION,
        )
        val regenerated = repository.settingsSnapshot.first().localProxy
        assertTrue(LocalProxySettings.isValidCredential(regenerated.username))
        assertTrue(LocalProxySettings.isValidCredential(regenerated.password))
        assertNotEquals(original.username, regenerated.username)
    }
}
