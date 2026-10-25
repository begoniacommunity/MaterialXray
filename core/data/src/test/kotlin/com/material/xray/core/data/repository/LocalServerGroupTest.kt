package com.material.xray.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.material.xray.core.common.platform.PlatformInfo
import com.material.xray.core.data.parser.SubscriptionDeviceIdentity
import com.material.xray.core.data.parser.SubscriptionFetcher
import com.material.xray.core.database.AppDatabase
import com.material.xray.core.database.entity.ServerEntity
import com.material.xray.core.database.entity.SubscriptionEntity
import com.material.xray.core.network.DirectHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalServerGroupTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `pasted share links collect in one local group that never refreshes`() = runBlocking {
        withRepository { repository, database ->
            val first = repository.addLink("vless://id@one.example:443#One")
            val second = repository.add("Renamed", "trojan://secret@two.example:443#Two")
            val third = repository.addLink("vless://id@three.example:443#Three\n\n  ss://YWVzLTI1Ni1nY206cGFzcw@four.example:8388#Four  ")

            assertEquals(first, second)
            assertEquals(first, third)
            val local = repository.getAllSubscriptions().single()
            assertTrue(local.isLocal)
            assertEquals(0, local.autoUpdateIntervalHours)
            val servers = database.serverDao().getBySubscription(local.id)
            assertEquals(listOf("One", "Renamed", "Three", "Four"), servers.map { it.name })
            assertEquals(listOf(0, 1, 2, 3), servers.map { it.sortOrder })
            assertNull(repository.prepareRefresh(local.id, local.url))
        }
    }

    @Test
    fun `links separated by whitespace or punctuation are added together`() = runBlocking {
        withRepository { repository, database ->
            val id = requireNotNull(
                repository.addLink(
                    "vless://id@a.example:443?alpn=h2,http/1.1#A, trojan://s@b.example:443#B;vless://id@c.example:443#C\r\n" +
                        "\tvless://id@d.example:443#D | vless://id@e.example:443#E vless://id@f.example:443#F,\n" +
                        "ss://YWVzLTI1Ni1nY206cGFzcw@g.example:8388#US 1 vless://id@h.example:443",
                ),
            )
            val servers = database.serverDao().getBySubscription(id)
            assertEquals(listOf("A", "B", "C", "D", "E", "F", "US 1", "h.example:443"), servers.map { it.name })
        }
    }

    @Test
    fun `text with a line that is not a share link is treated as a subscription URL`() = runBlocking {
        withRepository { repository, _ ->
            try {
                repository.addLink("vless://id@one.example:443#One\nnot a link")
                org.junit.Assert.fail("Mixed text must not be added as servers")
            } catch (_: IOException) {
                assertTrue(repository.getAllSubscriptions().isEmpty())
            }
        }
    }

    @Test
    fun `legacy share link subscriptions fold into the local group keeping server ids`() = runBlocking {
        withRepository { repository, database ->
            val subscriptions = database.subscriptionDao()
            val servers = database.serverDao()
            val remote = subscriptions.insertAtEnd(SubscriptionEntity(name = "Provider", url = "https://example.com/sub"))
            val remoteServer = servers.insertAll(listOf(server(remote, "Provider node"))).single()
            // The title shown before wins unless it was automatic; a server is never left nameless.
            val legacyIds = listOf(
                Triple("Subscription 2", "vless://id@a.example:443#A", "A"),
                Triple("Work VPN", "trojan://secret@b.example:443#B", "B"),
                Triple("Subscription 3", "vless://id@nameless.example:443", ""),
            ).map { (title, link, serverName) ->
                val id = subscriptions.insertAtEnd(SubscriptionEntity(name = title, url = link, autoUpdateIntervalHours = 0))
                servers.insertAll(listOf(server(id, serverName).copy(guarded = true))).single()
            }
            // Never folded: a share-link URL that updates, or one holding several servers.
            val updating = subscriptions.insertAtEnd(SubscriptionEntity(name = "Odd", url = "vless://id@c.example:443#C", autoUpdateIntervalHours = 1))
            servers.insertAll(listOf(server(updating, "C")))
            val crowded = subscriptions.insertAtEnd(SubscriptionEntity(name = "Crowded", url = "vless://id@d.example:443#D", autoUpdateIntervalHours = 0))
            servers.insertAll(listOf(server(crowded, "D1"), server(crowded, "D2")))

            repository.consolidateLocalServers()
            repository.consolidateLocalServers()

            val after = repository.getAllSubscriptions()
            assertEquals(listOf("Provider", "", "Odd", "Crowded"), after.map { it.name })
            val local = after.single { it.isLocal }
            val folded = servers.getBySubscription(local.id)
            assertEquals(legacyIds, folded.map { it.id })
            assertEquals(listOf("A", "Work VPN", ".example:443"), folded.map { it.name })
            assertTrue(folded.none { it.guarded })
            assertEquals(remote, requireNotNull(servers.getById(remoteServer)).subscriptionId)
            assertEquals(2, servers.getBySubscription(crowded).size)

            repository.addLink("vless://id@e.example:443#E")
            assertEquals(listOf("A", "Work VPN", ".example:443", "E"), servers.getBySubscription(local.id).map { it.name })
        }
    }

    @Test
    fun `deleting the last local server removes the group`() = runBlocking {
        withRepository { repository, database ->
            val id = requireNotNull(repository.addLink("vless://id@one.example:443#One\nvless://id@two.example:443#Two"))
            val local = requireNotNull(repository.getById(id))
            val (one, two) = database.serverDao().getBySubscription(id).map { it.id }
            repository.deleteLocalServer(local, one)
            assertEquals(listOf(two), database.serverDao().getBySubscription(id).map { it.id })
            repository.deleteLocalServer(local, two)
            assertTrue(repository.getAllSubscriptions().isEmpty())
        }
    }

    private fun server(subscriptionId: Long, name: String) = ServerEntity(
        subscriptionId = subscriptionId,
        name = name,
        protocol = "VLESS",
        address = "$name.example",
        port = 443,
        configJson = "{}",
    )

    private suspend fun withRepository(block: suspend (SubscriptionRepository, AppDatabase) -> Unit) {
        val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(queryDispatcher)
            .build()
        val settings = SettingsRepository(PreferenceDataStores.settings { File(folder.root, "settings.preferences_pb") }) {}
        val client = OkHttpClient.Builder().addInterceptor { throw IOException("No network in this test") }.build()
        val identity = object : SubscriptionDeviceIdentity {
            override fun appVersion() = "test"
            override fun hardwareId() = "test-hwid"
        }
        val platform = object : PlatformInfo {
            override val sdkInt = 34
            override val osName = "Android"
            override val osVersion = "14"
            override val manufacturer = "Test"
            override val model = "Device"
            override val device = "test"
            override val processId = 1
            override val uid = 10123
            override val buildFingerprint = "test"
            override val primaryAbi = "arm64-v8a"
        }
        try {
            val fetcher = SubscriptionFetcher(DirectHttpClient(client), identity, platform)
            block(SubscriptionRepository(database, database.subscriptionDao(), database.serverDao(), fetcher, settings), database)
        } finally {
            database.close()
            queryDispatcher.close()
        }
    }
}
