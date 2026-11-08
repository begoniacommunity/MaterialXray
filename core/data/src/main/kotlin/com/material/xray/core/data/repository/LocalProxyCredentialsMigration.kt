package com.material.xray.core.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.material.xray.core.model.LocalProxySettings

/**
 * Gives the local proxy its credentials the first time the store opens, so they stay the same from
 * then on and a client configured once keeps working.
 */
internal class LocalProxyCredentialsMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = !currentData.hasLocalProxyCredentials()

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        ensureLocalProxyCredentials()
    }

    override suspend fun cleanUp() = Unit
}

internal fun MutablePreferences.ensureLocalProxyCredentials() {
    if (this[SettingsRepository.LOCAL_PROXY_USERNAME]?.let(LocalProxySettings::isValidCredential) != true) {
        this[SettingsRepository.LOCAL_PROXY_USERNAME] = LocalProxySettings.generateCredential()
    }
    if (this[SettingsRepository.LOCAL_PROXY_PASSWORD]?.let(LocalProxySettings::isValidCredential) != true) {
        this[SettingsRepository.LOCAL_PROXY_PASSWORD] = LocalProxySettings.generateCredential()
    }
}

internal fun Preferences.localProxySettings(): LocalProxySettings = LocalProxySettings(
    port = LocalProxySettings.normalizePort(this[SettingsRepository.LOCAL_PROXY_PORT]),
    username = this[SettingsRepository.LOCAL_PROXY_USERNAME].orEmpty(),
    password = this[SettingsRepository.LOCAL_PROXY_PASSWORD].orEmpty(),
    allowLan = this[SettingsRepository.LOCAL_PROXY_ALLOW_LAN] ?: false,
)

private fun Preferences.hasLocalProxyCredentials(): Boolean = this[SettingsRepository.LOCAL_PROXY_USERNAME]?.let(LocalProxySettings::isValidCredential) == true &&
    this[SettingsRepository.LOCAL_PROXY_PASSWORD]?.let(LocalProxySettings::isValidCredential) == true
