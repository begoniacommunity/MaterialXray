package com.material.xray.core.xray

import com.material.xray.core.model.XrayCoreRequirement
import org.koin.core.annotation.Singleton

/** Reads which core version runs on the next start. */
@Singleton
class XrayActiveCore(paths: XrayPaths) {
    private val binary = XrayBinary(paths)
    private val cores = XrayCoreStore(paths)

    // The bundled core only changes with the APK, and reading its version starts the executable,
    // which can take seconds to time out when it cannot run.
    private val bundledVersion by lazy { binary.readBundledVersion() }

    /** The selected core's version, or the bundled core's; null when it cannot be read. Blocks. */
    fun version(): String? {
        val selectedId = cores.selectedId()
        cores.installed().firstOrNull { it.id == selectedId }?.let { return it.version }
        return bundledVersion
    }
}

/** The core that would satisfy a requirement: an installed one, or a release that has to be downloaded first. */
data class XrayCoreCandidate(val version: String, val needsDownload: Boolean)

/**
 * Switches to a core a subscription asks for. Only builds that may download cores provide one, so
 * it is optional wherever it is injected.
 */
interface XrayCoreProvisioner {
    /** Whether the user lets subscriptions pick the core on their own. */
    val automatic: Boolean

    /** Looks up in the background which core would satisfy [requirement], so connecting need not wait for it. */
    fun prefetch(requirement: XrayCoreRequirement)

    /**
     * The newest core in range, or the newest installed one in range when the release list is not
     * known; null when none is. [lookUp] lets it fetch the list rather than only use a cached one.
     */
    suspend fun candidate(requirement: XrayCoreRequirement, lookUp: Boolean): XrayCoreCandidate?

    /** Selects [candidate], downloading it first if needed; false when that failed or it no longer is the answer. */
    suspend fun switchTo(requirement: XrayCoreRequirement, candidate: XrayCoreCandidate): Boolean
}
