package com.material.xray.feature.routing

import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.material.xray.core.data.parser.ProfileRoutingRule
import com.material.xray.core.data.parser.ProfileRoutingTarget
import com.material.xray.core.model.RoutingPolicyControl
import com.material.xray.core.model.RoutingRule
import com.material.xray.core.model.RoutingRuleCatalog
import com.material.xray.core.model.RoutingRuleScope
import com.material.xray.core.ui.R
import com.material.xray.core.ui.adaptive.TwoPaneMinWidth
import com.material.xray.core.ui.components.AnimatedDropdownMenu
import com.material.xray.core.ui.components.AppBarTitle
import com.material.xray.core.ui.components.AppTopBarHeight
import com.material.xray.core.ui.components.SegmentedTabRow
import com.material.xray.core.ui.components.TooltipIconButton
import com.material.xray.core.xray.ProviderGeoDataNotice
import com.material.xray.feature.routing.apps.AppBypassContent
import com.material.xray.feature.routing.apps.AppRoutingMenuActions
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class RoutingTab(@StringRes val titleResource: Int) {
    Rules(R.string.routing_tab_rules),
    Apps(R.string.routing_tab_apps),
}

private sealed interface RoutingRuleAction {
    data object Add : RoutingRuleAction
    data object EnableAll : RoutingRuleAction
    data object DisableAll : RoutingRuleAction
    data object ResetToDefault : RoutingRuleAction
    data class Edit(val rule: RoutingRule) : RoutingRuleAction
    data class Toggle(val rule: RoutingRule, val enabled: Boolean) : RoutingRuleAction
    data class Delete(val ruleIds: Set<String>) : RoutingRuleAction
}

/** Changes to rules the subscription supplied, which take routing manual while it is automatic. */
private sealed interface ProviderRoutingRuleAction {
    data class ToggleProfile(val rule: ProfileRoutingRule, val enabled: Boolean) : ProviderRoutingRuleAction
    data class ToggleSubscription(val rule: RoutingRule, val enabled: Boolean) : ProviderRoutingRuleAction
}

