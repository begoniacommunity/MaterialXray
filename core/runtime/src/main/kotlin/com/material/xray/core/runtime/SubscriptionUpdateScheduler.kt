package com.material.xray.core.runtime

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.material.xray.core.android.update.UpdateAlarmScheduler
import com.material.xray.service.SubscriptionUpdateWorker
import java.util.concurrent.TimeUnit
import org.koin.core.annotation.Singleton

@Singleton
class SubscriptionUpdateScheduler(
    private val context: Context,
    private val alarms: UpdateAlarmScheduler,
) {
    fun schedulePeriodicUpdates(intervalSeconds: Int, firstTriggerAtMillis: Long? = null) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        alarms.schedule(PERIODIC_WORK_NAME, intervalSeconds, SubscriptionUpdateWorker::class.java, firstTriggerAtMillis)
    }

    fun enqueueDueCheckNow(initialDelaySeconds: Long = 0) {
        val request = OneTimeWorkRequestBuilder<SubscriptionUpdateWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val PERIODIC_WORK_NAME = "subscription_auto_update"
        const val IMMEDIATE_WORK_NAME = "subscription_auto_update_now"
        const val BACKOFF_DELAY_MINUTES = 15L
    }
}
