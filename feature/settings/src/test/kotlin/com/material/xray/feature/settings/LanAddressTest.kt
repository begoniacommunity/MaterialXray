package com.material.xray.feature.settings

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanAddressTest {
    @Test
    fun `prefers Wi-Fi over the hotspot and ignores mobile data`() {
        val interfaces = listOf(
            "rmnet_data0" to listOf(ip("10.120.4.7")),
            "ap0" to listOf(ip("192.168.43.1")),
            "wlan0" to listOf(ip("fe80::1"), ip("192.168.1.23")),
        )

        assertEquals("192.168.1.23", pickLanAddress(interfaces))
    }

    @Test
    fun `falls back to the hotspot when there is no Wi-Fi`() {
        assertEquals("192.168.43.1", pickLanAddress(listOf("ap0" to listOf(ip("192.168.43.1")))))
    }

    @Test
    fun `finds nothing on mobile data alone`() {
        assertNull(pickLanAddress(listOf("rmnet_data0" to listOf(ip("10.120.4.7")))))
        assertNull(pickLanAddress(listOf("wlan0" to listOf(ip("100.64.0.2")))))
    }

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)
}
