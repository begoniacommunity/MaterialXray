package com.material.xray.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.material.xray.core.model.GeoDataUpdateInterval
import com.material.xray.core.runtime.GeoDataAsset
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.DropdownOption
import com.material.xray.core.ui.components.SettingsFieldSpacing
import com.material.xray.core.ui.components.TooltipIconButton
import com.material.xray.core.ui.components.UpdateChecksSetting
import com.material.xray.core.ui.components.UpdateIntervalDialog
import com.material.xray.core.ui.components.rememberUpdateAlarmPermissionRequest
import com.material.xray.core.ui.components.updateIntervalLabel

@Composable
internal fun GeoDataAssetDialog(
    asset: GeoDataAsset,
    bytes: Long,
    checksEnabled: Boolean,
    intervalSeconds: Int,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onChecksEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (Int) -> Unit,
) {
    var showInterval by rememberSaveable(asset) { mutableStateOf(false) }
    val requestAlarms = rememberUpdateAlarmPermissionRequest()
    if (showInterval) {
        UpdateIntervalDialog(
            current = intervalSeconds,
            default = GeoDataUpdateInterval.DEFAULT_HOURS * 3600,
            options = listOf(1, 6, 24, 72, 168).map { hours -> DropdownOption(hours * 3600, updateIntervalLabel(hours * 3600)) },
            onDismiss = { showInterval = false },
            onConfirm = {
                showInterval = false
                onIntervalChange(it)
            },
            onDismissAll = onDismiss,
        )
        return
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(asset.displayName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsFieldSpacing),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null, modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(asset.fileName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (bytes > 0) Formatter.formatShortFileSize(LocalContext.current, bytes) else stringResource(R.string.settings_geo_asset_not_downloaded),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TooltipIconButton(
                            tooltip = stringResource(R.string.settings_delete),
                            onClick = onDelete,
                            enabled = canDelete && bytes > 0,
                        ) {
                            Icon(painterResource(R.drawable.delete_24px), contentDescription = stringResource(R.string.settings_delete))
                        }
                    }
                }
                UpdateChecksSetting(
                    title = stringResource(R.string.settings_geo_asset_update_checks_title),
                    checked = checksEnabled,
                    description = updateIntervalLabel(intervalSeconds),
                    onClick = { showInterval = true },
                    onCheckedChange = {
                        onChecksEnabledChange(it)
                        if (it) requestAlarms()
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) } },
    )
}
