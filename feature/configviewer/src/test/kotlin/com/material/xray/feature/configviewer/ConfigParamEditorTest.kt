package com.material.xray.feature.configviewer

import com.material.xray.core.model.Protocol
import com.material.xray.core.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigParamEditorTest {
    @Test
    fun `confirming an already pinned certificate preserves the other rotation pins`() {
        val first = "ab".repeat(32)
        val second = "cd".repeat(32)
        assertEquals("$first, $second", confirmedCertificatePins("$first, $second", first))
        assertEquals(second, confirmedCertificatePins(first, second))
    }

    @Test
    fun `certificate pin edits are validated normalized and preserved`() {
        val config = fullConfig()
        val pin = "ab".repeat(32)
        val rebuilt = config.toEditSections().edit(EditKey.SecurityCertificatePin, pin.uppercase().chunked(2).joinToString(":"))
            .toServerConfig(config).valid()
        assertEquals(listOf(pin), rebuilt.security.pinnedPeerCertSha256)
        assertEquals(listOf(pin), rebuilt.toEditSections().toServerConfig(rebuilt).valid().security.pinnedPeerCertSha256)
        assertEquals(EditValidationError.INVALID_CERTIFICATE_PIN, config.toEditSections().edit(EditKey.SecurityCertificatePin, "invalid").toServerConfig(config).invalid())
        assertEquals(emptyList<String>(), rebuilt.toEditSections().edit(EditKey.SecurityCertificatePin, "").toServerConfig(rebuilt).valid().security.pinnedPeerCertSha256)
    }

    @Test
    fun `certificate inspection uses current draft address and SNI only for supported TLS profiles`() {
        val values = mapOf(EditKey.Protocol to "VLESS", EditKey.SecurityType to "tls", EditKey.Address to "127.0.0.1", EditKey.Port to "8443", EditKey.SecuritySni to "tls.example")
        assertEquals("tls.example", certificateEndpoint(values)?.serverName)
        assertEquals("127.0.0.1", certificateEndpoint(values + (EditKey.SecuritySni to ""))?.serverName)
        assertEquals(8443, certificateEndpoint(values)?.port)
        assertNull(certificateEndpoint(values + (EditKey.SecurityType to "reality")))
        assertNull(certificateEndpoint(values + (EditKey.Protocol to "HYSTERIA2")))
        assertNull(certificateEndpoint(values + (EditKey.Port to "0")))
    }

    @Test
    fun `an untouched field set rebuilds an identical config`() {
        val config = fullConfig()

        val rebuilt = config.toEditSections().toServerConfig(config).valid()

        assertEquals(config, rebuilt)
    }

    @Test
    fun `blank optional fields are shown so they can be filled in`() {
        val config = fullConfig().copy(security = ServerConfig.Security(type = "tls"))

        val fields = config.toEditSections().flatMap { it.fields }.associateBy { it.key }

        assertEquals("", fields.getValue(EditKey.SecuritySni).value)
        assertEquals("", fields.getValue(EditKey.SecurityShortId).value)
    }

    @Test
    fun `editing a field writes it back`() {
        val config = fullConfig()

        val rebuilt = config.toEditSections()
            .edit(EditKey.Address, "new.example.com")
            .edit(EditKey.Port, "8443")
            .toServerConfig(config)
            .valid()

        assertEquals("new.example.com", rebuilt.address)
        assertEquals(8443, rebuilt.port)
    }

    @Test
    fun `alpn is split on commas and trimmed`() {
        val config = fullConfig()

        val rebuilt = config.toEditSections()
            .edit(EditKey.SecurityAlpn, " h2 , http/1.1 ,")
            .toServerConfig(config)
            .valid()

        assertEquals(listOf("h2", "http/1.1"), rebuilt.security.alpn)
    }

    @Test
    fun `blank extra values are dropped`() {
        val config = fullConfig()

        val rebuilt = config.toEditSections()
            .edit(EditKey.Extra("flow"), "  ")
            .toServerConfig(config)
            .valid()

        assertNull(rebuilt.extra["flow"])
    }

    @Test
    fun `a port outside the valid range is rejected`() {
        val config = fullConfig()

        listOf("0", "70000", "", "https").forEach { badPort ->
            assertEquals(
                "port $badPort should be rejected",
                EditValidationError.INVALID_PORT,
                config.toEditSections().edit(EditKey.Port, badPort).toServerConfig(config).invalid(),
            )
        }
    }

    @Test
    fun `a blank address is rejected`() {
        val config = fullConfig()

        val error = config.toEditSections().edit(EditKey.Address, "   ").toServerConfig(config).invalid()

        assertEquals(EditValidationError.MISSING_ADDRESS, error)
    }

    @Test
    fun `a blank name is rejected`() {
        val config = fullConfig()

        val error = config.toEditSections().edit(EditKey.Name, "").toServerConfig(config).invalid()

        assertEquals(EditValidationError.MISSING_NAME, error)
    }

    @Test
    fun `the protocol dropdown value is applied`() {
        val config = fullConfig()

        val rebuilt = config.toEditSections()
            .edit(EditKey.Protocol, Protocol.TROJAN.name)
            .toServerConfig(config)
            .valid()

        assertEquals(Protocol.TROJAN, rebuilt.protocol)
    }

    @Test
    fun `fields the editor does not expose are carried over`() {
        val config = fullConfig().copy(rawUri = "vless://original", rawConfigJson = "{}")

        val rebuilt = config.toEditSections()
            .edit(EditKey.Name, "Renamed")
            .toServerConfig(config)
            .valid()

        assertEquals("vless://original", rebuilt.rawUri)
        assertEquals("{}", rebuilt.rawConfigJson)
    }

    private fun EditOutcome.valid(): ServerConfig = (this as EditOutcome.Valid).config

    private fun EditOutcome.invalid(): EditValidationError = (this as EditOutcome.Invalid).error

    private fun List<EditSection>.edit(key: EditKey, value: String): List<EditSection> = map { section ->
        section.copy(fields = section.fields.map { if (it.key == key) it.copy(value = value) else it })
    }

    private fun fullConfig() = ServerConfig(
        protocol = Protocol.VLESS,
        name = "Tokyo",
        address = "example.com",
        port = 443,
        password = "uuid-here",
        transport = ServerConfig.Transport(type = "ws", path = "/ray", host = "cdn.example.com"),
        security = ServerConfig.Security(
            type = "tls",
            sni = "example.com",
            fingerprint = "chrome",
            alpn = listOf("h2"),
        ),
        extra = mapOf("flow" to "xtls-rprx-vision"),
    )
}
