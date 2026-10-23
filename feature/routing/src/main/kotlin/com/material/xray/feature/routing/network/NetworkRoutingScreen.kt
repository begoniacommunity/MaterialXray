package com.material.xray.feature.routing.network

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var permissionsRevision by remember { mutableIntStateOf(0) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsRevision++
        viewModel.refreshNetwork()
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
    val fine = remember(permissionsRevision) { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val background = remember(permissionsRevision) { Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val location = remember(permissionsRevision) { LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)) }
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
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).clickable { adding = true },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(painterResource(R.drawable.info_24px), contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.network_ssid_permission_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
                NetworkLocationAccess(fine, background, location, onRequest = { requestPermission.launch(it) }, onOpenPermissions = viewModel::openLocationPermissions)
            }
        }
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

@Composable
private fun NetworkLocationAccess(fine: Boolean, background: Boolean, location: Boolean, onRequest: (Array<String>) -> Unit, onOpenPermissions: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.Start) {
        if (!fine) {
            TextButton(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), onClick = { onRequest(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }) {
                Text(stringResource(R.string.network_grant_location), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
        if (fine && !background) {
            TextButton(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), onClick = {
                if (Build.VERSION.SDK_INT == 29) {
                    onRequest(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                } else {
                    onOpenPermissions()
                }
            }) { Text(stringResource(R.string.network_background_location), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
        }
        if (!location) {
            TextButton(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text(stringResource(R.string.network_enable_location), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
        TextButton(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), onClick = { onOpenPermissions() }) {
            Text(stringResource(R.string.network_open_permissions), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
        }
    }
}
