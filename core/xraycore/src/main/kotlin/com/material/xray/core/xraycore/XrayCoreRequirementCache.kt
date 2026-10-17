package com.material.xray.core.xraycore

import com.material.xray.core.model.XrayCoreRequirement
import com.material.xray.core.xray.XrayPaths
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The newest release found for each requirement, kept for a day so connecting does not list
 * releases again. A changed requirement is a different key, so it is looked up afresh.
 */
internal class XrayCoreRequirementCache(paths: XrayPaths, private val abi: String) {
    private val file = File(paths.cacheDir, "xray-core-requirements.json")
    private var entries = read()

    /** The cached release for [requirement], or null when it is not cached. */
    @Synchronized
    fun get(requirement: XrayCoreRequirement, now: Long): Entry? = entries.firstOrNull { it.requirement == requirement && it.isFresh(now) }

    @Synchronized
    fun put(requirement: XrayCoreRequirement, release: XrayCoreRelease, now: Long) {
        entries = entries.filter { it.requirement != requirement && it.isFresh(now) } + Entry(requirement, release, now)
        // Cache failure must not turn a successful lookup into an error.
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(Snapshot.serializer(), Snapshot(abi, entries)))
            if (!temporary.renameTo(file)) temporary.delete()
        }
    }

    private fun read(): List<Entry> = runCatching {
        json.decodeFromString(Snapshot.serializer(), file.readText())
            .takeIf { it.abi == abi }?.entries.orEmpty()
    }.getOrDefault(emptyList())

    @Serializable
    data class Entry(val requirement: XrayCoreRequirement, val release: XrayCoreRelease, val fetchedAt: Long) {
        fun isFresh(now: Long): Boolean = now - fetchedAt in 0 until MAX_AGE_MILLIS
    }

    @Serializable
    private data class Snapshot(val abi: String, val entries: List<Entry>)

    private companion object {
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
        val json = Json { ignoreUnknownKeys = true }
    }
}
