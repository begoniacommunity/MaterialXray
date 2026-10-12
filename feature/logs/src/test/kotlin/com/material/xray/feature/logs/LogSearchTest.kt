package com.material.xray.feature.logs

import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LogSearchTest {
    private val entries = listOf(
        LogEntry(10, source = LogSource.APP, message = "Connection failed"),
        LogEntry(30, source = LogSource.XRAY, message = "2026/10/12 10:00:00.000 FAILED to dial"),
        LogEntry(50, source = LogSource.APP, message = "Connected"),
    )

    @Test
    fun `search ignores case and whitespace and keeps chronological line ids`() {
        assertEquals(listOf(10L, 30L), findLogMatches(entries, "  failed  "))
        assertEquals(listOf(50L), findLogMatches(entries, "CONNECTED"))
    }

    @Test
    fun `empty searches and hidden xray timestamp prefixes do not produce results`() {
        assertEquals(emptyList<Long>(), findLogMatches(entries, " "))
        assertEquals(emptyList<Long>(), findLogMatches(entries, "2026/10/12"))
        assertEquals(emptyList<Long>(), findLogMatches(entries, "absent"))
    }
}