private val defaultRoutingRulesById = RoutingRuleCatalog.defaults().associateBy(RoutingRule::id)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    showTitleBarLogo: Boolean,
    onViewRule: (RoutingRuleViewerRequest) -> Unit,
    onEditRule: (EditableRoutingRule) -> Unit,
    viewModel: RoutingViewModel = koinViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val scopeOrder by viewModel.scopeOrder.collectAsStateWithLifecycle()
    val subscriptionRules by viewModel.subscriptionRules.collectAsStateWithLifecycle()
    val routingPolicyControl by viewModel.routingPolicyControl.collectAsStateWithLifecycle()
    val automaticRoutingProviderName by viewModel.automaticRoutingProviderName.collectAsStateWithLifecycle()
    val profileRouting by viewModel.profileRouting.collectAsStateWithLifecycle()
    val providerGeoDataNotice by viewModel.providerGeoDataNotice.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { RoutingTab.entries.size })
    val rulesSearchQuery = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    val coroutineScope = rememberCoroutineScope()
    var previousTab by remember { mutableIntStateOf(pagerState.currentPage) }
    var selectedRuleIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingProviderAction by remember { mutableStateOf<ProviderRoutingRuleAction?>(null) }
    var confirmResetToDefault by remember { mutableStateOf(false) }
    val selectionMode by remember { derivedStateOf { selectedRuleIds.isNotEmpty() } }
    val selectedTab = pagerState.currentPage
    val newRuleName = stringResource(R.string.routing_new_rule_name)
    var wasSelecting by remember { mutableStateOf(false) }

    LaunchedEffect(selectionMode) {
        if (wasSelecting && !selectionMode) viewModel.applyPendingChangesIfNeeded()
        wasSelecting = selectionMode
    }

    BackHandler(enabled = selectionMode) {
        selectedRuleIds = emptySet()
    }

    LaunchedEffect(rules, subscriptionRules, profileRouting) {
        val available = rules.map { routingSelectionKey(RoutingRuleScope.Custom, it.id) } +
            subscriptionRules.map { routingSelectionKey(RoutingRuleScope.Subscription, it.id) } +
            profileRouting?.rules.orEmpty().map { it.selectionKey() }
        selectedRuleIds = selectedRuleIds.intersect(available.toSet())
    }

    LaunchedEffect(profileRouting?.serverId) { selectedRuleIds = emptySet() }

    fun applyRuleAction(action: RoutingRuleAction) {
        when (action) {
            RoutingRuleAction.Add -> {
                onEditRule(
                    EditableRoutingRule(
                        rule = RoutingRule(
                            id = "custom-${System.currentTimeMillis()}",
                            name = newRuleName,
                            outboundTag = "proxy",
                        ),
                        isNew = true,
                    ),
                )
            }
            RoutingRuleAction.EnableAll -> viewModel.setAllRulesEnabled(true)
            RoutingRuleAction.DisableAll -> viewModel.setAllRulesEnabled(false)
            RoutingRuleAction.ResetToDefault -> confirmResetToDefault = true
            is RoutingRuleAction.Edit -> onEditRule(EditableRoutingRule(rule = action.rule, isNew = false))
            is RoutingRuleAction.Toggle -> viewModel.updateRule(action.rule.copy(enabled = action.enabled))
            is RoutingRuleAction.Delete -> {
                viewModel.deleteRules(action.ruleIds)
                selectedRuleIds = emptySet()
            }
        }
    }

    fun applyProviderRuleAction(action: ProviderRoutingRuleAction) {
        when (action) {
            is ProviderRoutingRuleAction.ToggleProfile -> viewModel.setProfileRuleEnabled(action.rule, action.enabled)
            is ProviderRoutingRuleAction.ToggleSubscription ->
                viewModel.updateSubscriptionRule(action.rule.copy(enabled = action.enabled))
        }
    }

    fun requestProviderRuleAction(action: ProviderRoutingRuleAction) {
        if (routingPolicyControl == RoutingPolicyControl.SubscriptionProvider) {
            pendingProviderAction = action
        } else {
            applyProviderRuleAction(action)
        }
    }

    LaunchedEffect(routingPolicyControl) {
        if (routingPolicyControl == RoutingPolicyControl.User) {
            pendingProviderAction?.let(::applyProviderRuleAction)
            pendingProviderAction = null
        }
    }

    LaunchedEffect(selectedTab) {
        if (previousTab != selectedTab) {
            if (previousTab == RoutingTab.Rules.ordinal) {
                selectedRuleIds = emptySet()
            }
            viewModel.applyPendingChangesIfNeeded()
        }
        previousTab = selectedTab
    }

    val rulesTab: @Composable () -> Unit = {
        RoutingRulesTab(
            searchQuery = rulesSearchQuery,
            scopeOrder = scopeOrder,
            customRules = rules,
            subscriptionRules = subscriptionRules,
            profileRules = profileRouting?.rules.orEmpty(),
            profileServerId = profileRouting?.serverId,
            providerManaged = routingPolicyControl == RoutingPolicyControl.SubscriptionProvider,
            providerName = automaticRoutingProviderName,
            providerGeoDataNotice = providerGeoDataNotice,
            onProviderGeoDataNoticeClick = viewModel::onProviderGeoDataNoticeClick,
            selectionMode = selectionMode,
            selectedRuleIds = selectedRuleIds,
            actions = RoutingRuleActions(
                onRuleToggled = { rule, enabled -> applyRuleAction(RoutingRuleAction.Toggle(rule, enabled)) },
                onRuleClick = { rule ->
                    if (selectionMode) {
                        selectedRuleIds = selectedRuleIds.toggle(routingSelectionKey(RoutingRuleScope.Custom, rule.id))
                    } else {
                        applyRuleAction(RoutingRuleAction.Edit(rule))
                    }
                },
                onRuleLongClick = { rule ->
                    selectedRuleIds = selectedRuleIds + routingSelectionKey(RoutingRuleScope.Custom, rule.id)
                },
                onSubscriptionRuleClick = { rule ->
                    if (selectionMode) {
                        selectedRuleIds = selectedRuleIds.toggle(routingSelectionKey(RoutingRuleScope.Subscription, rule.id))
                    } else {
                        onEditRule(EditableRoutingRule(rule = rule, isNew = false, subscriptionWide = true))
                    }
                },
                onSubscriptionRuleLongClick = { rule -> selectedRuleIds = selectedRuleIds + routingSelectionKey(RoutingRuleScope.Subscription, rule.id) },
                onSubscriptionRuleToggled = { rule, enabled ->
                    requestProviderRuleAction(ProviderRoutingRuleAction.ToggleSubscription(rule, enabled))
                },
                onProfileRuleClick = { rule ->
                    val editableRule = rule.editableRule
                    if (selectionMode) {
                        selectedRuleIds = selectedRuleIds.toggle(rule.selectionKey())
                    } else if (rule.orphaned || editableRule == null) {
                        onViewRule(rule.toViewerRequest())
                    } else {
                        onEditRule(
                            EditableRoutingRule(
                                rule = editableRule,
                                isNew = false,
                                profileOriginalRuleJson = rule.originalRuleJson,
                                profileOriginalIndex = rule.originalIndex,
                                rawJson = rule.rawJson,
                            ),
                        )
                    }
                },
                onProfileRuleLongClick = { rule -> selectedRuleIds = selectedRuleIds + rule.selectionKey() },
                onProfileRuleToggled = { rule, enabled ->
                    requestProviderRuleAction(ProviderRoutingRuleAction.ToggleProfile(rule, enabled))
                },
                onScopeLongClick = { scope ->
                    selectedRuleIds = routingScopeSelectionKeys(scope, rules, subscriptionRules, profileRouting?.rules.orEmpty())
                },
                onRulesReordered = viewModel::reorderRules,
                onProfileRulesReordered = { ordered -> profileRouting?.serverId?.let { viewModel.reorderProfileRules(it, ordered) } },
                onScopesReordered = viewModel::reorderScopes,
            ),
        )
    }
    val ruleActions: @Composable () -> Unit = {
        RuleActions(
            selectionMode = selectionMode,
            selectedRuleIds = selectedRuleIds,
            rules = rules,
            onClearSelection = {
                selectedRuleIds = emptySet()
            },
            onRuleAction = ::applyRuleAction,
        )
    }

    RoutingLayout(
        pagerState = pagerState,
        showTitleBarLogo = showTitleBarLogo,
        selectionCount = selectedRuleIds.size.takeIf { selectionMode },
        onSelectTab = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
        ruleActions = ruleActions,
        rulesTab = rulesTab,
    )

    if (pendingProviderAction != null) {
        AutomaticRuleRoutingDialog(
            providerName = automaticRoutingProviderName,
            onDismiss = { pendingProviderAction = null },
            onSwitchToManual = viewModel::switchToManualRouting,
        )
    }

    if (confirmResetToDefault) {
        AlertDialog(
            onDismissRequest = { confirmResetToDefault = false },
            title = { Text(stringResource(R.string.routing_reset_to_default_title)) },
            text = { Text(stringResource(R.string.routing_reset_to_default_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetRulesToDefaults()
                        confirmResetToDefault = false
                    },
                ) {
                    Text(stringResource(R.string.routing_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetToDefault = false }) {
                    Text(stringResource(R.string.routing_cancel))
                }
            },
        )
    }
}

