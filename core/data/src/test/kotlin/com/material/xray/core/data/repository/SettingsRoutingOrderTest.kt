package com.material.xray.core.data.repository

import com.material.xray.core.model.BackupData
import com.material.xray.core.model.RoutingPolicyControl
import com.material.xray.core.model.RoutingRule
import com.material.xray.core.model.RoutingRuleScope
import com.material.xray.core.model.SubscriptionRouting
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRoutingOrderTest {
    @get:Rule val folder = TemporaryFolder()
    private val store by lazy { PreferenceDataStores.settings { File(folder.root, "settings.preferences_pb") } }
    private val repository by lazy { SettingsRepository(store) { } }

    @Test
    fun `rule and category order persist and survive backup restore`() = runBlocking {
        val custom = listOf(rule("a"), rule("b"))
        val provider = listOf(rule("p1"), rule("p2"))
        repository.setRoutingRules(custom)
        repository.setSubscriptionRouting(SubscriptionRouting(provider))
        repository.reorderRoutingRules(RoutingRuleScope.Custom, listOf("b", "a"))
        repository.reorderRoutingRules(RoutingRuleScope.Subscription, listOf("p2", "p1"))
        val order = listOf(RoutingRuleScope.Subscription, RoutingRuleScope.Profile, RoutingRuleScope.Custom)
        repository.setRoutingScopeOrder(order)
        val snapshot = repository.runtimeSettingsSnapshot()
        assertEquals(listOf("p2", "p1", "b", "a"), snapshot.routingRules.map { it.id })
        assertEquals(2, snapshot.profileRoutingInsertIndex)
        assertEquals(RoutingPolicyControl.User, repository.routingPolicyControl.first())
        val backup = repository.getAllAsMap()
        repository.setRoutingScopeOrder(RoutingRuleScope.entries)
        repository.restoreFromMap(backup, sourceBackupVersion = BackupData.CURRENT_VERSION)
        assertEquals(order, repository.routingScopeOrder.first())
        assertEquals(snapshot.routingRules, repository.runtimeSettingsSnapshot().routingRules)
        assertEquals(2, SettingsRepository(store) { }.runtimeSettingsSnapshot().profileRoutingInsertIndex)
    }

    @Test
    fun `stale drag order does not delete rules added during the gesture`() = runBlocking {
        repository.setRoutingRules(listOf(rule("a"), rule("new"), rule("b")))
        repository.reorderRoutingRules(RoutingRuleScope.Custom, listOf("b", "gone", "a"))
        assertEquals(listOf("b", "new", "a"), repository.customRoutingRules.first().map { it.id })
    }

    private fun rule(id: String) = RoutingRule(id, id, "proxy", domains = listOf("$id.example"))
}
