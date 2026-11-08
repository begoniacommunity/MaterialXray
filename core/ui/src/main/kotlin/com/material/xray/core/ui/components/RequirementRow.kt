package com.material.xray.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.material.xray.core.ui.R

/** A grouped checklist of [RequirementRow]s, for a dialog that asks for several permissions at once. */
@Composable
fun RequirementList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/**
 * One permission or setting the user still has to grant. An open row leads somewhere with a
 * chevron; a [done] row shows a check and does nothing, and a row that is not [enabled] waits
 * for an earlier step.
 */
@Composable
fun RequirementRow(title: String, summary: String, done: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val actionable = !done && enabled
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { RequirementStatus(done) },
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
private fun RequirementStatus(done: Boolean) {
    val description = stringResource(if (done) R.string.requirement_done else R.string.requirement_missing)
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
