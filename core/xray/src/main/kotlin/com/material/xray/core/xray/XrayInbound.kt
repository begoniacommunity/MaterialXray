package com.material.xray.core.xray

import com.material.xray.core.model.LocalProxySettings
import com.material.xray.core.model.XrayRuntimeSettings
import kotlinx.serialization.json.JsonObject

sealed interface XrayInbound {
    val tag: String

    data class Tun(
        val name: String,
        override val tag: String,
        val mtu: Int = XrayRuntimeSettings.DEFAULT_TUN_MTU,
    ) : XrayInbound

    data class Tproxy(
        val port: Int,
        override val tag: String,
        val allowIpv6: Boolean,
        val acceptNonLoopback: Boolean = false,
    ) : XrayInbound

    /** The user's local SOCKS5/HTTP proxy. */
    data class Mixed(
        val proxy: LocalProxySettings,
        override val tag: String = XRAY_LOCAL_PROXY_INBOUND_TAG,
    ) : XrayInbound

    /** Listens on [listenPath], the core's own name for the socket the app reaches at [path]. */
    data class PrivateHttp(
        val path: String,
        override val tag: String = XRAY_APP_HTTP_INBOUND_TAG,
        val listenPath: String = path,
    ) : XrayInbound
}

fun XrayInbound.toJson(): JsonObject = when (this) {
    is XrayInbound.Tun -> buildTunInbound(name, tag, mtu)
    is XrayInbound.Tproxy -> buildTproxyInbound(port, tag, allowIpv6, acceptNonLoopback)
    is XrayInbound.PrivateHttp -> buildPrivateHttpInbound(listenPath, tag)
    is XrayInbound.Mixed -> buildMixedInbound(proxy.listenAddress, proxy.port, proxy.username, proxy.password, tag)
}

const val XRAY_APP_HTTP_INBOUND_TAG = "mxray-http-in"

const val XRAY_LOCAL_PROXY_INBOUND_TAG = "mxray-proxy-in"

/** Private HTTP inbound that generated configs always route to `proxy`, for the live latency reading. */
const val XRAY_LATENCY_PROBE_INBOUND_TAG = "mxray-probe-in"
