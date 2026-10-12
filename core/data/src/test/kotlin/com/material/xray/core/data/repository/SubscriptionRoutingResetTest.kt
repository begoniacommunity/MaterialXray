package com.material.xray.core.data.repository

import com.material.xray.core.model.BackupData
import com.material.xray.core.model.RoutingPolicyControl
import com.material.xray.core.model.RoutingRule
import com.material.xray.core.model.RoutingRuleScope
import com.material.xray.core.model.SubscriptionRouting
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubscriptionRoutingResetTest {
    @get:Rule val folder = TemporaryFolder()
    private val store by lazy { PreferenceDataStores.settings { File(folder.root, "settings.preferences_pb") } }
    private val repository by lazy { SettingsRepository(store) { } }
    private val original = SubscriptionRouting(listOf(rule("first", false), rule("second", true)))

    @Test
    fun `reset restores source rules activity and order while retaining custom routing`() = runTest {
        repository.setSubscriptionRouting(original, 7)
        val custom = listOf(rule("custom", true))
        repository.setRoutingRules(custom)
        val scopes = listOf(RoutingRuleScope.Profile, RoutingRuleScope.Subscription, RoutingRuleScope.Custom)
        repository.setRoutingScopeOrder(scopes)
        repository.setSubscriptionRoutingRule(original.rules.first().copy(enabled = true))
        repository.reorderRoutingRules(RoutingRuleScope.Subscription, listOf("second", "first"))
        assertEquals(7L, repository.subscriptionRoutingState.first().subscriptionId)
        assertTrue(repository.resetSubscriptionRouting(original, 7, 7))
        assertEquals(SubscriptionRoutingState(7, original), repository.subscriptionRoutingState.first())
        assertEquals(custom, repository.customRoutingRules.first())
        assertEquals(scopes, repository.routingScopeOrder.first())
        assertEquals(RoutingPolicyControl.User, repository.routingPolicyControl.first())
        assertFalse(repository.resetSubscriptionRouting(original, 7, 7))
        assertFalse(repository.resetSubscriptionRouting(original, 7, 8))
    }

    @Test
    fun `pending reset cannot replace a different subscription's rules`() = runTest {
        repository.setSubscriptionRouting(original, 8)
        assertFalse(repository.resetSubscriptionRouting(SubscriptionRouting(emptyList()), 7, 7))
        assertEquals(SubscriptionRoutingState(8, original), repository.subscriptionRoutingState.first())
    }

    @Test
    fun `legacy source is bound only to the subscription explicitly chosen for reset`() = runTest {
        repository.setSubscriptionRouting(SubscriptionRouting(listOf(rule("edited", true))))
        assertTrue(repository.resetSubscriptionRouting(original, 7, null))
        assertEquals(SubscriptionRoutingState(7, original), repository.subscriptionRoutingState.first())
        val backup = repository.getAllAsMap()
        repository.restoreFromMap(backup, sourceBackupVersion = BackupData.CURRENT_VERSION)
        assertEquals(SubscriptionRoutingState(7, original), repository.subscriptionRoutingState.first())
    }

    private fun rule(id: String, enabled: Boolean) = RoutingRule(id, id, "proxy", domains = listOf("$id.example"), enabled = enabled)
}
