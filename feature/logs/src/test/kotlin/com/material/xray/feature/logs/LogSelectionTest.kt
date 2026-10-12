package com.material.xray.feature.logs

import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSelectionTest {
    private val entries = listOf(
        LogEntry(10, source = LogSource.APP, message = "First"),
        LogEntry(30, source = LogSource.XRAY, message = "Second"),
        LogEntry(50, source = LogSource.APP, message = "Third"),
    )

    @Test
    fun `all selected depends on visible ids rather than the size of the selection`() {
        assertTrue(areAllLogEntriesSelected(entries, setOf(50L, 10L, 30L)))
        assertFalse(areAllLogEntriesSelected(entries, setOf(10L)))
        assertFalse(areAllLogEntriesSelected(entries, setOf(10L, 30L, 90L)))
    }

    @Test
    fun `hidden entries do not prevent all visible results from being selected`() {
        val visible = visibleLogEntries(entries, LogSource.APP, "")
        assertTrue(areAllLogEntriesSelected(visible, setOf(10L, 50L)))
        assertTrue(areAllLogEntriesSelected(visible, setOf(10L, 30L, 50L)))
        assertFalse(areAllLogEntriesSelected(visible, emptySet()))
    }

    @Test
    fun `empty results are not all selected and new rows require selecting again`() {
        assertFalse(areAllLogEntriesSelected(emptyList(), emptySet()))
        val selected = setOf(10L, 30L, 50L)
        assertTrue(areAllLogEntriesSelected(entries, selected))
        assertFalse(areAllLogEntriesSelected(entries + LogEntry(70, source = LogSource.APP, message = "Fourth"), selected))
    }
}
