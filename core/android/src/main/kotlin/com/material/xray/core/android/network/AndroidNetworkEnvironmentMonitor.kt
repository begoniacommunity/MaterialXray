package com.material.xray.core.android.network

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.material.xray.core.common.di.ApplicationScope
import com.material.xray.core.model.NetworkEnvironment
import com.material.xray.core.model.NetworkKind
import com.material.xray.core.model.normalizedWifiSsid
import com.material.xray.core.network.NetworkEnvironmentMonitor
import com.material.xray.core.network.PhysicalNetworkCandidate
import com.material.xray.core.network.preferredPhysicalNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import org.koin.core.annotation.Singleton

@Singleton(binds = [NetworkEnvironmentMonitor::class])
class AndroidNetworkEnvironmentMonitor(private val context: Context, @ApplicationScope scope: CoroutineScope) : NetworkEnvironmentMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private var refreshCallback: (() -> Unit)? = null
    override fun refresh() {
        handler.post { refreshCallback?.invoke() }
    }

    override val environments = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = mutableMapOf<Network, NetworkCapabilities>()
        fun publish() {
            trySend(snapshot(manager, capabilities))
        }
        val callback = locationAware(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post { publish() }
            }
            override fun onLost(network: Network) {
                handler.post {
                    capabilities.remove(network)
                    publish()
                }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                handler.post {
                    capabilities[network] = caps
                    publish()
                }
            }
        })
        val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post { publish() }
            }
            override fun onLost(network: Network) {
                handler.post { publish() }
            }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                handler.post { publish() }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                publish()
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        // Register on the main thread, also on API24 where the handler overload does not exist.
        handler.post {
            manager.registerNetworkCallback(request, callback)
            manager.registerDefaultNetworkCallback(defaultCallback)
            ContextCompat.registerReceiver(context, receiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            refreshCallback = {
                // Re-register to obtain fresh, non-redacted capabilities after a permission change.
                capabilities.clear()
                manager.unregisterNetworkCallback(callback)
                manager.registerNetworkCallback(request, callback)
                publish()
            }
            publish()
        }
        awaitClose {
            handler.post {
                refreshCallback = null
                runCatching { manager.unregisterNetworkCallback(callback) }
                runCatching { manager.unregisterNetworkCallback(defaultCallback) }
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 1)

    @Suppress("DEPRECATION")
    private fun snapshot(manager: ConnectivityManager, cached: Map<Network, NetworkCapabilities>): NetworkEnvironment {
        val candidates = manager.allNetworks.mapNotNull { network ->
            (cached[network] ?: manager.getNetworkCapabilities(network))?.let { network to it }
        }.filter { (_, caps) -> caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        val preferred = candidates.map { (network, caps) ->
            PhysicalNetworkCandidate(network.networkHandle, caps.networkKind(), false, true, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }.preferredPhysicalNetwork(manager.activeNetwork?.networkHandle) ?: return NetworkEnvironment()
        val chosen = candidates.first { it.first.networkHandle == preferred.id }
        val (network, caps) = chosen
        val kind = caps.networkKind()
        val ssid = if (kind == NetworkKind.Wifi && canReadSsid()) {
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= 29) cached[network]?.transportInfo as? WifiInfo else null
                normalizedWifiSsid(info?.ssid ?: context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid)
            }.getOrNull()
        } else {
            null
        }
        return NetworkEnvironment(kind, network.networkHandle, ssid)
    }

    fun canReadSsid(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        androidx.core.location.LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)) &&
        canReadLocationInBackground()

    private fun canReadLocationInBackground(): Boolean {
        if (Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) return true
        val process = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(process)
        return process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun NetworkCapabilities.networkKind(): NetworkKind = when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Mobile
        else -> NetworkKind.Other
    }

    private fun locationAware(delegate: ConnectivityManager.NetworkCallback): ConnectivityManager.NetworkCallback = if (Build.VERSION.SDK_INT >= 31) {
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onAvailable(network: Network) = delegate.onAvailable(network)
            override fun onLost(network: Network) = delegate.onLost(network)
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = delegate.onCapabilitiesChanged(network, networkCapabilities)
        }
    } else {
        delegate
    }
}
