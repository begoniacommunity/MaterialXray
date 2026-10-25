package com.material.xray.feature.configviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.material.xray.core.model.parseCertificateFingerprints
import com.material.xray.core.network.FetchedTlsCertificate
import com.material.xray.core.network.TlsCertificateFetcher
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.FadingOutlinedTextField
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

@Composable
internal fun TlsCertificateField(drafts: Map<EditKey, TextFieldState>) {
    val endpoint = certificateEndpoint(drafts.mapValues { it.value.text.toString() }) ?: return
    val pin = drafts.getValue(EditKey.SecurityCertificatePin)
    val fetcher = koinInject<TlsCertificateFetcher>()
    val pinText = pin.text.toString()
    var requestId by remember(endpoint, pinText) { mutableIntStateOf(0) }
    var fetching by remember(endpoint, pinText, requestId) { mutableStateOf(false) }
    var failure by remember(endpoint, pinText, requestId) { mutableStateOf(false) }
    var certificate by remember(endpoint, pinText) { mutableStateOf<FetchedTlsCertificate?>(null) }
    // An edit or navigation cancels the probe and discards its result. It can never fill another endpoint.
    LaunchedEffect(endpoint, pinText, requestId) {
        certificate = null
        failure = false
        fetching = false
        if (requestId > 0) {
            fetching = true
            try {
                certificate = fetcher.fetch(endpoint)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure = true
            } finally {
                fetching = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FadingOutlinedTextField(
            state = pin,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.config_viewer_certificate_pin)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Text(stringResource(R.string.config_viewer_certificate_pin_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { requestId++ }, enabled = !fetching) {
            Text(stringResource(R.string.config_viewer_certificate_fetch))
        }
        if (fetching) {
            CircularProgressIndicator()
            TextButton(onClick = { requestId = 0 }) { Text(stringResource(android.R.string.cancel)) }
        }
        if (failure) Text(stringResource(R.string.config_viewer_certificate_fetch_failed), color = MaterialTheme.colorScheme.error)
    }
    certificate?.takeIf { it.endpoint == endpoint }?.let { result ->
        CertificateConfirmation(
            certificate = result,
            replacing = pinText.isNotBlank() && runCatching { result.sha256 !in parseCertificateFingerprints(pinText) }.getOrDefault(true),
            onDismiss = { certificate = null },
            onConfirm = {
                // Replacing an existing pin is always explicit, never part of a connection retry.
                if (certificateEndpoint(drafts.mapValues { it.value.text.toString() }) == result.endpoint && pin.text.toString() == pinText) {
                    val updated = confirmedCertificatePins(pinText, result.sha256)
                    pin.edit { replace(0, length, updated) }
                }
                certificate = null
            },
        )
    }
}

@Composable
private fun CertificateConfirmation(certificate: FetchedTlsCertificate, replacing: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_viewer_certificate_title)) },
        text = {
            SelectionContainer {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.config_viewer_certificate_endpoint, certificate.endpoint.address, certificate.endpoint.port, certificate.endpoint.serverName))
                    Text(stringResource(R.string.config_viewer_certificate_sha256, certificate.sha256), fontFamily = FontFamily.Monospace)
                    Text(stringResource(R.string.config_viewer_certificate_subject, certificate.subject))
                    Text(stringResource(R.string.config_viewer_certificate_issuer, certificate.issuer))
                    Text(stringResource(R.string.config_viewer_certificate_validity, dateFormat.format(Date(certificate.validFrom)), dateFormat.format(Date(certificate.validUntil))))
                    Text(
                        stringResource(if (certificate.trusted) R.string.config_viewer_certificate_trusted else R.string.config_viewer_certificate_untrusted),
                        color = if (certificate.trusted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                    if (replacing) Text(stringResource(R.string.config_viewer_certificate_replacing), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.config_viewer_certificate_confirmation))
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.config_viewer_certificate_use)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
