package com.material.xray.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun UpdateChecksSetting(
    title: String,
    checked: Boolean,
    description: String,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            // Draw the separator at the label's measured edge. IntrinsicSize.Min would ask
            // AnimatedVisibility for its final height and make the whole row jump on expansion.
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).drawBehind {
                val inset = 12.dp.toPx()
                val thickness = 1.dp.toPx()
                val edge = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width - thickness
                drawRect(dividerColor, Offset(edge, inset), Size(thickness, (size.height - 2 * inset).coerceAtLeast(0f)))
            }.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            AnimatedOptionContent(visible = checked, blockOutgoingInput = false) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
