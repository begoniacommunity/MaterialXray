package com.material.xray.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingOrderTest {
    @Test
    fun `category order defines the exact profile insertion point`() {
        val custom = listOf(rule("custom-1"), rule("custom-2"))
        val subscription = listOf(rule("provider"))
        for (order in permutations(RoutingRuleScope.entries)) {
            val ordered = orderRoutingRules(custom, subscription, order)
            val expected = order.flatMap {
                if (it == RoutingRuleScope.Custom) {
                    custom
                } else if (it == RoutingRuleScope.Subscription) {
                    subscription
                } else {
                    emptyList()
                }
            }
            assertEquals(expected, ordered.rules)
            assertEquals(order.takeWhile { it != RoutingRuleScope.Profile }.sumOf { if (it == RoutingRuleScope.Custom) 2 else 1 }, ordered.profileInsertIndex)
        }
    }

    @Test
    fun `partial reorder preserves hidden and newly added rules`() {
        assertEquals(listOf("c", "hidden", "a", "new"), listOf("a", "hidden", "c", "new").reorderedBy(listOf("c", "removed", "a")) { it })
        assertEquals(RoutingRuleScope.entries, RoutingRuleScope.decode("invalid,custom,custom"))
    }

    @Test
    fun `profile reorder preserves disabled edits and hidden default slots`() {
        val a = rawRule("a")
        val b = rawRule("b")
        val fallback = """{"network":"tcp,udp","outboundTag":"proxy"}"""
        val raw = config(a, fallback, b)
        val edited = ProfileRoutingOverride(a, 0, replacement = rule("edited"), enabled = false)
        val reordered = ProfileRoutingOverrideEngine.reorder(raw, listOf(edited), listOf(ProfileRoutingOverride(b, 2), ProfileRoutingOverride(a, 0)))
        assertEquals(listOf("b", "proxy"), outputs(ProfileRoutingOverrideEngine.apply(raw, reordered)))
        val enabled = ProfileRoutingOverrideEngine.replace(reordered, a, 0, rule("edited"), true)
        assertEquals(listOf("b", "proxy", "edited"), outputs(ProfileRoutingOverrideEngine.apply(raw, enabled)))
    }

    @Test
    fun `reordering after refresh rebases overrides and matches JSON semantically`() {
        val a = rawRule("a")
        val b = rawRule("b")
        val reorderedJsonA = """{"outboundTag":"a","domain":["shared.example"],"type":"field"}"""
        val stale = ProfileRoutingOverride(a, 0, replacement = rule("edited"), position = 1)
        val raw = config(rawRule("new"), reorderedJsonA, b)
        val reconciled = ProfileRoutingOverrideEngine.reconcile(raw, listOf(stale))
        assertEquals(1, reconciled.single().originalIndex)
        val result = ProfileRoutingOverrideEngine.reorder(raw, listOf(stale), listOf(ProfileRoutingOverride(b, 2), ProfileRoutingOverride(reorderedJsonA, 1)))
        assertEquals(2, result.size)
        assertEquals(listOf("new", "b", "edited"), outputs(ProfileRoutingOverrideEngine.apply(raw, result)))
    }

    private fun rule(id: String) = RoutingRule(id, id, id, domains = listOf("shared.example"))
    private fun rawRule(tag: String): String = """{"type":"field","domain":["shared.example"],"outboundTag":"$tag"}"""
    private fun config(vararg rules: String): String = """{"routing":{"rules":[${rules.joinToString(",")}]}}"""
    private fun outputs(raw: String): List<String> = Json.parseToJsonElement(raw).jsonObject.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject.getValue("outboundTag").jsonPrimitive.content }
    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList()) else values.flatMap { value -> permutations(values - value).map { listOf(value) + it } }
}
