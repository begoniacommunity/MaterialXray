package com.material.xray.feature.routing

import com.material.xray.core.data.parser.ProfileRoutingInspector
import com.material.xray.core.model.RoutingRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRuleSearchTest {
    private val rule = RoutingRule(
        id = "custom",
        name = "Москва services",
        outboundTag = "direct",
        domains = listOf("domain:first.example", "domain:second.example", "geosite:category-ru"),
        ips = listOf("geoip:ru", "192.0.2.0/24"),
        port = "1000-2000",
        protocols = listOf("quic"),
        enabled = false,
    )

    @Test
    fun `finds names regardless of case and surrounding whitespace`() {
        assertTrue(rule.matchesSearch("  МОСКВА  "))
        assertTrue(rule.matchesSearch("SERVICES"))
        assertFalse(rule.matchesSearch("missing"))
    }

    @Test
    fun `finds full conditions including entries omitted from card previews`() {
        for (query in listOf("category-ru", "geoip:ru", "192.0.2", "1000", "QUIC", "direct")) {
            assertTrue("Expected a match for $query", rule.matchesSearch(query))
        }
    }

    @Test
    fun `empty queries include disabled rules and displayed names are searchable`() {
        assertTrue(rule.matchesSearch(""))
        assertTrue(rule.matchesSearch("  "))
        assertTrue(rule.matchesSearch("локальное", displayName = "Локальное правило"))
    }

    @Test
    fun `finds profile balancers and conditions unsupported by the rule editor`() {
        val profileRule = ProfileRoutingInspector.inspect(
            """
            {"routing":{"rules":[{
                "type":"field", "name":"Provider rule", "balancerTag":"world-pool",
                "domain":["geosite:google"], "ip":["geoip:us"], "port":"443",
                "protocol":["tls"], "inboundTag":["tunnel"], "network":"udp"
            }]}}
            """.trimIndent(),
        )!!.rules.single()

        for (query in listOf("PROVIDER", "world-pool", "google", "geoip:us", "443", "TLS", "tunnel", "udp")) {
            assertTrue("Expected a match for $query", profileRule.matchesSearch(query))
        }
        assertFalse(profileRule.matchesSearch("nonexistent"))
    }
}