@Composable
private fun RoutingLayout(
    pagerState: PagerState,
    showTitleBarLogo: Boolean,
    selectionCount: Int?,
    onSelectTab: (Int) -> Unit,
    ruleActions: @Composable () -> Unit,
    rulesTab: @Composable () -> Unit,
) {
    val selectedTab = pagerState.currentPage
    BoxWithConstraints {
        // With the width for it, rules and apps sit side by side, each with its own actions, so
        // neither has to be paged away to reach the other.
        val sideBySide = maxWidth >= TwoPaneMinWidth
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                RoutingTopBar(
                    showTitleBarLogo = showTitleBarLogo,
                    selectionCount = selectionCount.takeIf { sideBySide || selectedTab == RoutingTab.Rules.ordinal },
                    actions = {
                        // Side by side, each pane carries its own actions instead.
                        if (!sideBySide) {
                            PagedTabActions(appsTabSelected = selectedTab == RoutingTab.Apps.ordinal, ruleActions = ruleActions)
                        }
                    },
                )
            },
            bottomBar = {
                if (!sideBySide) {
                    SegmentedTabRow(
                        labels = RoutingTab.entries.map { stringResource(it.titleResource) },
                        selectedIndex = selectedTab,
                        onSelected = onSelectTab,
                    )
                }
            },
        ) { padding ->
            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    RoutingPane(
                        title = stringResource(RoutingTab.Rules.titleResource),
                        actions = { ruleActions() },
                        modifier = Modifier.weight(1f),
                        content = rulesTab,
                    )
                    VerticalDivider()
                    RoutingPane(
                        title = stringResource(RoutingTab.Apps.titleResource),
                        actions = { AppRoutingMenuActions() },
                        modifier = Modifier.weight(1f),
                    ) {
                        AppBypassContent(active = true)
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) { page ->
                    when (RoutingTab.entries[page]) {
                        RoutingTab.Rules -> rulesTab()
                        RoutingTab.Apps -> AppBypassContent(active = selectedTab == RoutingTab.Apps.ordinal)
                    }
                }
            }
        }
    }
}

