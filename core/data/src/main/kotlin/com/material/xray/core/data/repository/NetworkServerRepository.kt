package com.material.xray.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.material.xray.core.model.NetworkKind
import com.material.xray.core.model.NetworkServerHistory
import com.material.xray.core.model.NetworkServerPolicy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Singleton

const val NETWORK_SERVER_POLICY_KEY = "network_server_policy"
const val NETWORK_SERVER_HISTORY_KEY = "network_server_history"
private val networkPolicyKey = stringPreferencesKey(NETWORK_SERVER_POLICY_KEY)
private val networkHistoryKey = stringPreferencesKey(NETWORK_SERVER_HISTORY_KEY)
private val networkPolicyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun decodeNetworkServerPolicy(encoded: String?): NetworkServerPolicy = encoded?.let {
    runCatching { networkPolicyJson.decodeFromString<NetworkServerPolicy>(it) }.getOrNull()
} ?: NetworkServerPolicy()

fun encodeNetworkServerPolicy(policy: NetworkServerPolicy): String = networkPolicyJson.encodeToString(NetworkServerPolicy.serializer(), policy)

fun decodeNetworkServerHistory(encoded: String?): NetworkServerHistory = encoded?.let {
    runCatching { networkPolicyJson.decodeFromString<NetworkServerHistory>(it) }.getOrNull()
} ?: NetworkServerHistory()

fun encodeNetworkServerHistory(history: NetworkServerHistory): String = networkPolicyJson.encodeToString(NetworkServerHistory.serializer(), history)

@Singleton
class NetworkServerRepository(@SettingsDataStore private val store: DataStore<Preferences>) {
    val policy = store.data.map { decodeNetworkServerPolicy(it[networkPolicyKey]) }.distinctUntilChanged()
    val history = store.data.map { decodeNetworkServerHistory(it[networkHistoryKey]) }.distinctUntilChanged()

    suspend fun update(transform: (NetworkServerPolicy) -> NetworkServerPolicy) {
        store.edit { prefs -> prefs[networkPolicyKey] = encodeNetworkServerPolicy(transform(decodeNetworkServerPolicy(prefs[networkPolicyKey]))) }
    }

    suspend fun rememberServer(kind: NetworkKind, serverId: Long) {
        store.edit { prefs -> prefs[networkHistoryKey] = encodeNetworkServerHistory(decodeNetworkServerHistory(prefs[networkHistoryKey]).remember(kind, serverId)) }
    }

    suspend fun replaceAfterRefresh(replacements: Map<Long, Long>, available: Set<Long>) {
        store.edit { prefs ->
            prefs[networkPolicyKey] = encodeNetworkServerPolicy(decodeNetworkServerPolicy(prefs[networkPolicyKey]).remap(replacements, available))
            prefs[networkHistoryKey] = encodeNetworkServerHistory(decodeNetworkServerHistory(prefs[networkHistoryKey]).remap(replacements, available))
        }
    }
}
