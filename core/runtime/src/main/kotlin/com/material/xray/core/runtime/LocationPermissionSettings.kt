package com.material.xray.core.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.material.xray.core.data.repository.SettingsRepository
import com.material.xray.core.root.RootShell
import com.material.xray.core.root.shellQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Singleton

// Android partitions Linux UIDs into 100,000-wide ranges for each user/profile.
private const val ANDROID_UIDS_PER_USER = 100_000

@Singleton
class LocationPermissionSettings(private val context: Context, private val settings: SettingsRepository, private val rootShell: RootShell) {
    suspend fun open() {
        // The single-permission screen requires a Parcelable UserHandle that am cannot supply.
        // The app-permissions screen supplies its own default user and is safe to open from am.
        // AOSP protects this screen from ordinary apps. An already enabled root mode can
        // open it through the system activity manager without changing any permission.
        if (settings.useRootService.first()) {
            val result = try {
                rootShell.execute(
                    "am start --user ${Process.myUid() / ANDROID_UIDS_PER_USER} -a android.intent.action.MANAGE_APP_PERMISSIONS " +
                        "--es android.intent.extra.PACKAGE_NAME ${shellQuote(context.packageName)}",
                    namespace = RootShell.NetworkNamespace.CURRENT,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (result?.isSuccess == true && !result.output.contains("Error:") && !result.error.contains("Error:")) return
        }
        val intents = listOf(
            Intent("android.settings.APP_PERMISSIONS_SETTINGS").putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
            Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        for (intent in intents) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
}
