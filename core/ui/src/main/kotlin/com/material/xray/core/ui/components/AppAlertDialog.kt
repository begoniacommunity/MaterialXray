package com.material.xray.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties

/** Aligns the whole title and its wrapped lines automatically from the presence of [icon]. */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val centered = icon != null
    val alignedTitle: (@Composable () -> Unit)? = title?.let { titleContent ->
        {
            ProvideTextStyle(TextStyle(textAlign = if (centered) TextAlign.Center else TextAlign.Start)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
                ) {
                    titleContent()
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = alignedTitle,
        text = text,
        properties = properties,
    )
}
