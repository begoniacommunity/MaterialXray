package com.material.xray.feature.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.RequirementList
import com.material.xray.core.ui.components.RequirementRow
import com.material.xray.core.ui.components.rememberSystemState
import com.material.xray.core.ui.permissions.openNotificationSettings
import com.material.xray.core.ui.permissions.recordNotificationPermissionRequest
import com.material.xray.core.ui.permissions.wasNotificationPermissionRequested

internal fun interface ConnectPermissionGate {
    fun connect(needsVpnConsent: Boolean)
}

/**
 * Wraps connecting so a missing VPN consent or a never-asked notification permission is explained
 * in one checklist first. Connect in that dialog walks through whatever is still missing, one
 * system prompt after another, then connects; declining notifications does not block it.
 */
@Composable
internal fun rememberConnectPermissionGate(onConnect: () -> Unit, onConsentUnavailable: () -> Unit): ConnectPermissionGate {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // Saveable, so a prompt answered after a rotation still finds the flow it belongs to.
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var needsVpn by rememberSaveable { mutableStateOf(false) }
    var connecting by rememberSaveable { mutableStateOf(false) }
    val vpnConsent = rememberSystemState { VpnService.prepare(it) == null }
    val notifications = rememberSystemState(::notificationsGranted)

    fun dismiss() {
        showDialog = false
        connecting = false
    }

    // The launchers always run the callback of the latest composition, so this is assigned by then.
    lateinit var advance: () -> Unit
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vpnConsent.refresh()
        if (connecting && result.resultCode == Activity.RESULT_OK) advance() else connecting = false
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        context.recordNotificationPermissionRequest()
        notifications.refresh()
        if (connecting) advance()
    }
    fun requestConsent(intent: Intent) {
        // Some TV firmwares (VIDAA) strip the system VPN consent dialog.
        try {
            consentLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            dismiss()
            onConsentUnavailable()
        }
    }
    advance = {
        val consent = if (needsVpn) VpnService.prepare(context) else null
        when {
            consent != null -> requestConsent(consent)
            notificationsAskable(context) -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> {
                dismiss()
                onConnect()
            }
        }
    }

    if (showDialog) {
        AppAlertDialog(
            onDismissRequest = ::dismiss,
            icon = { Icon(painterResource(R.drawable.lock_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
            title = { Text(stringResource(R.string.home_connect_permissions_title)) },
            text = {
                RequirementList {
                    if (needsVpn) {
                        RequirementRow(
                            stringResource(R.string.home_connect_permission_vpn),
                            stringResource(R.string.home_connect_permission_vpn_summary),
                            done = vpnConsent.value,
                            onClick = { VpnService.prepare(context)?.let(::requestConsent) },
                        )
                    }
                    if (notificationsRelevant(context)) {
                        RequirementRow(
                            stringResource(R.string.home_connect_permission_notifications),
                            stringResource(R.string.home_connect_permission_notifications_summary),
                            done = notifications.value,
                            onClick = {
                                val promptable = !context.wasNotificationPermissionRequested() ||
                                    (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
                                if (promptable) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.openNotificationSettings()
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    connecting = true
                    advance()
                }) { Text(stringResource(R.string.home_connect_permissions_confirm)) }
            },
            dismissButton = { TextButton(onClick = ::dismiss) { Text(stringResource(R.string.home_action_cancel)) } },
        )
    }

    return ConnectPermissionGate { vpn ->
        if ((vpn && VpnService.prepare(context) != null) || notificationsAskable(context)) {
            needsVpn = vpn
            showDialog = true
        } else {
            onConnect()
        }
    }
}

/** Android asks for notifications from 13 on; a TV has no shade to show them in. */
private fun notificationsRelevant(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

private fun notificationsGranted(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Asked once on connect; after that the notification row only reflects the choice. */
private fun notificationsAskable(context: Context): Boolean = notificationsRelevant(context) && !notificationsGranted(context) && !context.wasNotificationPermissionRequested()
