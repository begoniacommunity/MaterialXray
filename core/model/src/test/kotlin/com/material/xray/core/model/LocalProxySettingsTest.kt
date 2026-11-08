package com.material.xray.core.model

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProxySettingsTest {
    private val proxy = LocalProxySettings(port = 10808, username = "Ab3dEf7h", password = "Zz9yXx8w", allowLan = false)

    @Test
    fun `connection URI carries the credentials, host and port`() {
        assertEquals("socks5://Ab3dEf7h:Zz9yXx8w@192.168.1.5:10808", proxy.connectionUri(LocalProxySettings.SOCKS_SCHEME, "192.168.1.5"))
    }

    @Test
    fun `LAN access decides the listen address`() {
        assertEquals("127.0.0.1", proxy.listenAddress)
        assertEquals("0.0.0.0", proxy.copy(allowLan = true).listenAddress)
    }

    @Test
    fun `generated credentials are eight alphanumeric characters`() {
        val random = Random(1)
        val credentials = List(50) { LocalProxySettings.generateCredential(random) }
        credentials.forEach { credential ->
            assertEquals(8, credential.length)
            assertTrue(credential, credential.all { it.isLetterOrDigit() && it.code < 128 })
        }
        assertNotEquals(credentials[0], credentials[1])
    }

    @Test
    fun `custom credentials are limited to letters and digits`() {
        assertTrue(LocalProxySettings.isValidCredential("alice"))
        assertTrue(LocalProxySettings.isValidCredential("a".repeat(32)))
        assertFalse(LocalProxySettings.isValidCredential(""))
        assertFalse(LocalProxySettings.isValidCredential("a".repeat(33)))
        assertFalse(LocalProxySettings.isValidCredential("al:ce"))
        assertFalse(LocalProxySettings.isValidCredential("пароль"))
    }

    @Test
    fun `ports below 1024 fall back to the default`() {
        assertFalse(LocalProxySettings.isValidPort(80))
        assertEquals(LocalProxySettings.DEFAULT_PORT, LocalProxySettings.normalizePort(80))
        assertEquals(20000, LocalProxySettings.normalizePort(20000))
    }
}
