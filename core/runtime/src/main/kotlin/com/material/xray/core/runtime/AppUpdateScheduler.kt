package com.material.xray.core.runtime

import android.content.Context
import androidx.work.WorkManager
import com.material.xray.core.android.update.UpdateAlarmScheduler
import com.material.xray.core.common.connection.AppUpdateScheduling
import com.material.xray.service.AppUpdateWorker
import org.koin.core.annotation.Singleton

@Singleton(binds = [AppUpdateScheduling::class])
class AppUpdateScheduler(
    private val context: Context,
    private val alarms: UpdateAlarmScheduler,
) : AppUpdateScheduling {
    override fun setEnabled(enabled: Boolean, intervalSeconds: Int) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("app_update_check_now")
        workManager.cancelUniqueWork("app_update_check")
        alarms.schedule("app_update_check", if (enabled) intervalSeconds else 0, AppUpdateWorker::class.java)
    }
}
