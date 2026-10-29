package com.material.xray.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.material.xray.core.ui.R

@Composable
fun updateIntervalLabel(seconds: Int): String {
    val resources = LocalResources.current
    return formatUpdateIntervalLabel(
        seconds = seconds,
        string = resources::getString,
        quantity = { resource, count -> resources.getQuantityString(resource, count, count) },
        every = { duration -> resources.getString(R.string.update_check_every, duration) },
    )
}

internal fun formatUpdateIntervalLabel(
    seconds: Int,
    string: (Int) -> String,
    quantity: (Int, Int) -> String,
    every: (String) -> String,
): String {
    val single = when (seconds) {
        0 -> R.string.home_duration_manual
        1 -> R.string.update_every_second
        60 -> R.string.update_every_minute
        3600 -> R.string.update_every_hour
        86400 -> R.string.update_every_day
        604800 -> R.string.update_every_week
        2592000 -> R.string.update_every_month
        31536000 -> R.string.update_every_year
        else -> null
    }
    if (single != null) return string(single)
    val duration = when {
        seconds % 604800 == 0 -> quantity(R.plurals.update_duration_weeks, seconds / 604800)
        seconds % 86400 == 0 -> quantity(R.plurals.update_duration_days, seconds / 86400)
        else -> listOf(
            seconds / 3600 to R.plurals.update_duration_hours,
            seconds % 3600 / 60 to R.plurals.update_duration_minutes,
            seconds % 60 to R.plurals.update_duration_seconds,
        ).filter { it.first > 0 }.joinToString(" ") { (count, resource) -> quantity(resource, count) }
    }
    return every(duration)
}
