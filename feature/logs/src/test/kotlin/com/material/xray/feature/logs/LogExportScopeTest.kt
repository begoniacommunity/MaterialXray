package com.material.xray.feature.logs

import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LogExportScopeTest {
    private val entries = listOf(
        LogEntry(10, source = LogSource.APP, message = "First"),
        LogEntry(30, source = LogSource.XRAY, message = "Second"),
        LogEntry(50, source = LogSource.APP, message = "Third"),
    )

    @Test
    fun `filenames identify all app xray and selected exports`() {
        assertEquals("material-xray-logs-all.txt", logExportFileName(null, false))
        assertEquals("material-xray-logs-internal-only.txt", logExportFileName(LogSource.APP, false))
        assertEquals("material-xray-logs-core-only.txt", logExportFileName(LogSource.XRAY, false))
        assertEquals("material-xray-logs-selected.txt", logExportFileName(null, true))
        assertEquals("material-xray-logs-selected.txt", logExportFileName(LogSource.XRAY, true))
    }

    @Test
    fun `unselected exports contain all entries from the chosen tab`() {
        assertEquals(entries, logExportSnapshot(entries, emptySet(), null))
        assertEquals(listOf(entries[0], entries[2]), logExportSnapshot(entries, emptySet(), LogSource.APP))
        assertEquals(listOf(entries[1]), logExportSnapshot(entries, emptySet(), LogSource.XRAY))
    }

    @Test
    fun `captured selection takes precedence over a changed tab and stale ids never export all`() {
        assertEquals(listOf(entries[0], entries[2]), logExportSnapshot(entries, setOf(50L, 10L), LogSource.XRAY))
        assertEquals(emptyList<LogEntry>(), logExportSnapshot(entries, setOf(90L), LogSource.APP))
    }
}
