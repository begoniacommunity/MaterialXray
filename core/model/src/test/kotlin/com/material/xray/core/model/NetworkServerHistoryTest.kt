package com.material.xray.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkServerHistoryTest {
    private val policy = NetworkServerPolicy(enabled = true)
    private val wifi = NetworkEnvironment(NetworkKind.Wifi, ssid = "Home")
    private val mobile = NetworkEnvironment(NetworkKind.Mobile)

    @Test
    fun `returning to WiFi restores the outgoing manual selection`() {
        val history = NetworkServerHistory().remember(NetworkKind.Wifi, 10).remember(NetworkKind.Mobile, 20)
        assertEquals(10L, history.serverFor(policy, wifi))
        assertEquals(20L, history.serverFor(policy, mobile))
        assertEquals(11L, history.remember(NetworkKind.Wifi, 11).serverFor(policy, wifi))
    }

    @Test
    fun `explicit SSID and network defaults take precedence over history`() {
        val history = NetworkServerHistory(10, 20)
        val assigned = policy.copy(wifiServerId = 30, mobileServerId = 40, ssidServers = mapOf("Home" to 50))
        assertEquals(50L, history.serverFor(assigned, wifi))
        assertEquals(30L, history.serverFor(assigned, wifi.copy(ssid = "Work")))
        assertEquals(40L, history.serverFor(assigned, mobile))
        assertNull(history.serverFor(assigned.copy(enabled = false), wifi))
    }

    @Test
    fun `missing history offline and unrelated transports keep the current selection`() {
        assertNull(NetworkServerHistory().serverFor(policy, wifi))
        val history = NetworkServerHistory(10, 20)
        assertNull(history.serverFor(policy, NetworkEnvironment()))
        assertNull(history.serverFor(policy, NetworkEnvironment(NetworkKind.Other)))
        assertEquals(history, history.remember(NetworkKind.Offline, 30))
        assertEquals(history, history.remember(NetworkKind.Wifi, -1))
    }

    @Test
    fun `history follows refreshed servers and forgets removed ones`() {
        val history = NetworkServerHistory(10, 20).remap(mapOf(10L to 11L), setOf(11))
        assertEquals(NetworkServerHistory(wifiServerId = 11), history)
    }
}
