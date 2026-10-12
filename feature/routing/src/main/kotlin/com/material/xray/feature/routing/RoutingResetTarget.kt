package com.material.xray.feature.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.material.xray.core.model.RoutingRuleScope
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.DropdownOption
import com.material.xray.core.ui.components.ReadOnlyDropdownField

internal sealed interface RoutingResetTarget {
    data class Server(val id: Long) : RoutingResetTarget
    data class Subscription(val sourceId: Long?) : RoutingResetTarget
}

internal fun routingResetTarget(scope: RoutingRuleScope, serverId: Long?, sourceId: Long?): RoutingResetTarget? = when (scope) {
    RoutingRuleScope.Profile -> serverId?.let { RoutingResetTarget.Server(it) }
    RoutingRuleScope.Subscription -> RoutingResetTarget.Subscription(sourceId)
    RoutingRuleScope.Custom -> null
}

internal fun canResetSubscriptionRules(sources: List<RoutingSubscriptionSource>, sourceId: Long?): Boolean = if (sourceId == null) sources.isNotEmpty() else sources.any { it.id == sourceId }

@Composable
internal fun RoutingRulesResetDialog(target: RoutingResetTarget?, sources: List<RoutingSubscriptionSource>, onDismiss: () -> Unit, onServerReset: (Long) -> Unit, onSubscriptionReset: (Long, Long?) -> Unit) {
    if (target == null) return
    val server = target as? RoutingResetTarget.Server
    val subscription = target as? RoutingResetTarget.Subscription
    var chosenSource by remember(target) { mutableStateOf(subscription?.sourceId ?: sources.singleOrNull()?.id) }
    val selectedSource = sources.firstOrNull { it.id == chosenSource }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.settings_backup_restore_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
        title = { Text(stringResource(if (server != null) R.string.routing_reset_server_rules_title else R.string.routing_reset_subscription_rules_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    when {
                        server != null -> stringResource(R.string.routing_reset_server_rules_description)
                        selectedSource != null -> stringResource(R.string.routing_reset_subscription_rules_description, selectedSource.name)
                        else -> stringResource(R.string.routing_reset_subscription_choose)
                    },
                )
                if (subscription != null && subscription.sourceId == null) {
                    ReadOnlyDropdownField(
                        label = stringResource(R.string.routing_reset_subscription_source),
                        selectedText = selectedSource?.name.orEmpty(),
                        options = sources.map { DropdownOption(it.id, it.name) },
                        onSelected = { chosenSource = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = server != null || selectedSource != null, onClick = {
                when (target) {
                    is RoutingResetTarget.Server -> onServerReset(target.id)
                    is RoutingResetTarget.Subscription -> selectedSource?.id?.let { onSubscriptionReset(it, target.sourceId) }
                }
                onDismiss()
            }) { Text(stringResource(R.string.routing_reset_rules_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.routing_cancel)) }
        },
    )
}
