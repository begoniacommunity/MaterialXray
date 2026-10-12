package com.material.xray.feature.routing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.material.xray.core.data.parser.ProfileRoutingRule
import com.material.xray.core.model.RoutingRule
import com.material.xray.core.model.RoutingRuleScope
import com.material.xray.core.model.reorderedBy
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.ScrollFadeEdges
import com.material.xray.core.xray.ProviderGeoDataNotice

internal fun routingSelectionKey(scope: RoutingRuleScope, id: String): String = "${scope.value}:$id"
internal fun ProfileRoutingRule.selectionKey(): String = routingSelectionKey(RoutingRuleScope.Profile, "$originalIndex:$originalRuleJson")
private fun RoutingRuleScope.headerKey(): String = "scope:$value"

private data class RuleRow(val scope: RoutingRuleScope, val rule: RoutingRule? = null, val profile: ProfileRoutingRule? = null) {
    val key: String get() = profile?.selectionKey() ?: routingSelectionKey(scope, requireNotNull(rule).id)
    val enabled: Boolean get() = profile?.enabled ?: requireNotNull(rule).enabled
    val orphaned: Boolean get() = profile?.orphaned == true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoutingRulesTab(
    searchQuery: TextFieldState,
    scopeOrder: List<RoutingRuleScope>,
    customRules: List<RoutingRule>,
    subscriptionRules: List<RoutingRule>,
    profileRules: List<ProfileRoutingRule>,
    profileServerId: Long?,
    providerManaged: Boolean,
    providerName: String?,
    providerGeoDataNotice: ProviderGeoDataNotice?,
    onProviderGeoDataNoticeClick: () -> Unit,
    selectionMode: Boolean,
    selectedRuleIds: Set<String>,
    actions: RoutingRuleActions,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val edgeSize = with(LocalDensity.current) { 64.dp.toPx() }
    val drag = remember(listState, coroutineScope, edgeSize, profileServerId) { RoutingReorderState(listState, coroutineScope, edgeSize) }
    val haptics = LocalHapticFeedback.current
    var scopes by remember(scopeOrder) { mutableStateOf(scopeOrder) }
    var customOrder by remember { mutableStateOf(customRules.map { it.id }) }
    var subscriptionOrder by remember { mutableStateOf(subscriptionRules.map { it.id }) }
    var profileOrder by remember { mutableStateOf(profileRules.map { it.selectionKey() }) }
    LaunchedEffect(customRules) { if (drag.draggingKey == null) customOrder = customRules.map { it.id } }
    LaunchedEffect(subscriptionRules) { if (drag.draggingKey == null) subscriptionOrder = subscriptionRules.map { it.id } }
    LaunchedEffect(profileRules) { if (drag.draggingKey == null) profileOrder = profileRules.map { it.selectionKey() } }
    DisposableEffect(drag) { onDispose { drag.finish(cancelled = true) } }

    val custom = customRules.reorderedBy(customOrder, RoutingRule::id)
    val subscription = subscriptionRules.reorderedBy(subscriptionOrder, RoutingRule::id)
    val profile = profileRules.reorderedBy(profileOrder) { it.selectionKey() }
    val query = searchQuery.text.toString()
    val rows = mapOf(
        RoutingRuleScope.Custom to custom.filter { it.matchesSearch(query, routingRuleDisplayName(it)) }.map { RuleRow(RoutingRuleScope.Custom, rule = it) },
        RoutingRuleScope.Subscription to subscription.filter { it.matchesSearch(query, routingRuleDisplayName(it)) }.map { RuleRow(RoutingRuleScope.Subscription, rule = it) },
        RoutingRuleScope.Profile to profile.filter { it.matchesSearch(query) }.map { RuleRow(RoutingRuleScope.Profile, profile = it) },
    )
    val visibleScopes = scopes.filter { rows.getValue(it).isNotEmpty() }
    drag.keys = { key ->
        if (key.startsWith("scope:")) visibleScopes.map { it.headerKey() } else rows.values.firstOrNull { group -> group.any { it.key == key } }.orEmpty().filterNot { it.orphaned }.map { it.key }
    }
    drag.onMove = { from, to ->
        if (from.startsWith("scope:")) {
            scopes = scopes.moved(scopes.first { it.headerKey() == from }, scopes.first { it.headerKey() == to })
        } else {
            when {
                from.startsWith("custom:") -> customOrder = customRules.reorderedBy(customOrder, RoutingRule::id).map { it.id }.moved(from.removePrefix("custom:"), to.removePrefix("custom:"))
                from.startsWith("subscription:") -> subscriptionOrder = subscriptionRules.reorderedBy(subscriptionOrder, RoutingRule::id).map { it.id }.moved(from.removePrefix("subscription:"), to.removePrefix("subscription:"))
                else -> profileOrder = profileRules.reorderedBy(profileOrder) { it.selectionKey() }.map { it.selectionKey() }.moved(from, to)
            }
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    drag.onFinish = { key, cancelled ->
        if (cancelled) {
            scopes = scopeOrder
            customOrder = customRules.map { it.id }
            subscriptionOrder = subscriptionRules.map { it.id }
            profileOrder = profileRules.map { it.selectionKey() }
        } else {
            persistRoutingOrder(
                key,
                RuleOrderSnapshot(scopeOrder, customRules, subscriptionRules, profileRules),
                RuleOrderSnapshot(scopes, customRules.reorderedBy(customOrder, RoutingRule::id), subscriptionRules.reorderedBy(subscriptionOrder, RoutingRule::id), profileRules.reorderedBy(profileOrder) { it.selectionKey() }),
                actions,
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        RoutingSearchField(state = searchQuery, hint = stringResource(R.string.routing_search_rules))
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (providerManaged) item(key = "providerRoutingBanner") { ProviderManagedBanner(customRules.isNotEmpty(), providerName) }
                if (providerGeoDataNotice != null) item(key = "providerGeoDataBanner") { ProviderGeoDataBanner(providerGeoDataNotice, onProviderGeoDataNoticeClick) }
                visibleScopes.forEach { scope ->
                    val headerKey = scope.headerKey()
                    item(key = headerKey, contentType = "routingScopeHeader") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().animateItem(placementSpec = if (drag.draggingKey == headerKey) null else androidx.compose.animation.core.spring())
                                .zIndex(if (drag.draggingKey == headerKey) 1f else 0f)
                                .onPlaced { drag.placed(headerKey, it.positionInParent().y, it.size.height) }
                                .graphicsLayer { translationY = drag.offset(headerKey) }
                                .combinedClickable(onClick = {}, onLongClick = { actions.onScopeLongClick(scope) })
                                .padding(start = 4.dp, top = 12.dp, end = 16.dp, bottom = 6.dp),
                        ) {
                            Text(stringResource(scope.titleResource()), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            if (selectionMode) RoutingDragHandle(headerKey, stringResource(R.string.routing_reorder_category), stringResource(R.string.routing_move_up), stringResource(R.string.routing_move_down), drag)
                        }
                    }
                    val group = rows.getValue(scope)
                    itemsIndexed(group, key = { _, row -> row.key }, contentType = { _, _ -> "routingRule" }) { index, row ->
                        RoutingRuleCard(
                            row = row,
                            shape = routingRuleShape(index, group.lastIndex),
                            selected = row.key in selectedRuleIds,
                            selectionMode = selectionMode,
                            drag = drag,
                            modifier = Modifier.animateItem(placementSpec = if (drag.draggingKey == row.key) null else androidx.compose.animation.core.spring())
                                .zIndex(if (drag.draggingKey == row.key) 1f else 0f)
                                .onPlaced { drag.placed(row.key, it.positionInParent().y, it.size.height) }
                                .graphicsLayer { translationY = drag.offset(row.key) },
                            actions = actions,
                        )
                    }
                }
                if (query.isNotBlank() && visibleScopes.isEmpty()) {
                    item(key = "noMatchingRules") {
                        Text(stringResource(R.string.routing_no_matching_rules), modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ScrollFadeEdges()
        }
    }
}

@Composable
private fun ProviderManagedBanner(hasCustomRules: Boolean, providerName: String?) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = when {
                    hasCustomRules && providerName != null -> stringResource(R.string.routing_some_rules_managed_by_provider, providerName)
                    hasCustomRules -> stringResource(R.string.routing_some_rules_managed_by_selected_subscription)
                    providerName != null -> stringResource(R.string.routing_rules_managed_by_provider, providerName)
                    else -> stringResource(R.string.routing_rules_managed_by_selected_subscription)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutingRuleCard(row: RuleRow, shape: RoundedCornerShape, selected: Boolean, selectionMode: Boolean, drag: RoutingReorderState, modifier: Modifier, actions: RoutingRuleActions) {
    val containerColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer, label = "routingRuleContainerColor")
    val profile = row.profile
    val rule = row.rule
    fun click(long: Boolean) {
        when {
            profile != null -> if (long) actions.onProfileRuleLongClick(profile) else actions.onProfileRuleClick(profile)
            row.scope == RoutingRuleScope.Custom -> if (long) actions.onRuleLongClick(requireNotNull(rule)) else actions.onRuleClick(requireNotNull(rule))
            else -> if (long) actions.onSubscriptionRuleLongClick(requireNotNull(rule)) else actions.onSubscriptionRuleClick(requireNotNull(rule))
        }
    }
    Surface(color = containerColor, shape = shape, modifier = modifier.fillMaxWidth().alpha(if (row.orphaned) 0.55f else 1f).clip(shape).combinedClickable(onClick = { click(false) }, onLongClick = { click(true) })) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile?.name ?: routingRuleDisplayName(requireNotNull(rule)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(profile?.let { profileRoutingRuleContentText(it) } ?: routingRuleContentText(requireNotNull(rule)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                profile?.target?.let { Text(it.displayText(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (row.orphaned) Text(stringResource(R.string.routing_profile_rule_orphaned), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            AnimatedContent(targetState = selectionMode, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) using null }, label = "routingRuleControl") { selecting ->
                Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                    if (selecting) {
                        RoutingDragHandle(row.key, stringResource(R.string.routing_reorder_rule), stringResource(R.string.routing_move_up), stringResource(R.string.routing_move_down), drag, enabled = selectionMode && !row.orphaned)
                    } else {
                        Switch(checked = row.enabled && !row.orphaned, enabled = !selectionMode && !row.orphaned, onCheckedChange = { enabled ->
                            when {
                                profile != null -> actions.onProfileRuleToggled(profile, enabled)
                                row.scope == RoutingRuleScope.Custom -> actions.onRuleToggled(requireNotNull(rule), enabled)
                                else -> actions.onSubscriptionRuleToggled(requireNotNull(rule), enabled)
                            }
                        })
                    }
                }
            }
        }
    }
}

private fun RoutingRuleScope.titleResource(): Int = when (this) {
    RoutingRuleScope.Custom -> R.string.routing_scope_custom
    RoutingRuleScope.Subscription -> R.string.routing_scope_subscription_wide
    RoutingRuleScope.Profile -> R.string.routing_scope_profile_specific
}

private fun routingRuleShape(index: Int, lastIndex: Int) = RoundedCornerShape(
    topStart = if (index == 0) 20.dp else 4.dp,
    topEnd = if (index == 0) 20.dp else 4.dp,
    bottomStart = if (index == lastIndex) 20.dp else 4.dp,
    bottomEnd = if (index == lastIndex) 20.dp else 4.dp,
)

private data class RuleOrderSnapshot(
    val scopes: List<RoutingRuleScope>,
    val custom: List<RoutingRule>,
    val subscription: List<RoutingRule>,
    val profile: List<ProfileRoutingRule>,
)

private fun persistRoutingOrder(key: String, original: RuleOrderSnapshot, ordered: RuleOrderSnapshot, actions: RoutingRuleActions) {
    when {
        key.startsWith("scope:") -> if (ordered.scopes != original.scopes) actions.onScopesReordered(ordered.scopes)
        key.startsWith("custom:") -> if (ordered.custom != original.custom) actions.onRulesReordered(RoutingRuleScope.Custom, ordered.custom.map { it.id })
        key.startsWith("subscription:") -> if (ordered.subscription != original.subscription) actions.onRulesReordered(RoutingRuleScope.Subscription, ordered.subscription.map { it.id })
        else -> {
            val profile = ordered.profile.filterNot { it.orphaned }
            if (profile != original.profile.filterNot { it.orphaned }) actions.onProfileRulesReordered(profile)
        }
    }
}
