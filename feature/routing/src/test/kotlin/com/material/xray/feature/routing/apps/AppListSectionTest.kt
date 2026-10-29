package com.material.xray.feature.routing.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListSectionTest {
    @Test
    fun `always proxied with a server stays in the always proxied group`() {
        assertEquals(AppListSection.AlwaysProxied, app("Server", AppRouteKind.SERVER, alwaysProxied = true).listSection)
        assertEquals(AppListSection.AlwaysProxied, app("Default", alwaysProxied = true).listSection)
        assertEquals(AppListSection.SpecificServer, app("Server", AppRouteKind.SERVER).listSection)
    }

    @Test
    fun `changed groups follow picker order with defaults last`() {
        val apps = listOf(
            app("Default"),
            app("Server", AppRouteKind.SERVER),
            app("Always", alwaysProxied = true),
            app("Bypass", AppRouteKind.BYPASS),
            app("Direct", AppRouteKind.DIRECT),
        )
        assertEquals(listOf("Direct", "Bypass", "Always", "Server", "Default"), apps.sortedBy { it.listSection.ordinal }.map { it.name })
    }

    @Test
    fun `recent apps use install time rather than route priority and exclude unknown dates`() {
        val apps = (1..7).map { app("App$it", firstInstallTime = it.toLong()) } + app("Unknown", AppRouteKind.DIRECT)
        assertEquals(listOf("App7", "App6", "App5", "App4", "App3"), apps.recentlyInstalled().map { it.name })
    }

    private fun app(name: String, kind: AppRouteKind = AppRouteKind.DEFAULT, alwaysProxied: Boolean = false, firstInstallTime: Long = 0) = AppItem(
        appKey = name,
        packageName = name,
        name = name,
        uid = 10000,
        icon = null,
        systemApp = false,
        profileId = 0,
        workProfile = false,
        routeKey = kind.name,
        routeKind = kind,
        alwaysProxied = alwaysProxied,
        customRouted = kind != AppRouteKind.DEFAULT || alwaysProxied,
        routeTitle = AppRouteText.Raw("Route"),
        routeDescription = AppRouteText.Raw(""),
        firstInstallTime = firstInstallTime,
    )
}
