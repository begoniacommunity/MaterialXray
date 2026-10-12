package com.material.xray.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.material.xray.core.data.parser.ProfileRoutingInspector
import com.material.xray.core.database.AppDatabase
import com.material.xray.core.database.dao.ServerDao
import com.material.xray.core.database.dao.ServerEditsDao
import com.material.xray.core.database.entity.ServerEntity
import com.material.xray.core.database.entity.SubscriptionEntity
import com.material.xray.core.model.ProfileRoutingOverride
import com.material.xray.core.model.Protocol
import com.material.xray.core.model.ServerConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ServerRoutingEditsTest {
    @get:Rule val folder = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true }
    private val firstRule = """{"type":"field","domain":["first.example"],"outboundTag":"proxy"}"""
    private val secondRule = """{"type":"field","domain":["second.example"],"outboundTag":"direct","enabled":false}"""
    private val config = ServerConfig(Protocol.RAW, "Server", "example.com", 443, "", rawConfigJson = """{"routing":{"rules":[$firstRule,$secondRule]}}""")

    @Test
    fun `routing reset restores original activity and order without marking the config edited`() = runTest {
        withServer { repository, id, _, _ ->
            assertTrue(
                repository.updateProfileRouting(id) {
                    listOf(ProfileRoutingOverride(firstRule, 0, enabled = false, position = 1), ProfileRoutingOverride(secondRule, 1, enabled = true, position = 0))
                },
            )
            val modified = repository.parseConfig(requireNotNull(repository.getById(id)))
            assertEquals(false, modified.configurationEdited)
            assertTrue(repository.updateProfileRouting(id) { emptyList() })
            val entity = requireNotNull(repository.getById(id))
            val restored = repository.parseConfig(entity)
            val rules = requireNotNull(ProfileRoutingInspector.inspect(restored)).rules
            assertEquals(listOf("first.example", "second.example"), rules.map { it.domains.single() })
            assertEquals(listOf(true, false), rules.map { it.enabled })
            assertEquals(config.rawConfigJson, restored.rawConfigJson)
            assertFalse(entity.edited)
            assertTrue(entity.guarded)
            assertEquals(123, entity.latencyMs)
        }
    }

    @Test
    fun `saving a config keeps routing edits and resetting routing keeps the config edit`() = runTest {
        withServer { repository, id, _, _ ->
            repository.updateProfileRouting(id) { listOf(ProfileRoutingOverride(firstRule, 0, enabled = false)) }
            repository.saveEditedConfig(id, config.copy(address = "edited.example"))
            val both = repository.parseConfig(requireNotNull(repository.getById(id)))
            assertEquals(true, both.configurationEdited)
            assertEquals(1, both.profileRoutingOverrides.size)
            repository.updateProfileRouting(id) { emptyList() }
            val entity = requireNotNull(repository.getById(id))
            val restored = repository.parseConfig(entity)
            assertEquals("edited.example", restored.address)
            assertEquals(true, restored.configurationEdited)
            assertTrue(entity.edited)
            assertTrue(restored.profileRoutingOverrides.isEmpty())
        }
    }

    @Test
    fun `concurrent config save is preserved when a routing write retries`() = runTest {
        withServer { repository, id, serverDao, editsDao ->
            var intercepted = false
            val racingDao = object : ServerEditsDao by editsDao {
                override suspend fun updateRoutingConfig(id: Long, configJson: String, edited: Boolean, previousConfigJson: String): Int {
                    if (!intercepted) {
                        intercepted = true
                        repository.saveEditedConfig(id, config.copy(address = "concurrent.example"))
                    }
                    return editsDao.updateRoutingConfig(id, configJson, edited, previousConfigJson)
                }
            }
            val racingRepository = ServerRepository(serverDao, racingDao)
            racingRepository.updateProfileRouting(id) { listOf(ProfileRoutingOverride(firstRule, 0, enabled = false)) }
            val restored = repository.parseConfig(requireNotNull(repository.getById(id)))
            assertEquals("concurrent.example", restored.address)
            assertEquals(true, restored.configurationEdited)
            assertEquals(1, restored.profileRoutingOverrides.size)
        }
    }

    @Test
    fun `unchanged routing and missing server do not write a config`() = runTest {
        withServer { repository, id, _, _ ->
            val original = repository.getById(id)
            assertFalse(repository.updateProfileRouting(id) { it.profileRoutingOverrides })
            assertEquals(original, repository.getById(id))
            assertFalse(repository.updateProfileRouting(-1) { emptyList() })
            assertFalse(repository.saveEditedConfig(-1, config))
        }
    }

    private suspend fun withServer(block: suspend (ServerRepository, Long, ServerDao, ServerEditsDao) -> Unit) {
        val database = Room.databaseBuilder<AppDatabase>(folder.root.resolve("test.db").path)
            .setDriver(BundledSQLiteDriver()).build()
        try {
            val subscriptionId = database.subscriptionDao().insert(SubscriptionEntity(name = "Provider", url = "https://example.com/sub"))
            val dao = database.serverDao()
            val id = dao.insertAll(listOf(ServerEntity(subscriptionId = subscriptionId, name = config.name, protocol = config.protocol.name, address = config.address, port = config.port, configJson = json.encodeToString(config), latencyMs = 123, guarded = true))).single()
            val editsDao = database.serverEditsDao()
            block(ServerRepository(dao, editsDao), id, dao, editsDao)
        } finally {
            database.close()
        }
    }
}
