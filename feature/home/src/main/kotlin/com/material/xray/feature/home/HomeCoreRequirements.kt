package com.material.xray.feature.home

import com.material.xray.core.model.ServerConfig
import com.material.xray.core.model.XrayCoreRequirement
import com.material.xray.core.xray.XrayActiveCore
import com.material.xray.core.xray.XrayCoreCandidate
import com.material.xray.core.xray.XrayCoreProvisioner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Singleton

/** A connection held back because the core is outside the range [server]'s subscription asks for. */
internal data class CoreRequirementPrompt(
    val server: ServerConfig,
    /** The server to switch the running connection to, or null when the user pressed Connect. */
    val switchToServerId: Long?,
    val requirement: XrayCoreRequirement,
    val currentVersion: String,
    /** What the Download or Use button switches to; null when this build cannot or no core is known. */
    val candidate: XrayCoreCandidate?,
)

/**
 * Checks the core against the selected subscription's version range before connecting. The
 * provisioner is missing from builds that may not download cores, which can only warn.
 */
@Singleton
class HomeCoreRequirements(
    private val activeCore: XrayActiveCore,
    private val provisioner: XrayCoreProvisioner?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun prefetch(requirement: XrayCoreRequirement) {
        provisioner?.prefetch(requirement)
    }

    /**
     * Null when [server] may connect now, possibly after switching cores on the user's behalf;
     * otherwise what to ask the user. [onDownload] announces a download before it starts.
     */
    internal suspend fun check(server: ServerConfig, switchToServerId: Long?, onDownload: (String) -> Unit): CoreRequirementPrompt? {
        val requirement = server.subscriptionCoreRequirement ?: return null
        // An unreadable version cannot be judged, and the connection reports a broken core itself.
        val current = withContext(ioDispatcher) { activeCore.version() } ?: return null
        if (requirement.accepts(current)) return null
        val provisioner = provisioner
        // Only a user who lets subscriptions pick the core waits for GitHub here; otherwise the
        // dialog offers what the background lookup already found, or an installed core.
        val candidate = provisioner?.candidate(requirement, lookUp = provisioner.automatic)
        if (provisioner?.automatic == true && candidate != null && switchTo(requirement, candidate, onDownload)) return null
        return CoreRequirementPrompt(server, switchToServerId, requirement, current, candidate)
    }

    /** Switches to [candidate] for [requirement]; false when that failed or this build cannot. */
    suspend fun switchTo(requirement: XrayCoreRequirement, candidate: XrayCoreCandidate, onDownload: (String) -> Unit): Boolean {
        val provisioner = provisioner ?: return false
        if (candidate.needsDownload) onDownload(candidate.version)
        return provisioner.switchTo(requirement, candidate)
    }
}
