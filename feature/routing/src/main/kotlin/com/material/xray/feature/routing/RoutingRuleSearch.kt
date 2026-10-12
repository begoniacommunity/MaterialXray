package com.material.xray.feature.routing

import com.material.xray.core.data.parser.ProfileRoutingRule
import com.material.xray.core.model.RoutingRule

internal fun RoutingRule.matchesSearch(query: String, displayName: String = name): Boolean = routingSearchMatches(
    query,
    sequenceOf(name, displayName, outboundTag, port.orEmpty()) + domains + ips + protocols,
)

internal fun ProfileRoutingRule.matchesSearch(query: String): Boolean = routingSearchMatches(
    query,
    sequenceOf(name, target?.tag.orEmpty(), port.orEmpty(), rawJson) + domains + ips + protocols + additionalConditionFields,
)

private fun routingSearchMatches(query: String, values: Sequence<String>): Boolean {
    val trimmedQuery = query.trim()
    return trimmedQuery.isEmpty() || values.any { it.contains(trimmedQuery, ignoreCase = true) }
}
