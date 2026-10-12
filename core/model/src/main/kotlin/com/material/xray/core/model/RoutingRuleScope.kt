package com.material.xray.core.model

enum class RoutingRuleScope(val value: String) {
    Custom("custom"),
    Subscription("subscription"),
    Profile("profile"),
    ;

    companion object {
        fun normalize(order: List<RoutingRuleScope>): List<RoutingRuleScope> = (order + entries).distinct()

        fun decode(value: String?): List<RoutingRuleScope> = normalize(
            value.orEmpty().split(',').mapNotNull { stored -> entries.firstOrNull { it.value == stored } },
        )
    }
}

data class OrderedRoutingRules(val rules: List<RoutingRule>, val profileInsertIndex: Int)

fun orderRoutingRules(custom: List<RoutingRule>, subscription: List<RoutingRule>, scopes: List<RoutingRuleScope>): OrderedRoutingRules {
    val rules = mutableListOf<RoutingRule>()
    var profileIndex = 0
    RoutingRuleScope.normalize(scopes).forEach { scope ->
        when (scope) {
            RoutingRuleScope.Custom -> rules.addAll(custom)
            RoutingRuleScope.Subscription -> rules.addAll(subscription)
            RoutingRuleScope.Profile -> profileIndex = rules.size
        }
    }
    return OrderedRoutingRules(rules, profileIndex)
}

/** Reorder existing slots without losing rows added while the gesture was in progress. */
fun <T, K> List<T>.reorderedBy(keys: List<K>, key: (T) -> K): List<T> {
    val byKey = associateBy(key)
    val ordered = keys.distinct().mapNotNull(byKey::get).iterator()
    val changedKeys = keys.toSet()
    return map { item -> if (key(item) in changedKeys && ordered.hasNext()) ordered.next() else item }
}
