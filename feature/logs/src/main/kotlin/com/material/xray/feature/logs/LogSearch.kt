package com.material.xray.feature.logs

import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import com.material.xray.core.common.log.displayMessage

internal fun findLogMatches(entries: List<LogEntry>, query: String): List<Long> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return entries.filter { it.displayMessage.contains(needle, ignoreCase = true) }.map(LogEntry::id)
}

/** The shared scope for displayed rows, the result count and Select all. */
internal fun visibleLogEntries(entries: List<LogEntry>, source: LogSource?, query: String): List<LogEntry> {
    val scoped = if (source == null) entries else entries.filter { it.source == source }
    if (query.isBlank()) return scoped
    val matches = findLogMatches(scoped, query).toSet()
    return scoped.filter { it.id in matches }
}

internal fun areAllLogEntriesSelected(entries: List<LogEntry>, selectedIds: Set<Long>): Boolean = entries.isNotEmpty() && entries.all { it.id in selectedIds }
