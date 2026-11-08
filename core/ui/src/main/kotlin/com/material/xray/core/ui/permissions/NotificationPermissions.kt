package com.material.xray.core.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

// Android answers a permanently denied POST_NOTIFICATIONS request without a prompt and without a
// rationale, exactly like one that was never asked, so the app remembers having asked.

fun Context.recordNotificationPermissionRequest() {
    getSharedPreferences(NOTIFICATION_PERMISSION_PREFS, Context.MODE_PRIVATE).edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply()
}

fun Context.wasNotificationPermissionRequested(): Boolean = getSharedPreferences(NOTIFICATION_PERMISSION_PREFS, Context.MODE_PRIVATE).getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)

/** Opens the app's notification settings, or its app info page where Android has none. */
fun Context.openNotificationSettings() {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        startActivity(appDetails)
        return
    }
    val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(notificationSettings) }
        .onFailure { startActivity(appDetails) }
}

private const val NOTIFICATION_PERMISSION_PREFS = "notification_permission"
private const val NOTIFICATION_PERMISSION_REQUESTED = "requested"
