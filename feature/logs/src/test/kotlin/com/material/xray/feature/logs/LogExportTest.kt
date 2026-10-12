package com.material.xray.feature.logs

import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LogExportTest {
    private val entries = listOf(
        LogEntry(10, source = LogSource.APP, message = "First"),
        LogEntry(30, source = LogSource.XRAY, message = "2026/10/12 10:00:00.000 Second"),
        LogEntry(50, source = LogSource.APP, message = "Third"),
    )

    @Test
    fun `selected export includes only requested existing lines in their original order`() {
        assertEquals(listOf(entries[0], entries[2]), logExportSnapshot(entries, setOf(50L, 10L, 90L)))
        assertEquals(emptyList<LogEntry>(), logExportSnapshot(entries, setOf(90L)))
    }

    @Test
    fun `export snapshot survives clearing or changing the live list`() {
        val live = entries.toMutableList()
        val snapshot = logExportSnapshot(live, emptySet())
        live.clear()
        assertEquals(entries, snapshot)
    }

    @Test
    fun `export uses displayed messages and one line per record`() {
        val snapshot = logExportSnapshot(entries, setOf(10L, 30L))
        assertEquals(listOf("[APP] First", "[XRAY] Second"), formatLogEntries(snapshot).lines().map { it.substringAfter(' ') })
    }
}
