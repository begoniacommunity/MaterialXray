package com.material.xray.core.xraycore

import com.material.xray.core.common.di.ApplicationScope
import com.material.xray.core.common.log.LogBuffer
import com.material.xray.core.common.log.LogSource
import com.material.xray.core.common.platform.PlatformInfo
import com.material.xray.core.model.XrayCoreRequirement
import com.material.xray.core.model.compareXrayVersions
import com.material.xray.core.model.normalizeXrayVersion
import com.material.xray.core.xray.XrayCoreCandidate
import com.material.xray.core.xray.XrayCoreProvisioner
import com.material.xray.core.xray.XrayPaths
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Singleton

/**
 * Picks the core a subscription's version range asks for: the current one while it fits, otherwise
 * the newest upstream release in range, falling back to an installed core in range when the list
 * cannot be fetched.
 */
@Singleton(binds = [XrayCoreProvisioner::class])
class XrayCoreRequirementProvisioner(
    private val manager: XrayCoreManager,
    private val settingsStore: XrayCoreUpdateSettingsStore,
    private val log: LogBuffer,
    @ApplicationScope private val scope: CoroutineScope,
    paths: XrayPaths,
    platformInfo: PlatformInfo,
) : XrayCoreProvisioner {
    private val cache = XrayCoreRequirementCache(paths, platformInfo.primaryAbi)
    private val lookup = Mutex()

    override val automatic: Boolean
        get() = manager.canDownload && settingsStore.settings.value.followProviderRequirements

    override fun prefetch(requirement: XrayCoreRequirement) {
        if (!manager.canDownload) return
        scope.launch {
            if (requirement.accepts(loadedState().activeVersion ?: return@launch)) return@launch
            try {
                newestRelease(requirement)
            } catch (_: IOException) {
                // Connecting looks it up again; the failure is already in the app log.
            }
        }
    }

    override suspend fun candidate(requirement: XrayCoreRequirement, lookUp: Boolean): XrayCoreCandidate? = plan(requirement, lookUp)?.candidate

    override suspend fun switchTo(requirement: XrayCoreRequirement, candidate: XrayCoreCandidate): Boolean {
        // Planning again normally hits the cache; the user agreed to this version, so a different answer is a failure.
        val plan = plan(requirement, lookUp = candidate.needsDownload)?.takeIf { it.candidate == candidate } ?: return false
        val id = when (plan) {
            is Plan.Select -> plan.id
            is Plan.Download -> (manager.installAndWait(plan.release) ?: return false).id
        }
        try {
            manager.select(id)
        } catch (error: IOException) {
            log.append(LogSource.APP, "Could not select the Xray core a subscription requires: ${error.message}")
            return false
        }
        log.append(LogSource.APP, "Switched the Xray core to ${candidate.version} as the subscription requires")
        return true
    }

    /** What satisfying [requirement] takes; without [lookUp] only a cached release list is consulted. */
    private suspend fun plan(requirement: XrayCoreRequirement, lookUp: Boolean): Plan? {
        val state = loadedState()
        val release = if (lookUp) {
            try {
                newestRelease(requirement)
            } catch (_: IOException) {
                null
            }
        } else {
            cache.get(requirement, System.currentTimeMillis())?.release
        }
        // The bundled core is a candidate too, selected by clearing the selection.
        val cores = state.installed.map { Plan.Select(it.id, it.version) } +
            listOfNotNull(state.bundledVersion?.let { Plan.Select(null, it) })
        if (release != null) {
            return cores.firstOrNull { compareXrayVersions(it.version, release.tag) == 0 } ?: Plan.Download(release)
        }
        return cores.filter { requirement.accepts(it.version) }
            .maxWithOrNull { left, right -> compareXrayVersions(left.version, right.version) ?: 0 }
    }

    private suspend fun loadedState(): XrayCoreState = manager.state.first { it.loaded }

    /**
     * The newest release in range, or null when none is; throws when the list cannot be fetched. Only
     * a found release is cached: the one a provider asks for may not be out yet, or older than the pages searched.
     */
    private suspend fun newestRelease(requirement: XrayCoreRequirement): XrayCoreRelease? = lookup.withLock {
        val now = System.currentTimeMillis()
        cache.get(requirement, now)?.let { return@withLock it.release }
        findNewestRelease(requirement) { page -> manager.releases(page) }
            ?.also { cache.put(requirement, it, now) }
    }

    private sealed interface Plan {
        val candidate: XrayCoreCandidate

        data class Select(val id: String?, val version: String) : Plan {
            override val candidate get() = XrayCoreCandidate(normalizeXrayVersion(version), needsDownload = false)
        }

        data class Download(val release: XrayCoreRelease) : Plan {
            override val candidate get() = XrayCoreCandidate(normalizeXrayVersion(release.tag), needsDownload = true)
        }
    }
}

/**
 * Pages through releases, newest first, until one is in [requirement]'s range or every later one
 * is older than its minimum. Gives up after [maxPages] pages of ten.
 */
internal suspend fun findNewestRelease(
    requirement: XrayCoreRequirement,
    maxPages: Int = MAX_REQUIREMENT_PAGES,
    fetchPage: suspend (Int) -> XrayCoreReleasePage,
): XrayCoreRelease? {
    for (page in 1..maxPages) {
        val result = fetchPage(page)
        result.releases.firstOrNull { requirement.accepts(it.tag) }?.let { return it }
        val oldest = result.releases.lastOrNull()?.tag
        val min = requirement.min
        val belowMinimum = min != null && oldest != null && (compareXrayVersions(oldest, min) ?: 0) < 0
        if (!result.hasMore || belowMinimum) return null
    }
    return null
}

private const val MAX_REQUIREMENT_PAGES = 5
