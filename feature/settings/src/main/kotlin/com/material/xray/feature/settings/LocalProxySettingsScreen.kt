package com.material.xray.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.material.xray.core.data.repository.SettingsSnapshot
import com.material.xray.core.model.LocalProxySettings
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.AnimatedOptionContent
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.FadingOutlinedTextField as OutlinedTextField
import com.material.xray.core.ui.components.ScrolledTopAppBar
import com.material.xray.core.ui.components.SettingsItemSpacing
import com.material.xray.core.ui.components.SettingsSwitchRow
import com.material.xray.core.ui.components.TooltipIconButton

/**
 * The local proxy subpage: whether the core opens its SOCKS5/HTTP port, who may reach it, and
 * every value a client needs, each one copyable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalProxySettingsScreen(
    settings: SettingsSnapshot,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val proxy = settings.localProxy
    val resumed by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    // Read again on every return to the page, since the network may have changed while away.
    val lanAddress by produceState<String?>(null, proxy.allowLan, resumed.isAtLeast(Lifecycle.State.RESUMED)) {
        value = if (proxy.allowLan) viewModel.lanAddress() else null
    }
    val host = lanAddress.takeIf { proxy.allowLan } ?: LocalProxySettings.LOOPBACK_ADDRESS
    var editing by rememberSaveable { mutableStateOf<LocalProxyField?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            ScrolledTopAppBar(
                title = stringResource(R.string.settings_local_proxy_title),
                scrollBehavior = scrollBehavior,
                showLogo = false,
                navigationIcon = {
                    TooltipIconButton(tooltip = stringResource(R.string.settings_dns_back), onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_dns_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SettingsItemSpacing),
        ) {
            item(key = "detection_warning") {
                AnimatedOptionContent(visible = settings.localProxyEnabled) {
                    SettingsNotice(text = stringResource(R.string.settings_local_proxy_detection_warning))
                }
            }
            item(key = "allow_lan") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_local_proxy_allow_lan_title),
                    description = stringResource(R.string.settings_local_proxy_allow_lan_description),
                    checked = proxy.allowLan,
                    onCheckedChange = viewModel::setLocalProxyAllowLan,
                )
            }
            item(key = "details") {
                LocalProxyDetails(
                    proxy = proxy,
                    host = host,
                    lanAddressMissing = proxy.allowLan && lanAddress == null,
                    onEdit = { editing = it },
                )
            }
        }
    }

    editing?.let { field ->
        LocalProxyFieldDialog(
            field = field,
            current = field.valueOf(proxy),
            onDismiss = { editing = null },
            onSave = { value ->
                editing = null
                when (field) {
                    LocalProxyField.Port -> value.toIntOrNull()?.let(viewModel::setLocalProxyPort)
                    LocalProxyField.Username -> viewModel.setLocalProxyUsername(value)
                    LocalProxyField.Password -> viewModel.setLocalProxyPassword(value)
                }
            },
        )
    }
}

/** The connection details a user may change. */
private enum class LocalProxyField(@param:StringRes val label: Int) {
    Port(R.string.settings_local_proxy_port_label),
    Username(R.string.settings_local_proxy_username_label),
    Password(R.string.settings_local_proxy_password_label),
    ;

    fun valueOf(proxy: LocalProxySettings): String = when (this) {
        Port -> proxy.port.toString()
        Username -> proxy.username
        Password -> proxy.password
    }

    fun isValid(value: String): Boolean = when (this) {
        Port -> value.toIntOrNull()?.let(LocalProxySettings::isValidPort) == true
        Username, Password -> LocalProxySettings.isValidCredential(value)
    }

    // Characters that cannot be valid never reach the field, so only length and range can be wrong.
    val inputTransformation: InputTransformation
        get() = when (this) {
            Port -> InputTransformation.byValue { _, proposed -> proposed.filter(Char::isDigit).take(PORT_DIGITS) }
            Username, Password -> InputTransformation.byValue { _, proposed ->
                proposed.filter { LocalProxySettings.isValidCredential(it.toString()) }.take(LocalProxySettings.MAX_CREDENTIAL_LENGTH)
            }
        }
}

private const val PORT_DIGITS = 5

@Composable
private fun LocalProxyDetails(
    proxy: LocalProxySettings,
    host: String,
    lanAddressMissing: Boolean,
    onEdit: (LocalProxyField) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp - SettingsItemSpacing)) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_local_proxy_details_title),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        AnimatedOptionContent(visible = lanAddressMissing) {
            Text(
                text = stringResource(R.string.settings_local_proxy_no_lan_address),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        CopyableValueRow(stringResource(R.string.settings_local_proxy_host_label), host)
        LocalProxyField.entries.forEach { field ->
            CopyableValueRow(stringResource(field.label), field.valueOf(proxy), onEdit = { onEdit(field) })
        }
        CopyableValueRow(
            stringResource(R.string.settings_local_proxy_link_label),
            proxy.connectionUri(LocalProxySettings.SOCKS_SCHEME, host),
        )
    }
}

@Composable
private fun CopyableValueRow(label: String, value: String, onEdit: (() -> Unit)? = null) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.settings_local_proxy_copied, label)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
            }
        }
        if (onEdit != null) {
            TooltipIconButton(tooltip = stringResource(R.string.settings_local_proxy_edit, label), onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.settings_local_proxy_edit, label))
            }
        }
        TooltipIconButton(
            tooltip = stringResource(R.string.settings_local_proxy_copy, label),
            onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.settings_local_proxy_copy, label))
        }
    }
}

@Composable
private fun LocalProxyFieldDialog(
    field: LocalProxyField,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val state = rememberSaveable(field, saver = TextFieldState.Saver) { TextFieldState(current) }
    val valid by remember(state, field) { derivedStateOf { field.isValid(state.text.toString()) } }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(field.label)) },
        text = {
            OutlinedTextField(
                state = state,
                label = { Text(stringResource(field.label)) },
                supportingText = {
                    Text(
                        if (field == LocalProxyField.Port) {
                            stringResource(
                                R.string.settings_local_proxy_port_supporting_text,
                                LocalProxySettings.MIN_PORT,
                                LocalProxySettings.MAX_PORT,
                                LocalProxySettings.DEFAULT_PORT,
                            )
                        } else {
                            stringResource(R.string.settings_local_proxy_credential_supporting_text, LocalProxySettings.MAX_CREDENTIAL_LENGTH)
                        },
                    )
                },
                isError = state.text.isNotEmpty() && !valid,
                inputTransformation = field.inputTransformation,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field) {
                        LocalProxyField.Port -> KeyboardType.Number
                        LocalProxyField.Username -> KeyboardType.Ascii
                        LocalProxyField.Password -> KeyboardType.Password
                    },
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(state.text.toString()) }, enabled = valid) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
