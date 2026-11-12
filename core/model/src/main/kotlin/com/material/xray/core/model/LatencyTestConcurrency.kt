package com.material.xray.core.model

/**
 * How many servers a latency test probes at once. Every httping probe starts its own Xray core, and
 * starting many at once saturated slow phones' CPUs badly enough to ANR the UI.
 */
object LatencyTestConcurrency {
    const val DEFAULT = 10
    const val MIN = 1
    const val MAX = 50

    fun isValid(value: Int): Boolean = value in MIN..MAX

    fun normalize(value: Int?): Int = value?.takeIf(::isValid) ?: DEFAULT
}
