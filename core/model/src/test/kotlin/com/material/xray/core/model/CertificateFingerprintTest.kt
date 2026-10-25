package com.material.xray.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CertificateFingerprintTest {
    @Test
    fun `accepts OpenSSL hex and multiple pins while rejecting malformed values`() {
        val pin = "ab".repeat(32)
        assertEquals(listOf(pin), parseCertificateFingerprints(pin.uppercase().chunked(2).joinToString(":")))
        assertEquals(listOf(pin, "cd".repeat(32)), parseCertificateFingerprints("$pin, ${"cd".repeat(32)}"))
        assertEquals(listOf(pin), parseCertificateFingerprints(",$pin,,"))
        listOf("deadbeef", "g".repeat(64), ",").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseCertificateFingerprints(it) }
        }
        assertEquals(emptyList<String>(), parseCertificateFingerprints(""))
    }

    @Test
    fun `serialized server config keeps pins and old backups retain normal trust`() {
        val server = ServerConfig(Protocol.VLESS, "Test", "example.com", 443, "id", security = ServerConfig.Security(type = "tls", pinnedPeerCertSha256 = listOf("ab".repeat(32))))
        assertEquals(server, Json.decodeFromString<ServerConfig>(Json.encodeToString(ServerConfig.serializer(), server)))
        val old = Json.decodeFromString<ServerConfig>("""{"protocol":"VLESS","name":"Test","address":"example.com","port":443,"password":"id","security":{"type":"tls"}}""")
        assertEquals(emptyList<String>(), old.security.pinnedPeerCertSha256)
    }
}
