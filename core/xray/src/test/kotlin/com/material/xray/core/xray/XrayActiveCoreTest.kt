package com.material.xray.core.xray

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XrayActiveCoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val paths = object : XrayPaths {
        override val filesDir: File get() = temporary.root
        override val cacheDir: File get() = temporary.root
        override val nativeLibraryDir: File? = null
    }

    @Test
    fun `the selected core's manifest names the version`() {
        val store = XrayCoreStore(paths)
        val staging = store.createStagingDir()
        store.stagedExecutable(staging).writeText("core")
        store.commit(staging, InstalledXrayCore("v26.10.10", "26.10.10", "", XrayCoreSource.Release))
        store.select("v26.10.10")

        assertEquals("26.10.10", XrayActiveCore(paths).version())
    }

    @Test
    fun `without a selection the bundled core is read, here missing`() {
        assertNull(XrayActiveCore(paths).version())
    }
}