@Composable
private fun PagedTabActions(appsTabSelected: Boolean, ruleActions: @Composable () -> Unit) {
    if (appsTabSelected) {
        AppRoutingMenuActions()
    } else {
        ruleActions()
    }
}

@Composable
private fun RoutingPane(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            actions()
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingTopBar(
    showTitleBarLogo: Boolean,
    selectionCount: Int?,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        title = {
            AppBarTitle(
                if (selectionCount != null) {
                    pluralStringResource(R.plurals.routing_rules_selected, selectionCount, selectionCount)
                } else {
                    stringResource(R.string.routing_title)
                },
                showTitleBarLogo,
            )
        },
        expandedHeight = AppTopBarHeight,
        windowInsets = TopAppBarDefaults.windowInsets,
        actions = actions,
    )
}

@Composable
private fun RuleActions(
    selectionMode: Boolean,
    selectedRuleIds: Set<String>,
    rules: List<RoutingRule>,
    onClearSelection: () -> Unit,
    onRuleAction: (RoutingRuleAction) -> Unit,
) {
    var rulesMenuExpanded by remember { mutableStateOf(false) }
    when {
        selectionMode -> {
            TooltipIconButton(tooltip = stringResource(R.string.routing_clear_selection), onClick = onClearSelection) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.routing_clear_selection),
                    modifier = Modifier.size(24.dp),
                )
            }
            if (selectedRuleIds.all { it.startsWith("custom:") }) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.routing_delete_selected_rules),
                    onClick = { onRuleAction(RoutingRuleAction.Delete(selectedRuleIds.mapTo(mutableSetOf()) { it.removePrefix("custom:") })) },
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.routing_delete_selected_rules),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        else -> {
            TooltipIconButton(tooltip = stringResource(R.string.routing_add_rule), onClick = { onRuleAction(RoutingRuleAction.Add) }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.routing_add_rule),
                    modifier = Modifier.size(24.dp),
                )
            }
            Box {
                TooltipIconButton(tooltip = stringResource(R.string.routing_rules_menu), onClick = { rulesMenuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.routing_rules_menu),
                        modifier = Modifier.size(24.dp),
                    )
                }
                AnimatedDropdownMenu(
                    expanded = rulesMenuExpanded,
                    onDismissRequest = { rulesMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routing_enable_all)) },
                        enabled = rules.any { !it.enabled },
                        onClick = {
                            rulesMenuExpanded = false
                            onRuleAction(RoutingRuleAction.EnableAll)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routing_disable_all)) },
                        enabled = rules.any { it.enabled },
                        onClick = {
                            rulesMenuExpanded = false
                            onRuleAction(RoutingRuleAction.DisableAll)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.routing_reset_to_default)) },
                        onClick = {
                            rulesMenuExpanded = false
                            onRuleAction(RoutingRuleAction.ResetToDefault)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProviderGeoDataBanner(
    notice: ProviderGeoDataNotice,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (containerColor, contentColor) = when (notice) {
        ProviderGeoDataNotice.CompatibilityMode -> colors.errorContainer to colors.onErrorContainer
        ProviderGeoDataNotice.ReadyToApply -> colors.tertiaryContainer to colors.onTertiaryContainer
        ProviderGeoDataNotice.Downloading -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val (message, action) = when (notice) {
        ProviderGeoDataNotice.Downloading -> R.string.routing_provider_geodata_downloading to null
        ProviderGeoDataNotice.CompatibilityMode ->
            R.string.routing_provider_geodata_compatibility to R.string.routing_provider_geodata_retry
        ProviderGeoDataNotice.ReadyToApply ->
            R.string.routing_provider_geodata_ready to R.string.routing_provider_geodata_apply
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = action != null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (notice) {
                ProviderGeoDataNotice.Downloading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
                ProviderGeoDataNotice.CompatibilityMode -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                ProviderGeoDataNotice.ReadyToApply -> Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = stringResource(message), style = MaterialTheme.typography.bodySmall)
                action?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** What tapping, long-pressing and toggling the listed rules does. */
internal data class RoutingRuleActions(
    val onRuleToggled: (RoutingRule, Boolean) -> Unit,
    val onRuleClick: (RoutingRule) -> Unit,
    val onRuleLongClick: (RoutingRule) -> Unit,
    val onSubscriptionRuleClick: (RoutingRule) -> Unit,
    val onSubscriptionRuleLongClick: (RoutingRule) -> Unit,
    val onSubscriptionRuleToggled: (RoutingRule, Boolean) -> Unit,
    val onProfileRuleClick: (ProfileRoutingRule) -> Unit,
    val onProfileRuleLongClick: (ProfileRoutingRule) -> Unit,
    val onProfileRuleToggled: (ProfileRoutingRule, Boolean) -> Unit,
    val onScopeLongClick: (RoutingRuleScope) -> Unit,
    val onRulesReordered: (RoutingRuleScope, List<String>) -> Unit,
    val onProfileRulesReordered: (List<ProfileRoutingRule>) -> Unit,
    val onScopesReordered: (List<RoutingRuleScope>) -> Unit,
)

@Composable
internal fun AutomaticRuleRoutingDialog(
    providerName: String?,
    onDismiss: () -> Unit,
    onSwitchToManual: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.routing_rules_automatic_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                providerName?.let {
                    stringResource(R.string.routing_rules_automatic_provider_description, it)
                } ?: stringResource(R.string.routing_rules_automatic_selected_subscription_description),
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.routing_leave_as_is))
                }
                OutlinedButton(
                    onClick = onSwitchToManual,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.routing_switch_to_manual_mode))
                }
            }
        },
    )
}

