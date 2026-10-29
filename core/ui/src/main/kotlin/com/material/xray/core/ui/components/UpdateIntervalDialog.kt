package com.material.xray.core.ui.components

import android.app.AlarmManager
import android.os.Build
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.material.xray.core.model.UpdateInterval
import com.material.xray.core.ui.R

@Composable
fun UpdateIntervalDialog(
    current: Int,
    default: Int,
    options: List<DropdownOption<Int>>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(current) }
    var customSelected by rememberSaveable { mutableStateOf(options.none { it.value == current }) }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val requestAlarms = rememberUpdateAlarmPermissionRequest()
    if (showCustom) {
        CustomUpdateIntervalDialog(
            current = selected.takeIf { it > 0 } ?: default,
            onDismiss = { showCustom = false },
            onConfirm = {
                selected = it
                customSelected = true
                showCustom = false
            },
        )
        return
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_app_update_interval_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val label = updateIntervalLabel(option.value)
                    IntervalOptionRow(
                        selected = !customSelected && selected == option.value,
                        label = if (option.value == default) stringResource(R.string.settings_update_interval_default, label) else label,
                        onClick = {
                            selected = option.value
                            customSelected = false
                        },
                    )
                }
                IntervalOptionRow(
                    selected = customSelected,
                    label = stringResource(R.string.update_custom_date),
                    onClick = { showCustom = true },
                )
                if (customSelected) {
                    Text(updateIntervalLabel(selected), modifier = Modifier.padding(start = 44.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selected)
                if (selected > 0) requestAlarms()
            }) {
                Text(stringResource(R.string.settings_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
fun UpdateIntervalDropdown(
    label: String,
    current: Int,
    options: List<DropdownOption<Int>>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    ReadOnlyDropdownField(
        label = label,
        selectedText = updateIntervalLabel(current),
        options = options.map { it.copy(label = updateIntervalLabel(it.value)) } + DropdownOption(-1, stringResource(R.string.update_custom_date)),
        onSelected = { if (it == -1) showCustom = true else onSelected(it) },
        modifier = modifier,
    )
    if (showCustom) {
        CustomUpdateIntervalDialog(
            current = current.takeIf { it > 0 } ?: 3600,
            onDismiss = { showCustom = false },
            onConfirm = {
                showCustom = false
                onSelected(it)
            },
        )
    }
}

@Composable
private fun CustomUpdateIntervalDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val pickers = remember { mutableMapOf<String, NumberPicker>() }
    var hours by rememberSaveable { mutableIntStateOf(current / 3600) }
    var minutes by rememberSaveable { mutableIntStateOf(current % 3600 / 60) }
    var seconds by rememberSaveable { mutableIntStateOf(current % 60) }
    val interval = hours * 3600 + minutes * 60 + seconds
    val alarmPermission = rememberSystemState { context ->
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val requestAlarms = rememberUpdateAlarmPermissionRequest()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_custom_date)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!alarmPermission.value) {
                    Surface(
                        onClick = requestAlarms,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null)
                            Text(
                                text = stringResource(R.string.update_grant_alarm_permission),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DurationPicker(stringResource(R.string.update_hours), hours, 0, UpdateInterval.MAX_SECONDS / 3600, { hours = it }, Modifier.weight(1f), { pickers["hours"] = it })
                    DurationPicker(stringResource(R.string.update_minutes), minutes, 0, 59, { minutes = it }, Modifier.weight(1f), { pickers["minutes"] = it })
                    DurationPicker(stringResource(R.string.update_seconds), seconds, 0, 59, { seconds = it }, Modifier.weight(1f), { pickers["seconds"] = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                pickers.values.forEach { it.clearFocus() }
                val selected = (pickers["hours"]?.value ?: hours) * 3600 + (pickers["minutes"]?.value ?: minutes) * 60 + (pickers["seconds"]?.value ?: seconds)
                if (UpdateInterval.isValid(selected)) onConfirm(selected)
            }, enabled = UpdateInterval.isValid(interval)) { Text(stringResource(R.string.settings_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun DurationPicker(label: String, value: Int, minimum: Int, maximum: Int, onChanged: (Int) -> Unit, modifier: Modifier, onBound: (NumberPicker) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(144.dp),
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = minimum
                    maxValue = maximum
                    wrapSelectorWheel = false
                }
            },
            update = { picker ->
                onBound(picker)
                picker.contentDescription = label
                picker.value = value
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) picker.textColor = color
                picker.setOnValueChangedListener { _, _, newValue -> onChanged(newValue) }
            },
        )
    }
}

@Composable
private fun IntervalOptionRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        Text(label)
    }
}
