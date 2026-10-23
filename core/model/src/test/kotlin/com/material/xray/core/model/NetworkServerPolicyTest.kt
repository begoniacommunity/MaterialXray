package com.material.xray.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkServerPolicyTest {
    private val policy = NetworkServerPolicy(true, wifiServerId = 10, mobileServerId = 20, ssidServers = mapOf("Home" to 30))

    @Test
    fun `exact SSID overrides WiFi and unknown SSID uses WiFi default`() {
        assertEquals(30L, policy.serverFor(NetworkEnvironment(NetworkKind.Wifi, ssid = "Home")))
        assertEquals(10L, policy.serverFor(NetworkEnvironment(NetworkKind.Wifi)))
        assertEquals(10L, policy.serverFor(NetworkEnvironment(NetworkKind.Wifi, ssid = "home")))
        assertEquals(20L, policy.serverFor(NetworkEnvironment(NetworkKind.Mobile, ssid = "Home")))
    }

    @Test
    fun `disabled absent and unsupported network settings never select a server`() {
        assertNull(policy.copy(enabled = false).serverFor(NetworkEnvironment(NetworkKind.Wifi)))
        assertNull(policy.serverFor(NetworkEnvironment(NetworkKind.Offline)))
        assertNull(policy.serverFor(NetworkEnvironment(NetworkKind.Other)))
        assertNull(NetworkServerPolicy(enabled = true).serverFor(NetworkEnvironment(NetworkKind.Mobile)))
    }

    @Test
    fun `subscription refresh remaps valid targets and removes deleted targets`() {
        val mapped = policy.remap(mapOf(10L to 11L, 30L to 31L), setOf(11, 31))
        assertEquals(11L, mapped.wifiServerId)
        assertNull(mapped.mobileServerId)
        assertEquals(mapOf("Home" to 31L), mapped.ssidServers)
        assertEquals(11L, mapped.serverFor(NetworkEnvironment(NetworkKind.Wifi, ssid = "removed")))
    }

    @Test
    fun `SSID matching preserves spaces and ignores Android unknown sentinel`() {
        assertEquals(" Home ", normalizedWifiSsid("\" Home \""))
        assertEquals("Home", normalizedWifiSsid("Home"))
        assertNull(normalizedWifiSsid("\"<unknown ssid>\""))
        assertNull(normalizedWifiSsid("0x"))
        assertNull(normalizedWifiSsid(null))
    }
}
