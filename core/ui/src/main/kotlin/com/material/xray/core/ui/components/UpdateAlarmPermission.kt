package com.material.xray.core.ui.components

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.material.xray.core.ui.R

@Composable
fun rememberUpdateAlarmPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return {
        requestUpdateAlarmPermission(
            sdkInt = Build.VERSION.SDK_INT,
            canScheduleExactAlarms = { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
            launch = {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                // Package visibility can hide a handler from resolveActivity without blocking launch.
                try {
                    launcher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.update_alarm_permission_unavailable, Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

internal fun requestUpdateAlarmPermission(sdkInt: Int, canScheduleExactAlarms: () -> Boolean, launch: () -> Unit) {
    if (sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) launch()
}
