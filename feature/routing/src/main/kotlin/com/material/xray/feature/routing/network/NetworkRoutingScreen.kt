package com.material.xray.feature.routing.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.material.xray.core.runtime.NetworkServerStatus
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.ScrollFadeEdges
import com.material.xray.core.ui.components.SelectableOptionRow
import com.material.xray.core.ui.components.TooltipIconButton
import com.material.xray.core.ui.components.rememberOptionValue
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NetworkRoutingContent(contentBottomPadding: Dp, viewModel: NetworkRoutingViewModel = koinViewModel()) {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val network by viewModel.network.collectAsStateWithLifecycle()
    val choices by viewModel.choices.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val activeOverrideSsid by viewModel.activeOverrideSsid.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var askingLocation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permissionsRevision by remember { mutableIntStateOf(0) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionsRevision++
        viewModel.refreshNetwork()
        // Android answers a permanently denied request without a prompt; only app settings can grant it then.
        val denied = results.filterValues { !it }.keys
        if (activity != null && denied.isNotEmpty() && denied.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
            viewModel.openLocationPermissions()
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsRevision++
                viewModel.refreshNetwork()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (askingLocation) {
        // Quick settings toggles location without pausing the activity.
        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    permissionsRevision++
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose { context.unregisterReceiver(receiver) }
        }
    }
    val fine = remember(permissionsRevision) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val background = remember(permissionsRevision) { Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val location = remember(permissionsRevision) { LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)) }
    val access = LocationAccess(service = location, precise = fine, background = background)
    fun title(id: Long?): String = when (id) {
        null -> resources.getString(R.string.network_server_unchanged)
        else -> choices.firstOrNull { it.id == id }?.name ?: resources.getString(R.string.network_server_unavailable)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = contentBottomPadding)) {
        item {
            NetworkMainSwitch(checked = policy.enabled, onCheckedChange = viewModel::setEnabled)
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                    .clip(RoundedCornerShape(24.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                NetworkServerRow(stringResource(R.string.network_wifi), title(policy.wifiServerId)) { editing = "wifi" }
                NetworkServerRow(stringResource(R.string.network_mobile), title(policy.mobileServerId)) { editing = "mobile" }
            }
        }
        item {
            NetworkOverrideStatus(activeOverrideSsid)
        }
        if (status in listOf(NetworkServerStatus.EditedConfig, NetworkServerStatus.IncompatibleCore, NetworkServerStatus.UnavailableServer)) {
            item {
                val label = when (status) {
                    NetworkServerStatus.EditedConfig -> R.string.network_blocked_override
                    NetworkServerStatus.IncompatibleCore -> R.string.network_blocked_core
                    else -> R.string.network_server_unavailable
                }
                Text(stringResource(label), modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text(stringResource(R.string.network_ssid_rules), modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        items(policy.ssidServers.toList(), key = { it.first }) { (ssid, id) ->
            ListItem(
                headlineContent = { Text(ssid) },
                supportingContent = { Text(title(id)) },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 2.dp)
                    .clip(RoundedCornerShape(24.dp)).clickable { editing = "ssid:$ssid" },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                trailingContent = {
                    TooltipIconButton(stringResource(R.string.network_remove_ssid), onClick = { viewModel.removeSsid(ssid) }) {
                        Icon(painterResource(R.drawable.delete_24px), contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.network_add_ssid), color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(painterResource(R.drawable.ic_add_24), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).clickable { if (access.ready) adding = true else askingLocation = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
    if (askingLocation) {
        NetworkLocationDialog(
            access,
            onRequest = { requestPermission.launch(it) },
            onOpenPermissions = viewModel::openLocationPermissions,
            onDismiss = { askingLocation = false },
            onContinue = {
                askingLocation = false
                adding = true
            },
        )
    }
    if (adding) {
        SsidDialog(network.ssid, onDismiss = { adding = false }) { ssid ->
            adding = false
            editing = "ssid:$ssid"
        }
    }
    editing?.let { target ->
        val selected = when (target) {
            "wifi" -> policy.wifiServerId
            "mobile" -> policy.mobileServerId
            else -> policy.ssidServers[target.removePrefix("ssid:")]
        }
        NetworkServerPicker(choices, selected, ssidRule = target.startsWith("ssid:"), onDismiss = { editing = null }) { id ->
            when (target) {
                "wifi" -> viewModel.setWifi(id)
                "mobile" -> viewModel.setMobile(id)
                else -> if (id != null) viewModel.setSsid(target.removePrefix("ssid:"), id) else viewModel.removeSsid(target.removePrefix("ssid:"))
            }
            editing = null
        }
    }
}

@Composable
private fun NetworkOverrideStatus(ssid: String?) {
    val shownSsid = rememberOptionValue(ssid != null, ssid.orEmpty())
    AnimatedVisibility(
        visible = ssid != null,
        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
    ) {
        Text(
            stringResource(R.string.network_ssid_active, shownSsid),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NetworkServerRow(label: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick).heightIn(min = 76.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun NetworkMainSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.padding(16.dp).fillMaxWidth()
            .clip(RoundedCornerShape(42.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 72.dp).padding(start = 32.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.network_server_enable), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun SsidDialog(current: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val state = remember { TextFieldState(current.orEmpty()) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_add_ssid)) },
        text = {
            Column {
                OutlinedTextField(state = state, label = { Text(stringResource(R.string.network_ssid)) }, lineLimits = TextFieldLineLimits.SingleLine, modifier = Modifier.fillMaxWidth())
                if (current != null) TextButton(onClick = { state.edit { replace(0, length, current) } }) { Text(stringResource(R.string.network_use_current)) }
            }
        },
        confirmButton = { TextButton(enabled = state.text.isNotEmpty() && state.text.toString().toByteArray(Charsets.UTF_8).size <= 32, onClick = { onConfirm(state.text.toString()) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun NetworkServerPicker(choices: List<NetworkServerChoice>, selected: Long?, ssidRule: Boolean, onDismiss: () -> Unit, onSelected: (Long?) -> Unit) {
    val query = remember { TextFieldState() }
    val filtered = choices.filter { it.name.contains(query.text, ignoreCase = true) || it.subscription.contains(query.text, ignoreCase = true) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_pick_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state = query, label = { Text(stringResource(R.string.apps_find_servers)) }, leadingIcon = { Icon(painterResource(R.drawable.search_24px), contentDescription = null) }, lineLimits = TextFieldLineLimits.SingleLine, modifier = Modifier.fillMaxWidth())
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item { SelectableOptionRow(selected = selected == null, onSelected = { onSelected(null) }) { Text(stringResource(if (ssidRule) R.string.network_remove_ssid else R.string.network_server_unchanged)) } }
                        filtered.groupBy { it.subscriptionId }.forEach { (_, servers) ->
                            val name = servers.first().subscription
                            item { Text(name, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                            items(servers, key = { it.id }) { server ->
                                SelectableOptionRow(selected = selected == server.id, onSelected = { onSelected(server.id) }) { Text(server.name) }
                            }
                        }
                    }
                    ScrollFadeEdges(fadeColor = AlertDialogDefaults.containerColor)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private data class LocationAccess(val service: Boolean, val precise: Boolean, val background: Boolean) {
    val ready get() = service && precise && background
}

@Composable
private fun NetworkLocationDialog(
    access: LocationAccess,
    onRequest: (Array<String>) -> Unit,
    onOpenPermissions: () -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
        title = { Text(stringResource(R.string.network_location_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.network_ssid_permission_description))
                Column(modifier = Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LocationRequirementRow(R.string.network_location_service, R.string.network_location_service_summary, done = access.service) {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    LocationRequirementRow(R.string.network_location_precise, R.string.network_location_precise_summary, done = access.precise) {
                        onRequest(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                    if (Build.VERSION.SDK_INT >= 29) {
                        LocationRequirementRow(
                            R.string.network_location_background,
                            if (access.precise) R.string.network_location_background_summary else R.string.network_location_background_blocked,
                            done = access.background,
                            enabled = access.precise,
                        ) {
                            // Android 11+ only grants background location from the permission screen.
                            if (Build.VERSION.SDK_INT == 29) onRequest(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) else onOpenPermissions()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(if (access.ready) R.string.network_location_ready else R.string.network_location_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun LocationRequirementRow(@StringRes title: Int, @StringRes summary: Int, done: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val actionable = !done && enabled
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        leadingContent = { LocationRequirementStatus(done) },
        trailingContent = if (actionable) {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
        modifier = Modifier.clickable(enabled = actionable, onClick = onClick).alpha(if (done || enabled) 1f else 0.6f),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun LocationRequirementStatus(done: Boolean) {
    val description = stringResource(if (done) R.string.network_location_done else R.string.network_location_missing)
    if (done) {
        Icon(Icons.Filled.CheckCircle, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
    } else {
        // An empty ring marks a step that is still open, like an unchecked list item.
        Box(
            modifier = Modifier.size(24.dp).padding(2.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .semantics { contentDescription = description },
        )
    }
}
