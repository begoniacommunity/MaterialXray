package com.material.xray.core.xraycore

import com.material.xray.core.common.log.LogBuffer
import com.material.xray.core.common.platform.JvmPlatformInfo
import com.material.xray.core.common.platform.PlatformInfo
import com.material.xray.core.model.XrayCoreRequirement
import com.material.xray.core.network.AppHttpClient
import com.material.xray.core.network.DirectHttpClient
import com.material.xray.core.xray.InstalledXrayCore
import com.material.xray.core.xray.XrayCoreCandidate
import com.material.xray.core.xray.XrayCoreSource
import com.material.xray.core.xray.XrayCoreStore
import com.material.xray.core.xray.XrayPaths
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XrayCoreRequirementProvisionerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val requirement = XrayCoreRequirement(min = "26.10.10")

    private val paths: XrayPaths
        get() = object : XrayPaths {
            override val filesDir: File = temporary.root
            override val cacheDir: File = temporary.root
            override val nativeLibraryDir: File? = null
        }

    @Test
    fun `the newest release in range wins over an older installed core in range`() = runTest {
        install("v26.10.10")
        val provisioner = provisioner(releases("v26.10.25", "v26.10.10", "v26.9.30"))

        assertEquals(XrayCoreCandidate("26.10.25", needsDownload = true), provisioner.candidate(requirement, lookUp = true))
    }

    @Test
    fun `an installed newest release is selected without downloading`() = runTest {
        install("v26.10.25")
        var requests = 0
        val provisioner = provisioner(releases("v26.10.25", "v26.9.30", onRequest = { requests++ }))

        val candidate = XrayCoreCandidate("26.10.25", needsDownload = false)
        assertEquals(candidate, provisioner.candidate(requirement, lookUp = true))
        assertTrue(provisioner.switchTo(requirement, candidate))
        assertEquals("v26.10.25", XrayCoreStore(paths).selectedId())
        // The second lookup came from the cache.
        assertEquals(1, requests)
    }

    @Test
    fun `without the release list the newest installed core in range is used`() = runTest {
        install("v26.9.30")
        install("v26.10.10")
        install("v26.10.12")
        val offline = object : AppHttpClient {
            override suspend fun <T> use(block: suspend (OkHttpClient) -> T): T = throw IOException("offline")
        }
        val provisioner = provisioner(offline)

        val candidate = XrayCoreCandidate("26.10.12", needsDownload = false)
        assertEquals(candidate, provisioner.candidate(requirement, lookUp = true))
        assertTrue(provisioner.switchTo(requirement, candidate))
        assertEquals("v26.10.12", XrayCoreStore(paths).selectedId())
    }

    @Test
    fun `nothing in range offline leaves the core alone`() = runTest {
        install("v26.9.30")
        val offline = object : AppHttpClient {
            override suspend fun <T> use(block: suspend (OkHttpClient) -> T): T = throw IOException("offline")
        }
        val provisioner = provisioner(offline)

        assertEquals(null, provisioner.candidate(requirement, lookUp = true))
        assertFalse(provisioner.switchTo(requirement, XrayCoreCandidate("26.10.10", needsDownload = true)))
        assertEquals(null, XrayCoreStore(paths).selectedId())
    }

    @Test
    fun `without a lookup only a cached release is offered`() = runTest {
        install("v26.10.10")
        var requests = 0
        val provisioner = provisioner(releases("v26.10.25", onRequest = { requests++ }))

        assertEquals(XrayCoreCandidate("26.10.10", needsDownload = false), provisioner.candidate(requirement, lookUp = false))
        assertEquals(0, requests)
        provisioner.candidate(requirement, lookUp = true)
        assertEquals(XrayCoreCandidate("26.10.25", needsDownload = true), provisioner.candidate(requirement, lookUp = false))
    }

    @Test
    fun `switching installs what was offered even once a newer release is known`() = runTest {
        install("v26.10.10")
        var requests = 0
        val provisioner = provisioner(releases("v26.10.25", onRequest = { requests++ }))

        // The dialog offered the installed core before the list was fetched.
        assertTrue(provisioner.switchTo(requirement, XrayCoreCandidate("26.10.10", needsDownload = false)))
        assertEquals("v26.10.10", XrayCoreStore(paths).selectedId())
        assertEquals(0, requests)
    }

    @Test
    fun `switching refuses a download that is no longer the answer`() = runTest {
        val provisioner = provisioner(releases("v26.10.25"))

        assertFalse(provisioner.switchTo(requirement, XrayCoreCandidate("26.10.12", needsDownload = true)))
        assertEquals(null, XrayCoreStore(paths).selectedId())
    }

    @Test
    fun `a release not out yet is looked up again`() = runTest {
        var requests = 0
        val provisioner = provisioner(releases("v26.9.30", onRequest = { requests++ }))

        provisioner.candidate(requirement, lookUp = true)
        provisioner.candidate(requirement, lookUp = true)

        assertEquals(2, requests)
    }

    @Test
    fun `turning requirements off makes the provisioner manual`() = runTest {
        val provisioner = provisioner(releases())
        assertTrue(provisioner.automatic)
        XrayCoreUpdateSettingsStore(paths).update { it.copy(followProviderRequirements = false) }
        assertFalse(provisioner(releases()).automatic)
    }

    private fun install(tag: String) {
        val store = XrayCoreStore(paths)
        val staging = store.createStagingDir()
        store.stagedExecutable(staging).writeText("core")
        store.commit(staging, InstalledXrayCore(tag, tag.removePrefix("v"), "ab".repeat(32), XrayCoreSource.Release))
    }

    private fun TestScope.provisioner(httpClient: AppHttpClient): XrayCoreRequirementProvisioner {
        val platformInfo = object : PlatformInfo by JvmPlatformInfo {
            override val primaryAbi = "arm64-v8a"
        }
        val log = LogBuffer()
        val manager = XrayCoreManager(paths, httpClient, platformInfo, log, backgroundScope)
        return XrayCoreRequirementProvisioner(manager, XrayCoreUpdateSettingsStore(paths), log, backgroundScope, paths, platformInfo)
    }

    private fun releases(vararg tags: String, onRequest: () -> Unit = {}): AppHttpClient {
        val body = tags.joinToString(",", "[", "]") { tag ->
            val name = "Xray-android-arm64-v8a.zip"
            """
                {"tag_name":"$tag","published_at":"2026-10-01T00:00:00Z","prerelease":false,"draft":false,
                 "assets":[{"name":"$name","browser_download_url":"https://github.com/XTLS/Xray-core/releases/download/$tag/$name",
                 "digest":"sha256:${"ab".repeat(32)}"}]}
            """.trimIndent()
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                onRequest()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        return DirectHttpClient(client)
    }
}