@Composable
internal fun routingRuleContentText(rule: RoutingRule): String {
    val domains = rule.domains.map(String::trim).filter(String::isNotEmpty)
    val ips = rule.ips.map(String::trim).filter(String::isNotEmpty)
    val protocols = rule.protocols.map(String::trim).filter(String::isNotEmpty)
    val domainText = compactDomainText(domains)
    val ipText = compactListText(ips, R.plurals.routing_rule_ips, R.plurals.routing_rule_more_ips)
    val portText = rule.port?.takeIf(String::isNotBlank)?.let {
        stringResource(R.string.routing_rule_port, it)
    }
    val protocolText = compactListText(
        protocols,
        R.plurals.routing_rule_protocols,
        R.plurals.routing_rule_more_protocols,
    )
    return listOfNotNull(domainText, ipText, portText, protocolText)
        .joinToString("\n")
        .ifBlank { stringResource(R.string.routing_no_match_content) }
}

@Composable
internal fun profileRoutingRuleContentText(rule: ProfileRoutingRule): String {
    val domainText = compactDomainText(rule.domains)
    val ipText = compactListText(rule.ips, R.plurals.routing_rule_ips, R.plurals.routing_rule_more_ips)
    val portText = rule.port?.let { stringResource(R.string.routing_rule_port, it) }
    val protocolText = compactListText(
        rule.protocols,
        R.plurals.routing_rule_protocols,
        R.plurals.routing_rule_more_protocols,
    )
    val additionalText = rule.additionalConditionFields.takeIf(List<String>::isNotEmpty)?.let {
        stringResource(R.string.routing_rule_additional_conditions, it.joinToString(", "))
    }
    return listOfNotNull(domainText, ipText, portText, protocolText, additionalText)
        .joinToString("\n")
        .ifBlank { stringResource(R.string.routing_no_match_content) }
}

