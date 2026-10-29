package com.material.xray.core.model

object UpdateInterval {
    const val MAX_SECONDS = 365 * 24 * 60 * 60

    fun isValid(seconds: Int, allowManual: Boolean = false): Boolean = seconds in 1..MAX_SECONDS || (allowManual && seconds == 0)

    fun fromHours(hours: Int): Int = (hours.toLong() * 3600).coerceIn(0, MAX_SECONDS.toLong()).toInt()

    fun normalize(seconds: Int?, fallback: Int, allowManual: Boolean = false): Int = seconds?.takeIf { isValid(it, allowManual) } ?: fallback
}
