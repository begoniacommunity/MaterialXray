package com.material.xray.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.material.xray.core.common.platform.PlatformInfo
import com.material.xray.core.data.parser.SubscriptionDeviceIdentity
import com.material.xray.core.data.parser.SubscriptionFetchException
import com.material.xray.core.data.parser.SubscriptionFetcher
import com.material.xray.core.database.AppDatabase
import com.material.xray.core.network.DirectHttpClient
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubscriptionJsonFallbackTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `adding a subscription takes the saved URL when json answers with a server error`() = runBlocking {
        withRepository { repository, database, _, server ->
            val id = requireNotNull(repository.add("Links", URL))

            assertEquals(listOf("/sub/base64/json", "/sub/base64"), server.requests)
            assertEquals(listOf("Link"), database.serverDao().getBySubscription(id).map { it.name })
        }
    }

    @Test
    fun `adding after hardware id consent takes the saved URL when json answers with a server error`() = runBlocking {
        withRepository { repository, database, settings, server ->
            settings.setSubscriptionSendHardwareId(false)
            server.requiresHardwareId = true

            val id = requireNotNull(repository.add("Links", URL) { true })

            assertEquals(listOf("/sub/base64/json", "/sub/base64/json", "/sub/base64"), server.requests)
            assertEquals(listOf("Link"), database.serverDao().getBySubscription(id).map { it.name })
        }
    }

    @Test
    fun `refresh takes the saved URL while the subscription holds no json profiles`() = runBlocking {
        withRepository { repository, database, _, server ->
            val id = requireNotNull(repository.add("Links", URL))
            server.requests.clear()

            repository.commitRefresh(requireNotNull(repository.prepareRefresh(id, URL)))

            assertEquals(listOf("/sub/base64/json", "/sub/base64"), server.requests)
            assertEquals(listOf("Link"), database.serverDao().getBySubscription(id).map { it.name })
        }
    }

    @Test
    fun `refresh takes json profiles from the saved URL when json answers with a server error`() = runBlocking {
        withRepository { repository, database, _, server ->
            server.savedUrlServesJson = true
            val id = requireNotNull(repository.add("Json", URL))

            repository.commitRefresh(requireNotNull(repository.prepareRefresh(id, URL)))

            assertEquals(listOf("Json profile"), database.serverDao().getBySubscription(id).map { it.name })
        }
    }

    @Test
    fun `refresh fails and keeps the json profiles when only share links are left`() = runBlocking {
        withRepository { repository, database, _, server ->
            server.jsonEndpointWorks = true
            val id = requireNotNull(repository.add("Json", URL))
            assertEquals(listOf("Json profile"), database.serverDao().getBySubscription(id).map { it.name })
            server.jsonEndpointWorks = false

            val error = runCatching { repository.prepareRefresh(id, URL) }.exceptionOrNull()

            assertEquals(HTTP_BAD_GATEWAY, (error as SubscriptionFetchException).statusCode)
            assertEquals(listOf("Json profile"), database.serverDao().getBySubscription(id).map { it.name })
        }
    }

    /** Serves a share link list or Xray JSON on the saved URL, and Xray JSON or a 502 on its /json sibling. */
    private class FakeServer {
        val requests = mutableListOf<String>()
        var savedUrlServesJson = false
        var jsonEndpointWorks = false
        var requiresHardwareId = false

        fun respond(request: Request): Response.Builder {
            val path = request.url.encodedPath
            requests += path
            return when {
                requiresHardwareId && request.header("x-hwid") == null ->
                    response(HTTP_FORBIDDEN, "text/plain", "").header("subscription-always-hwid-enable", "true")

                !path.endsWith("/json") && !savedUrlServesJson -> response(HTTP_OK, "text/plain", "vless://uuid@link.example:443#Link")
                !path.endsWith("/json") || jsonEndpointWorks -> response(HTTP_OK, "application/json", JSON_PROFILES)
                else -> response(HTTP_BAD_GATEWAY, "text/plain", "")
            }.request(request)
        }

        private fun response(code: Int, contentType: String, body: String) = Response.Builder()
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Fake")
            .header("content-type", contentType)
            .body(body.toResponseBody(contentType.toMediaType()))
    }

    private suspend fun withRepository(block: suspend (SubscriptionRepository, AppDatabase, SettingsRepository, FakeServer) -> Unit) {
        val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(queryDispatcher)
            .build()
        val settings = SettingsRepository(PreferenceDataStores.settings { File(folder.root, "settings.preferences_pb") }) {}
        val server = FakeServer()
        val client = OkHttpClient.Builder().addInterceptor { chain -> server.respond(chain.request()).build() }.build()
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
            block(
                SubscriptionRepository(database, database.subscriptionDao(), database.serverDao(), SubscriptionFetcher(DirectHttpClient(client), identity, platform), settings),
                database,
                settings,
                server,
            )
        } finally {
            database.close()
            queryDispatcher.close()
        }
    }

    private companion object {
        const val URL = "https://example.com/sub/base64"
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val HTTP_BAD_GATEWAY = 502
        const val JSON_PROFILES =
            """[{"remarks":"Json profile","outbounds":[{"protocol":"trojan","settings":{"servers":[{"address":"json.example","port":443,"password":"secret"}]}}]}]"""
    }
}
