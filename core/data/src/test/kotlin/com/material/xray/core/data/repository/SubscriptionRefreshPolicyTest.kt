package com.material.xray.core.data.repository

import com.material.xray.core.database.entity.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRefreshPolicyTest {
    @Test
    fun `next alarm follows a manual refresh and failure backoff`() {
        val subscription = SubscriptionEntity(name = "Sub", url = "https://example.com", lastUpdated = 1000, autoUpdateIntervalSeconds = 86400)
        assertEquals(86_401_000L, subscription.nextAutoRefreshAt())
        assertEquals(90_001_000L, subscription.copy(lastUpdated = 3_601_000L).nextAutoRefreshAt())
        assertEquals(94_000_000L, subscription.copy(lastAutoRefreshFailureAt = 90_400_000L).nextAutoRefreshAt())
        assertNull(subscription.copy(autoUpdateIntervalSeconds = 0).nextAutoRefreshAt())
        assertNull(subscription.copy(isLocal = true).nextAutoRefreshAt())
    }

    @Test
    fun `custom interval keeps minute and second precision`() {
        val subscription = SubscriptionEntity(name = "Sub", url = "https://example.com", lastUpdated = 1000, autoUpdateIntervalHours = 1, autoUpdateIntervalSeconds = 61)
        assertFalse(subscription.isDueForRefresh(nowMillis = 61_999))
        assertTrue(subscription.isDueForRefresh(nowMillis = 62_000))
        assertFalse(subscription.copy(autoUpdateIntervalSeconds = 0).isDueForRefresh(nowMillis = 62_000))
    }

    @Test
    fun invalidatedSubscriptionIsDueEvenWhenAutomaticUpdatesAreDisabled() {
        val subscription = SubscriptionEntity(
            name = "Sub",
            url = "https://example.com",
            lastUpdated = 0,
            autoUpdateIntervalHours = 0,
        )

        assertTrue(subscription.isDueForRefresh(nowMillis = 1_000))
    }

    @Test
    fun currentManualSubscriptionIsNotDue() {
        val subscription = SubscriptionEntity(
            name = "Sub",
            url = "https://example.com",
            lastUpdated = 1_000,
            autoUpdateIntervalHours = 0,
        )

        assertFalse(subscription.isDueForRefresh(nowMillis = 2_000))
    }

    @Test
    fun failedAutomaticRefreshWaitsAnHourBeforeRetrying() {
        val subscription = SubscriptionEntity(
            name = "Sub",
            url = "https://example.com",
            lastUpdated = 1_000,
            lastAutoRefreshFailureAt = 3_601_000,
            autoUpdateIntervalHours = 1,
        )

        assertFalse(subscription.isDueForRefresh(nowMillis = 3_601_000 + 59 * 60_000))
        assertTrue(subscription.isDueForRefresh(nowMillis = 3_601_000 + 60 * 60_000))
    }

    @Test
    fun failedInvalidatedSubscriptionIsBackedOffEvenWhenAutomaticUpdatesAreDisabled() {
        val subscription = SubscriptionEntity(
            name = "Sub",
            url = "https://example.com",
            lastUpdated = 0,
            lastAutoRefreshFailureAt = 1_000,
            autoUpdateIntervalHours = 0,
        )

        assertFalse(subscription.isDueForRefresh(nowMillis = 2_000))
        assertTrue(subscription.isDueForRefresh(nowMillis = 3_601_000))
    }

    @Test
    fun successfulRefreshClearsTheEffectOfAnEarlierFailure() {
        val subscription = SubscriptionEntity(
            name = "Sub",
            url = "https://example.com",
            lastUpdated = 10_000_000,
            lastAutoRefreshFailureAt = 9_000_000,
            autoUpdateIntervalHours = 1,
        )

        assertTrue(subscription.isDueForRefresh(nowMillis = 13_600_000))
    }
}
