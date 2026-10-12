package com.material.xray.feature.logs

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.material.xray.core.android.locale.localizedString
import com.material.xray.core.common.log.LogBuffer
import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import com.material.xray.core.ui.R
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel

private const val LOG_EXPORT_DIRECTORY = "logs"

@KoinViewModel
class LogsViewModel(
    private val context: Application,
    private val logBuffer: LogBuffer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private var exportSnapshot: List<LogEntry>? = null
    val entries: StateFlow<List<LogEntry>> = logBuffer.entries

    fun prepareExport(entries: List<LogEntry>, selectedIds: Set<Long>, source: LogSource?): String {
        exportSnapshot = logExportSnapshot(entries, selectedIds, source)
        return logExportFileName(source, selectedIds.isNotEmpty())
    }

    fun clear() = logBuffer.clear()

    fun copyEntries(entries: List<LogEntry>) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                context.localizedString(R.string.clipboard_label_logs, context.localizedString(R.string.app_name)),
                formatLogEntries(entries),
            ),
        )
    }

    suspend fun saveLogs(destination: Uri) {
        val snapshot = exportSnapshot ?: throw IOException("The log export snapshot is no longer available")
        withContext(ioDispatcher) {
            val outputStream = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Unable to open the selected log file")
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(formatLogEntries(snapshot))
            }
        }
    }

    suspend fun createShareFile(entries: List<LogEntry>, fileName: String): Uri = withContext(ioDispatcher) {
        val exportDirectory = File(context.cacheDir, LOG_EXPORT_DIRECTORY)
        if (!exportDirectory.isDirectory && !exportDirectory.mkdirs()) {
            throw IOException("Unable to create the log export directory")
        }

        val exportFile = File(exportDirectory, fileName)
        exportFile.writeText(formatLogEntries(entries), Charsets.UTF_8)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )
    }
}
