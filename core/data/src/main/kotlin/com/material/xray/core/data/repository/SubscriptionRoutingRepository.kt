package com.material.xray.core.data.repository

import com.material.xray.core.database.dao.SubscriptionDao
import com.material.xray.core.model.SubscriptionRouting
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Singleton

data class SubscriptionRoutingState(val subscriptionId: Long?, val routing: SubscriptionRouting)

@Singleton
class SubscriptionRoutingRepository(
    private val settingsRepository: SettingsRepository,
    private val subscriptionDao: SubscriptionDao,
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend fun apply(routing: SubscriptionRouting): Boolean {
        val target = routing.normalized()
        val current = SubscriptionRouting(
            rules = settingsRepository.customRoutingRules.first(),
            domainStrategy = settingsRepository.customRoutingDomainStrategy.first(),
            domainMatcher = settingsRepository.customRoutingDomainMatcher.first(),
            fallbackOutboundTag = settingsRepository.customRoutingFallbackOutbound.first()?.tag,
        ).normalized()
        val customChanged = current != target
        if (customChanged) settingsRepository.setCustomRouting(target)
        // The copy replaces kept subscription-wide rules instead of applying alongside them twice.
        val subscriptionCleared = clear()
        return customChanged || subscriptionCleared
    }

    suspend fun applyForSubscription(subscriptionId: Long): Boolean {
        val subscription = subscriptionDao.getById(subscriptionId) ?: return false
        val routing = subscription.toSubscriptionRouting() ?: return false
        return replaceActiveRouting(routing, subscriptionId)
    }

    suspend fun clear(): Boolean = replaceActiveRouting(null)

    suspend fun resetForSubscription(subscriptionId: Long, expectedSourceId: Long?): Boolean = subscriptionRepository.withRefreshLock(subscriptionId) {
        val routing = subscriptionDao.getById(subscriptionId)?.toSubscriptionRouting() ?: return@withRefreshLock false
        settingsRepository.resetSubscriptionRouting(routing, subscriptionId, expectedSourceId)
    }

    private suspend fun replaceActiveRouting(routing: SubscriptionRouting?, subscriptionId: Long? = null): Boolean {
        val target = routing?.normalized() ?: SubscriptionRouting(emptyList())
        val current = settingsRepository.subscriptionRoutingState.first()
        val changed = current.routing != target
        if (changed || current.subscriptionId != subscriptionId) settingsRepository.setSubscriptionRouting(target, subscriptionId)
        return changed
    }
}
