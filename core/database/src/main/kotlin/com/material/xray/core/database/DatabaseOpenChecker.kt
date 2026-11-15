package com.material.xray.core.database

import androidx.room.useWriterConnection
import com.material.xray.core.common.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Singleton

/**
 * Room throws on every access to a database it cannot open, such as one left by a newer schema
 * after a downgrade. Each process entry point that reaches the database checks this first, so
 * the user lands on MainActivity's recovery screen instead of a crash: MaterialXrayApp's startup
 * work, MainActivity, XrayTileService, and XrayService's command failure handling. BootReceiver
 * and the workers catch their own failures.
 */
@Singleton
class DatabaseOpenChecker(
    private val database: AppDatabase,
    private val logger: AppLogger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val readable = MutableStateFlow(false)

    suspend fun canRead(): Boolean = mutex.withLock {
        if (readable.value) return@withLock true
        withContext(ioDispatcher) {
            try {
                // Opening runs Room migrations and schema validation; the query also verifies
                // that the main user-data table can be read before any screen uses it.
                database.useWriterConnection { connection ->
                    connection.usePrepared("SELECT 1 FROM servers LIMIT 1") { it.step() }
                }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Room reports schema and SQLite failures with different exception types.
                logger.e(LOG_TAG, "Unable to open app database", error)
                false
            }
        }.also { readable.value = it }
    }

    /** Suspends until a [canRead] call, such as the recovery screen's retry, finds the database readable. */
    suspend fun awaitReadable() {
        if (!canRead()) readable.first { it }
    }

    private companion object {
        const val LOG_TAG = "DatabaseOpenChecker"
    }
}
