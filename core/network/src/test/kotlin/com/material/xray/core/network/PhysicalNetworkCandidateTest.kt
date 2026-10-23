package com.material.xray.core.network

import com.material.xray.core.model.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalNetworkCandidateTest {
    private val wifi = PhysicalNetworkCandidate(1, NetworkKind.Wifi, false, true, true)
    private val mobile = PhysicalNetworkCandidate(2, NetworkKind.Mobile, false, true, true)
    private val vpn = PhysicalNetworkCandidate(3, NetworkKind.Wifi, true, true, true)

    @Test
    fun `rootless VPN default is ignored and WiFi is preferred over standby mobile`() {
        assertEquals(wifi, listOf(mobile, vpn, wifi).preferredPhysicalNetwork(vpn.id))
    }

    @Test
    fun `actual physical default wins even with other validated networks present`() {
        assertEquals(mobile, listOf(wifi, mobile).preferredPhysicalNetwork(mobile.id))
    }

    @Test
    fun `unvalidated WiFi does not displace working mobile under VPN`() {
        assertEquals(mobile, listOf(wifi.copy(validated = false), mobile, vpn).preferredPhysicalNetwork(vpn.id))
    }

    @Test
    fun `lost or VPN-only networks are never classified as mobile`() {
        assertNull(listOf(vpn, wifi.copy(hasInternet = false)).preferredPhysicalNetwork(null))
    }
}