@Composable
private fun compactDomainText(domains: List<String>): String? {
    if (domains.isEmpty()) return null
    val preview = routingDomainPreview(domains)
    val visibleText = preview.visibleValues.joinToString(", ")
    val domainList = if (preview.omittedCount > 0) {
        pluralStringResource(
            R.plurals.routing_rule_more_domains,
            preview.omittedCount,
            visibleText,
            preview.omittedCount,
        )
    } else {
        visibleText
    }
    return pluralStringResource(R.plurals.routing_rule_domains, domains.size, domainList)
}

@Composable
private fun compactListText(
    values: List<String>,
    @PluralsRes labelResource: Int,
    @PluralsRes moreResource: Int,
): String? {
    val preview = routingListPreview(values)
    if (preview.visibleValues.isEmpty()) return null
    val visibleText = preview.visibleValues.joinToString(", ")
    val valueList = if (preview.omittedCount > 0) {
        pluralStringResource(moreResource, preview.omittedCount, visibleText, preview.omittedCount)
    } else {
        visibleText
    }
    return pluralStringResource(labelResource, values.size, valueList)
}

private fun ProfileRoutingRule.toViewerRequest(): RoutingRuleViewerRequest = RoutingRuleViewerRequest(
    name = name,
    domains = domains,
    ips = ips,
    port = port,
    protocols = protocols,
    targetKind = when (target) {
        is ProfileRoutingTarget.Outbound -> RoutingRuleViewerTargetKind.Outbound
        is ProfileRoutingTarget.Balancer -> RoutingRuleViewerTargetKind.Balancer
        null -> null
    },
    targetTag = target?.tag,
    additionalConditionFields = additionalConditionFields,
    rawJson = rawJson,
)

@Composable
internal fun ProfileRoutingTarget.displayText(): String = when (this) {
    is ProfileRoutingTarget.Outbound -> stringResource(R.string.routing_rule_target_outbound, tag)
    is ProfileRoutingTarget.Balancer -> stringResource(R.string.routing_rule_target_balancer, tag)
}

@Composable
internal fun routingRuleDisplayName(rule: RoutingRule): String {
    if (rule.name != defaultRoutingRulesById[rule.id]?.name) return rule.name
    return when (rule.id) {
        "ru-direct" -> stringResource(R.string.routing_default_rule_ru_name)
        "block-ads" -> stringResource(R.string.routing_default_rule_block_ads_name)
        else -> rule.name
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

private fun routingScopeSelectionKeys(scope: RoutingRuleScope, custom: List<RoutingRule>, subscription: List<RoutingRule>, profile: List<ProfileRoutingRule>): Set<String> = when (scope) {
    RoutingRuleScope.Custom -> custom.mapTo(mutableSetOf()) { routingSelectionKey(scope, it.id) }
    RoutingRuleScope.Subscription -> subscription.mapTo(mutableSetOf()) { routingSelectionKey(scope, it.id) }
    RoutingRuleScope.Profile -> profile.mapTo(mutableSetOf()) { it.selectionKey() }
}
