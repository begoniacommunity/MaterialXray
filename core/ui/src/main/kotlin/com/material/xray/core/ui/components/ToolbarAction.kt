package com.material.xray.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

data class ToolbarAction(
    val id: String,
    @param:DrawableRes val icon: Int,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
    val menu: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
)

/** Keep empty slots as null; each slot transitions only when its visible symbol changes. */
@Composable
fun AnimatedToolbarActions(actions: List<ToolbarAction?>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { index, action ->
            val current by rememberUpdatedState(action)
            var menuExpanded by remember { mutableStateOf(false) }
            LaunchedEffect(action?.id) { menuExpanded = false }
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = action,
                    contentKey = { it?.icon },
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) using null },
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                    label = "toolbarAction$index",
                ) { shown ->
                    if (shown != null) {
                        TooltipIconButton(
                            tooltip = shown.label,
                            enabled = shown.enabled,
                            onClick = {
                                current?.takeIf { it.icon == shown.icon }?.let { latest ->
                                    if (latest.menu != null) menuExpanded = true else latest.onClick()
                                }
                            },
                        ) {
                            Icon(painterResource(shown.icon), contentDescription = shown.label, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                action?.menu?.let { menu ->
                    AnimatedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        menu { menuExpanded = false }
                    }
                }
            }
        }
    }
}
