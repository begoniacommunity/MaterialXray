package com.material.xray.feature.routing.apps

import com.material.xray.core.ui.R

internal enum class AppListSection(val titleResource: Int) {
    DefaultOutbound(R.string.apps_route_default_outbound_title),
    NotProxied(R.string.apps_route_not_proxied_title),
    Bypass(R.string.apps_route_bypass_tun_title),
    AlwaysProxied(R.string.apps_route_always_proxied_title),
    SpecificServer(R.string.apps_specific_server_section),
    Default(R.string.apps_route_default_server_title),
}

internal val AppItem.listSection: AppListSection
    get() = when {
        alwaysProxied -> AppListSection.AlwaysProxied
        routeKind == AppRouteKind.INHERIT -> AppListSection.DefaultOutbound
        routeKind == AppRouteKind.DIRECT -> AppListSection.NotProxied
        routeKind == AppRouteKind.BYPASS -> AppListSection.Bypass
        routeKind == AppRouteKind.SERVER -> AppListSection.SpecificServer
        else -> AppListSection.Default
    }

internal fun List<AppItem>.recentlyInstalled(): List<AppItem> = filter { it.firstInstallTime > 0 }
    .sortedWith(compareByDescending<AppItem> { it.firstInstallTime }.thenBy { it.name.lowercase() }.thenBy { it.appKey })
    .take(5)
