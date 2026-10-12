package com.material.xray.core.xray

import com.material.xray.core.model.Protocol
import com.material.xray.core.model.RoutingGeoData
import com.material.xray.core.model.RoutingRule
import com.material.xray.core.model.RoutingRuleOperator
import com.material.xray.core.model.ServerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPriorityTest {
    private val rawServer = ServerConfig(
        Protocol.RAW,
        "Profile",
        "",
        0,
        "",
        rawConfigJson = """
        {"outbounds":[{"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"server.example","port":443,"users":[{"id":"test"}]}]}},{"tag":"direct","protocol":"freedom"}],
        "routing":{"rules":[{"type":"field","domain":["shared.example"],"outboundTag":"direct"},{"type":"field","network":"tcp,udp","outboundTag":"proxy"}]}}
        """.trimIndent(),
    )

    @Test
    fun `profile priority changes while terminal fallback remains after every category`() {
        val custom = RoutingRule("custom", "Custom", "block", domains = listOf("shared.example"))
        val subscription = RoutingRule("subscription", "Subscription", "proxy", domains = listOf("shared.example"))
        val rules = listOf(custom, subscription)
        for ((index, expected) in listOf(0 to listOf("direct", "block", "proxy"), 1 to listOf("block", "direct", "proxy"), 2 to listOf("block", "proxy", "direct"))) {
            val routing = generatedRules(rules, index)
            assertEquals(expected, routing.filter { it["domain"]?.jsonArray?.any { domain -> domain.jsonPrimitive.content == "shared.example" } == true }.map { it.getValue("outboundTag").jsonPrimitive.content })
            assertEquals("tcp,udp", routing.last().getValue("network").jsonPrimitive.content)
        }
    }

    @Test
    fun `OR expansion and per-app copies stay ahead of profile according to the rule boundary`() {
        val rule = RoutingRule("or", "OR", "proxy", domains = listOf("first.example"), ips = listOf("192.0.2.1"), operator = RoutingRuleOperator.OR)
        val appServer = ServerConfig(Protocol.VLESS, "App", "app.example", 443, "test")
        val route = AppProxyRoute("app-1", 1, "app-proxy", appServer, applyRoutingRules = true)
        val routing = generatedRules(listOf(rule), 1, listOf(route))
        val profileIndex = routing.indexOfFirst { it["domain"]?.jsonArray?.any { domain -> domain.jsonPrimitive.content == "shared.example" } == true }
        assertEquals(4, routing.take(profileIndex).count { it["domain"]?.jsonArray?.any { domain -> domain.jsonPrimitive.content == "first.example" } == true || it["ip"]?.jsonArray?.any { ip -> ip.jsonPrimitive.content == "192.0.2.1" } == true })
        assertEquals("app-proxy", routing.last().getValue("outboundTag").jsonPrimitive.content)
        assertTrue(routing.first().containsKey("port"))
    }

    @Test
    fun `dropping provider geodata rules adjusts the profile boundary even with duplicate ids across categories`() {
        val dropped = RoutingRule("same", "Dropped", "proxy", domains = listOf("geosite:missing"), geoData = RoutingGeoData(geositeUrl = "https://provider.example/geosite.dat"))
        val custom = dropped.copy(name = "Custom", domains = listOf("shared.example"), outboundTag = "block")
        val original = listOf(dropped, custom)
        val resolution = resolveProviderGeoData(original, emptySet(), emptySet()) { null }
        assertEquals(listOf(1), resolution.sourceIndices)
        assertEquals(0, resolution.profileInsertIndex(original, 1))
        val routing = generatedRules(resolution.rules, resolution.profileInsertIndex(original, 1))
        assertEquals(listOf("direct", "block"), routing.filter { it["domain"]?.jsonArray?.any { domain -> domain.jsonPrimitive.content == "shared.example" } == true }.map { it.getValue("outboundTag").jsonPrimitive.content })
    }

    private fun generatedRules(rules: List<RoutingRule>, index: Int, appRoutes: List<AppProxyRoute> = emptyList()) = Json.parseToJsonElement(
        ConfigGenerator().generate(rawServer, routingRules = rules, profileRoutingInsertIndex = index, bypassLan = false, dnsServers = "", appProxyRoutes = appRoutes),
    ).jsonObject.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
}
