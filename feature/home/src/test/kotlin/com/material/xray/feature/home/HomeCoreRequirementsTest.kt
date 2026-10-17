package com.material.xray.feature.home

import com.material.xray.core.model.Protocol
import com.material.xray.core.model.ServerConfig
import com.material.xray.core.model.XrayCoreRequirement
import com.material.xray.core.xray.InstalledXrayCore
import com.material.xray.core.xray.XrayActiveCore
import com.material.xray.core.xray.XrayCoreCandidate
import com.material.xray.core.xray.XrayCoreProvisioner
import com.material.xray.core.xray.XrayCoreSource
import com.material.xray.core.xray.XrayCoreStore
import com.material.xray.core.xray.XrayPaths
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeCoreRequirementsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val requirement = XrayCoreRequirement(min = "26.10.10")
    private val server = ServerConfig(Protocol.VLESS, "Server", "example.com", 443, "id", subscriptionCoreRequirement = requirement)
    private val candidate = XrayCoreCandidate("26.10.25", needsDownload = true)
    private val downloads = mutableListOf<String>()
    private val io = UnconfinedTestDispatcher()

    private lateinit var activeCore: XrayActiveCore

    @Before
    fun selectOldCore() {
        val paths = object : XrayPaths {
            override val filesDir: File = temporary.root
            override val cacheDir: File = temporary.root
            override val nativeLibraryDir: File? = null
        }
        val store = XrayCoreStore(paths)
        val staging = store.createStagingDir()
        store.stagedExecutable(staging).writeText("core")
        store.commit(staging, InstalledXrayCore("v26.9.30", "26.9.30", "", XrayCoreSource.Release))
        store.select("v26.9.30")
        activeCore = XrayActiveCore(paths)
    }

    @Test
    fun `servers in range or without a range connect`() = runTest {
        val requirements = HomeCoreRequirements(activeCore, FakeProvisioner(automatic = true), io)

        assertNull(requirements.check(server.copy(subscriptionCoreRequirement = null), null, downloads::add))
        assertNull(requirements.check(server.copy(subscriptionCoreRequirement = XrayCoreRequirement(max = "26.9.30")), null, downloads::add))
    }

    @Test
    fun `following requirements switches without asking`() = runTest {
        val provisioner = FakeProvisioner(automatic = true)

        assertNull(HomeCoreRequirements(activeCore, provisioner, io).check(server, null, downloads::add))
        assertEquals(listOf(true), provisioner.lookUps)
        assertEquals(listOf(candidate), provisioner.switched)
        assertEquals(listOf("26.10.25"), downloads)
    }

    @Test
    fun `a failed switch falls back to asking`() = runTest {
        val prompt = HomeCoreRequirements(activeCore, FakeProvisioner(automatic = true, switches = false), io)
            .check(server, switchToServerId = 7, onDownload = downloads::add)

        assertEquals(CoreRequirementPrompt(server, 7, requirement, "26.9.30", candidate), prompt)
    }

    @Test
    fun `not following asks with what is already known`() = runTest {
        val provisioner = FakeProvisioner(automatic = false)

        val prompt = HomeCoreRequirements(activeCore, provisioner, io).check(server, null, downloads::add)

        assertEquals(candidate, prompt?.candidate)
        assertEquals(listOf(false), provisioner.lookUps)
        assertEquals(emptyList<XrayCoreCandidate>(), provisioner.switched)
    }

    @Test
    fun `builds without the core manager can only warn`() = runTest {
        val prompt = HomeCoreRequirements(activeCore, provisioner = null, ioDispatcher = io).check(server, null, downloads::add)

        assertEquals(CoreRequirementPrompt(server, null, requirement, "26.9.30", candidate = null), prompt)
    }

    private inner class FakeProvisioner(override val automatic: Boolean, private val switches: Boolean = true) : XrayCoreProvisioner {
        val lookUps = mutableListOf<Boolean>()
        val switched = mutableListOf<XrayCoreCandidate>()

        override fun prefetch(requirement: XrayCoreRequirement) = Unit

        override suspend fun candidate(requirement: XrayCoreRequirement, lookUp: Boolean): XrayCoreCandidate {
            lookUps += lookUp
            return candidate
        }

        override suspend fun switchTo(requirement: XrayCoreRequirement, candidate: XrayCoreCandidate): Boolean {
            switched += candidate
            return switches
        }
    }
}
