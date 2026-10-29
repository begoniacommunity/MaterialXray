package com.material.xray.core.ui.components

import com.material.xray.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateIntervalLabelTest {
    @Test
    fun `single units omit the redundant one`() {
        assertEquals("Every second", label(1))
        assertEquals("Every minute", label(60))
        assertEquals("Every hour", label(3600))
        assertEquals("Every day", label(86400))
        assertEquals("Every week", label(604800))
        assertEquals("Every month", label(2592000))
        assertEquals("Every year", label(31536000))
    }

    @Test
    fun `multiple units retain their number and whole weeks use weeks`() {
        assertEquals("Every 2 seconds", label(2))
        assertEquals("Every 2 minutes", label(120))
        assertEquals("Every 12 hours", label(43200))
        assertEquals("Every 3 days", label(259200))
        assertEquals("Every 2 weeks", label(1209600))
        assertEquals("Every 31 days", label(2678400))
    }

    @Test
    fun `mixed intervals keep every nonzero component`() {
        assertEquals("Every 1 hour 1 minute 1 second", label(3661))
        assertEquals("Every 1 hour 1 second", label(3601))
        assertEquals("Every 1 minute 1 second", label(61))
    }

    @Test
    fun `manual updates do not get a recurrence prefix`() {
        assertEquals("Manual", label(0))
    }

    private fun label(seconds: Int) = formatUpdateIntervalLabel(
        seconds = seconds,
        string = { resource ->
            when (resource) {
                R.string.home_duration_manual -> "Manual"
                R.string.update_every_second -> "Every second"
                R.string.update_every_minute -> "Every minute"
                R.string.update_every_hour -> "Every hour"
                R.string.update_every_day -> "Every day"
                R.string.update_every_week -> "Every week"
                R.string.update_every_month -> "Every month"
                R.string.update_every_year -> "Every year"
                else -> error("Unexpected string resource: $resource")
            }
        },
        quantity = { resource, count ->
            val unit = when (resource) {
                R.plurals.update_duration_seconds -> "second"
                R.plurals.update_duration_minutes -> "minute"
                R.plurals.update_duration_hours -> "hour"
                R.plurals.update_duration_days -> "day"
                R.plurals.update_duration_weeks -> "week"
                else -> error("Unexpected plural resource: $resource")
            }
            "$count $unit${if (count == 1) "" else "s"}"
        },
        every = { "Every $it" },
    )
}
