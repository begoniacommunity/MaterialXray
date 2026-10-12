package com.material.xray.feature.logs

import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LogVisibleEntriesTest {
    private val entries = listOf(
        LogEntry(10, source = LogSource.APP, message = "Connection failed"),
        LogEntry(30, source = LogSource.XRAY, message = "2026/10/12 10:00:00.000 FAILED to dial"),
        LogEntry(50, source = LogSource.APP, message = "Connected"),
    )

    @Test
    fun `select all search results excludes other tabs and nonmatching lines`() {
        val visible = visibleLogEntries(entries, LogSource.APP, "  FAILED  ")
        assertEquals(listOf(entries[0]), visible)
        val selectedIds = visible.mapTo(mutableSetOf(), LogEntry::id)
        assertEquals(visible, logExportSnapshot(entries, selectedIds))
    }

    @Test
    fun `blank query includes every line in the active tab and all tab preserves order`() {
        assertEquals(listOf(entries[0], entries[2]), visibleLogEntries(entries, LogSource.APP, " "))
        assertEquals(entries, visibleLogEntries(entries, null, ""))
        assertEquals(listOf(entries[0], entries[1]), visibleLogEntries(entries, null, "failed"))
        assertEquals(emptyList<LogEntry>(), visibleLogEntries(entries, null, "2026/10/12"))
    }
}
