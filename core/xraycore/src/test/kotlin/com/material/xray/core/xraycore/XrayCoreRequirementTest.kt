package com.material.xray.core.xraycore

import com.material.xray.core.model.XrayCoreRequirement
import com.material.xray.core.xray.XrayPaths
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XrayCoreRequirementTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val paths: XrayPaths
        get() = object : XrayPaths {
            override val filesDir: File = temporary.root
            override val cacheDir: File = temporary.root
            override val nativeLibraryDir: File? = null
        }

    @Test
    fun `picks the newest release in range including prereleases`() = runTest {
        val pages = listOf(page(listOf(release("v26.10.30", prerelease = true), release("v26.10.25"), release("v26.10.10"), release("v26.9.30"))))

        val found = findNewestRelease(XrayCoreRequirement(min = "26.10.10", max = "26.10.30")) { pages[it - 1] }

        assertEquals("v26.10.30", found?.tag)
    }

    @Test
    fun `pages back to a release below the maximum`() = runTest {
        val pages = listOf(
            page(listOf(release("v26.12.1"), release("v26.11.20")), hasMore = true),
            page(listOf(release("v26.10.25"), release("v26.10.10")), hasMore = true),
        )
        val fetched = mutableListOf<Int>()

        val found = findNewestRelease(XrayCoreRequirement(max = "26.10.31")) {
            fetched += it
            pages[it - 1]
        }

        assertEquals("v26.10.25", found?.tag)
        assertEquals(listOf(1, 2), fetched)
    }

    @Test
    fun `stops once releases are older than the minimum`() = runTest {
        val fetched = mutableListOf<Int>()

        val found = findNewestRelease(XrayCoreRequirement(min = "27.1.1")) {
            fetched += it
            page(listOf(release("v26.10.25")), hasMore = true)
        }

        assertNull(found)
        assertEquals(listOf(1), fetched)
    }

    @Test
    fun `gives up after the page limit`() = runTest {
        val fetched = mutableListOf<Int>()

        val found = findNewestRelease(XrayCoreRequirement(max = "26.1.1"), maxPages = 3) {
            fetched += it
            page(listOf(release("v26.10.25")), hasMore = true)
        }

        assertNull(found)
        assertEquals(listOf(1, 2, 3), fetched)
    }

    @Test
    fun `cached releases expire after a day and are keyed by range`() {
        val requirement = XrayCoreRequirement(min = "26.10.10")
        val day = 24L * 60 * 60 * 1000
        XrayCoreRequirementCache(paths, "arm64-v8a").put(requirement, release("v26.10.25"), now = 0)
        XrayCoreRequirementCache(paths, "arm64-v8a").put(XrayCoreRequirement(max = "26.1.1"), release("v26.1.1"), now = 0)

        val reopened = XrayCoreRequirementCache(paths, "arm64-v8a")
        assertEquals("v26.10.25", reopened.get(requirement, now = day - 1)?.release?.tag)
        assertNull(reopened.get(requirement, now = day))
        assertNull(reopened.get(XrayCoreRequirement(min = "26.10.11"), now = 1))
        assertEquals("v26.1.1", reopened.get(XrayCoreRequirement(max = "26.1.1"), now = 1)?.release?.tag)
        assertNull(XrayCoreRequirementCache(paths, "x86_64").get(requirement, now = 1))
    }

    private fun page(releases: List<XrayCoreRelease>, hasMore: Boolean = false) = XrayCoreReleasePage(releases, hasMore)

    private fun release(tag: String, prerelease: Boolean = false) = XrayCoreRelease(
        tag = tag,
        publishedAt = "2026-10-01T00:00:00Z",
        prerelease = prerelease,
        assetUrl = "https://github.com/XTLS/Xray-core/releases/download/$tag/Xray-android-arm64-v8a.zip",
        assetSha256 = "ab".repeat(32),
    )
}
