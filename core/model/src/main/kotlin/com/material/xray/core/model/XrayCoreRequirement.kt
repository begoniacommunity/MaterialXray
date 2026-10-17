package com.material.xray.core.model

import kotlinx.serialization.Serializable

/**
 * The range of Xray core versions a subscription's `mxray-min-core-version` and
 * `mxray-max-core-version` headers ask for. Both bounds are inclusive and normalized, without a `v`.
 */
@Serializable
data class XrayCoreRequirement(
    val min: String? = null,
    val max: String? = null,
) {
    /** Whether core [version] is in range. A version that cannot be compared is let through. */
    fun accepts(version: String): Boolean = (min == null || (compareXrayVersions(version, min) ?: 0) >= 0) &&
        (max == null || (compareXrayVersions(version, max) ?: 0) <= 0)

    companion object {
        /** The range the headers' values describe, ignoring a value that is not a version; null without any. */
        fun of(min: String?, max: String?): XrayCoreRequirement? {
            val requirement = XrayCoreRequirement(min?.toXrayVersionOrNull(), max?.toXrayVersionOrNull())
            return requirement.takeIf { it.min != null || it.max != null }
        }

        private fun String.toXrayVersionOrNull(): String? = normalizeXrayVersion(this).takeIf { compareXrayVersions(it, it) != null }
    }
}

/** Compares the numeric part of two Xray versions, or returns null when either is not numeric. */
fun compareXrayVersions(left: String, right: String): Int? {
    val leftParts = numericVersion(left) ?: return null
    val rightParts = numericVersion(right) ?: return null
    for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val comparison = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}

/** Drops the `v` of a tag such as `v26.9.30`, which `xray version` does not print. */
fun normalizeXrayVersion(version: String): String = version.trim().removePrefix("v")

private fun numericVersion(version: String): List<Int>? {
    val parts = normalizeXrayVersion(version).substringBefore('-').substringBefore('+').split('.')
    if (parts.size < 2) return null
    return parts.map { it.toIntOrNull()?.takeIf { number -> number >= 0 } ?: return null }
}
