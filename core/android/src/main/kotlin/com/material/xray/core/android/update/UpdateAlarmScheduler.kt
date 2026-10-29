package com.material.xray.core.android.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.await
import com.material.xray.core.model.UpdateInterval
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.annotation.Singleton

/** Alarms wake the update queue; WorkManager keeps network work out of the receiver. */
@Singleton
class UpdateAlarmScheduler(private val context: Context) {
    private val preferences = context.getSharedPreferences("update_alarms", Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(key: String, seconds: Int, workerClass: Class<out ListenableWorker>, firstTriggerAtMillis: Long? = null) = synchronized(scheduleLock) {
        require(UpdateInterval.isValid(seconds, allowManual = true))
        if (seconds == 0) {
            alarmManager.cancel(pendingIntent(key))
            preferences.edit().remove(key).remove("$key:worker").remove("$key:next").apply()
            WorkManager.getInstance(context).cancelUniqueWork("$key:alarm")
            return
        }
        val unchanged = preferences.getInt(key, 0) == seconds && preferences.getString("$key:worker", null) == workerClass.name
        val next = firstTriggerAtMillis ?: if (unchanged) preferences.getLong("$key:next", 0L) else System.currentTimeMillis() + seconds * 1000L
        preferences.edit().putInt(key, seconds).putString("$key:worker", workerClass.name).putLong("$key:next", next).apply()
        arm(key, next)
    }

    suspend fun onAlarm(key: String) {
        val scheduled = synchronized(scheduleLock) {
            val seconds = preferences.getInt(key, 0)
            if (seconds == 0) return
            val workerName = preferences.getString("$key:worker", null) ?: return
            ScheduledAlarm(seconds, workerName, preferences.getLong("$key:next", 0L))
        }
        val seconds = scheduled.seconds
        val workerName = scheduled.workerName
        val workerClass = runCatching { Class.forName(workerName).asSubclass(ListenableWorker::class.java) }.getOrNull() ?: return
        val request = OneTimeWorkRequest.Builder(workerClass)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            .build()
        try {
            WorkManager.getInstance(context).enqueueUniqueWork("$key:alarm", ExistingWorkPolicy.KEEP, request).await()
        } catch (error: Exception) {
            Log.e("UpdateAlarmScheduler", "Unable to enqueue an update", error)
            synchronized(scheduleLock) {
                if (isCurrent(key, scheduled)) arm(key, System.currentTimeMillis() + minOf(seconds, 900) * 1000L)
            }
            return
        }
        synchronized(scheduleLock) {
            if (!isCurrent(key, scheduled)) return
            val next = System.currentTimeMillis() + seconds * 1000L
            preferences.edit().putLong("$key:next", next).apply()
            arm(key, next)
        }
    }

    private fun isCurrent(key: String, scheduled: ScheduledAlarm): Boolean = preferences.getInt(key, 0) == scheduled.seconds &&
        preferences.getString("$key:worker", null) == scheduled.workerName &&
        preferences.getLong("$key:next", 0L) == scheduled.nextAt

    fun restore() = synchronized(scheduleLock) {
        preferences.all.filterValues { it is Int }.keys.forEach { key ->
            arm(key, preferences.getLong("$key:next", 0L))
        }
    }

    private fun arm(key: String, next: Long) {
        val triggerAt = next.coerceAtLeast(System.currentTimeMillis() + 1000L)
        val intent = pendingIntent(key)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
                return
            } catch (_: SecurityException) {
                // Access can be revoked between checking the permission and scheduling.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
    }

    private fun pendingIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, UpdateAlarmReceiver::class.java).setAction(key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private data class ScheduledAlarm(val seconds: Int, val workerName: String, val nextAt: Long)

    private companion object {
        val scheduleLock = Any()
    }
}

class UpdateAlarmReceiver(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(ioDispatcher).launch {
            try {
                val scheduler = UpdateAlarmScheduler(context)
                when (val action = intent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                    -> scheduler.restore()
                    null -> Unit
                    else -> scheduler.onAlarm(action)
                }
            } catch (error: Exception) {
                Log.e("UpdateAlarmReceiver", "Unable to restore update alarms", error)
            } finally {
                pending.finish()
            }
        }
    }
}
