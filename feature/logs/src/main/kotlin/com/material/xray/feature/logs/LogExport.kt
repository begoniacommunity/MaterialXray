package com.material.xray.feature.logs

import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import com.material.xray.core.common.log.displayMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun logExportSnapshot(entries: List<LogEntry>, selectedIds: Set<Long>, source: LogSource? = null): List<LogEntry> = when {
    selectedIds.isNotEmpty() -> entries.filter { it.id in selectedIds }
    source != null -> entries.filter { it.source == source }
    else -> entries.toList()
}

internal fun logExportFileName(source: LogSource?, selected: Boolean): String {
    val suffix = when {
        selected -> "selected"
        source == LogSource.APP -> "internal-only"
        source == LogSource.XRAY -> "core-only"
        else -> "all"
    }
    return "material-xray-logs-$suffix.txt"
}

internal fun formatLogEntries(entries: List<LogEntry>): String {
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    return entries.joinToString("\n") { entry ->
        "${timeFormat.format(Date(entry.timestamp))} [${entry.source.tag}] ${entry.displayMessage}"
    }
}
