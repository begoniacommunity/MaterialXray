package com.material.xray.core.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.material.xray.core.database.AppDatabase
import com.material.xray.core.database.entity.ServerEntity
import com.material.xray.core.database.entity.SubscriptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionProfileDnsOverrideTest {
    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val subscriptions = database.subscriptionDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `override is stored on the server's subscription only`() = runTest {
        val first = subscriptions.insert(SubscriptionEntity(name = "First", url = "https://one.example"))
        val second = subscriptions.insert(SubscriptionEntity(name = "Second", url = "https://two.example"))
        val (firstServer, secondServer) = database.serverDao().insertAll(listOf(server(first), server(second)))

        subscriptions.setUseProfileDnsOverrideForServer(firstServer, false)

        assertEquals(false, subscriptions.observeUseProfileDnsOverrideForServer(firstServer).first())
        assertNull(subscriptions.observeUseProfileDnsOverrideForServer(secondServer).first())

        subscriptions.setUseProfileDnsOverrideForServer(firstServer, null)

        assertNull(subscriptions.getById(first)?.useProfileDnsOverride)
    }

    private fun server(subscriptionId: Long) = ServerEntity(
        subscriptionId = subscriptionId,
        name = "Server",
        protocol = "VLESS",
        address = "example.com",
        port = 443,
        configJson = "{}",
        sortOrder = 0,
    )
}
