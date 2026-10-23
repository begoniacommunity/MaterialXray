package com.material.xray.feature.routing

import androidx.activity.compose.BackHandler
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.material.xray.core.ui.components.AnimatedToolbarActions
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.AppBarTitle
import com.material.xray.core.ui.components.FloatingTab
import com.material.xray.core.ui.components.FloatingTabRow
import com.material.xray.core.ui.components.LocalBottomBarVisibility
import com.material.xray.core.ui.components.ScrolledTopAppBar
import com.material.xray.core.ui.components.SearchTabsPanel
import com.material.xray.core.ui.components.ToolbarAction
import com.material.xray.core.ui.components.TooltipIconButton
import com.material.xray.core.ui.components.searchPanelFraction
import com.material.xray.core.ui.components.tabsPanelState
import com.material.xray.core.xray.ProviderGeoDataNotice
import com.material.xray.feature.routing.apps.AppBypassContent
import com.material.xray.feature.routing.apps.AppsViewModel
import com.material.xray.feature.routing.apps.appRoutingToolbarActions
import com.material.xray.feature.routing.network.NetworkRoutingContent
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class RoutingTab(@StringRes val titleResource: Int) {
    Rules(R.string.routing_tab_rules),
    Apps(R.string.routing_tab_apps),
    Network(R.string.network_tab),
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
    searchRevision: Int = 0,
    viewModel: RoutingViewModel = koinViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val scopeOrder by viewModel.scopeOrder.collectAsStateWithLifecycle()
    val subscriptionRules by viewModel.subscriptionRules.collectAsStateWithLifecycle()
    val subscriptionSourceId by viewModel.subscriptionRoutingSourceId.collectAsStateWithLifecycle()
    val subscriptionSources by viewModel.subscriptionRoutingSources.collectAsStateWithLifecycle()
    val routingPolicyControl by viewModel.routingPolicyControl.collectAsStateWithLifecycle()
    val automaticRoutingProviderName by viewModel.automaticRoutingProviderName.collectAsStateWithLifecycle()
    val profileRouting by viewModel.profileRouting.collectAsStateWithLifecycle()
    val providerGeoDataNotice by viewModel.providerGeoDataNotice.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { RoutingTab.entries.size })
    val rulesSearchQuery = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    val appsViewModel = koinViewModel<AppsViewModel>()
    val appsSearchQuery = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    LaunchedEffect(appsSearchQuery) { snapshotFlow { appsSearchQuery.text.toString() }.collect(appsViewModel::setSearchQuery) }
    val rulesQuery = rulesSearchQuery.text.toString()
    val ruleResultCount = rules.count { it.matchesSearch(rulesQuery, routingRuleDisplayName(it)) } +
        subscriptionRules.count { it.matchesSearch(rulesQuery, routingRuleDisplayName(it)) } +
        profileRouting?.rules.orEmpty().count { it.matchesSearch(rulesQuery) }
    val apps by appsViewModel.apps.collectAsStateWithLifecycle()
    val search = rememberRoutingSearch(pagerState, rulesSearchQuery, appsSearchQuery, ruleResultCount, apps.size, searchRevision)

    val coroutineScope = rememberCoroutineScope()
    var previousTab by remember { mutableIntStateOf(pagerState.currentPage) }
    var selectedRuleIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingProviderAction by remember { mutableStateOf<ProviderRoutingRuleAction?>(null) }
    var confirmResetToDefault by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<RoutingResetTarget?>(null) }
    var rulesToRemove by remember { mutableStateOf<Set<String>?>(null) }
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
    LaunchedEffect(profileRouting?.serverId, subscriptionSourceId) { resetTarget = null }

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
                rulesToRemove = action.ruleIds
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

    val rulesTab: @Composable (Dp) -> Unit = { bottomPadding ->
        RoutingRulesTab(
            contentBottomPadding = bottomPadding,
            searchQuery = rulesSearchQuery,
            scopeOrder = scopeOrder,
            customRules = rules,
            subscriptionRules = subscriptionRules,
            profileRules = profileRouting?.rules.orEmpty(),
            profileServerId = profileRouting?.serverId,
            canResetSubscriptionRules = canResetSubscriptionRules(subscriptionSources, subscriptionSourceId),
            providerManaged = routingPolicyControl == RoutingPolicyControl.SubscriptionProvider,
            providerName = automaticRoutingProviderName,
            providerGeoDataNotice = providerGeoDataNotice,
            onProviderGeoDataNoticeClick = viewModel::onProviderGeoDataNoticeClick,
            selectionMode = selectionMode,
            selectedRuleIds = selectedRuleIds,
            onSelectionChange = { selectedRuleIds = it },
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
                onScopeReset = { scope ->
                    resetTarget = routingResetTarget(scope, profileRouting?.serverId, subscriptionSourceId)
                },
            ),
        )
    }
    val ruleActions: @Composable (Set<String>) -> List<ToolbarAction?> = { headerSelectedIds ->
        ruleToolbarActions(
            selectionMode = headerSelectedIds.isNotEmpty(),
            selectedRuleIds = headerSelectedIds,
            rules = rules,
            onRuleAction = ::applyRuleAction,
        )
    }

    RoutingLayout(
        pagerState = pagerState,
        showTitleBarLogo = showTitleBarLogo,
        selectedRuleIds = selectedRuleIds,
        onClearSelection = { selectedRuleIds = emptySet() },
        onSelectTab = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
        ruleActions = ruleActions,
        rulesTab = rulesTab,
        search = search,
    )

    if (pendingProviderAction != null) {
        AutomaticRuleRoutingDialog(
            providerName = automaticRoutingProviderName,
            onDismiss = { pendingProviderAction = null },
            onSwitchToManual = viewModel::switchToManualRouting,
        )
    }

    RoutingRulesResetDialog(
        target = resetTarget,
        sources = subscriptionSources,
        onDismiss = { resetTarget = null },
        onServerReset = viewModel::resetServerRules,
        onSubscriptionReset = viewModel::resetSubscriptionRules,
    )

    RemoveRulesDialog(
        ruleIds = rulesToRemove,
        onDismiss = { rulesToRemove = null },
        onConfirm = { ids ->
            viewModel.deleteRules(ids)
            rulesToRemove = null
            selectedRuleIds = emptySet()
        },
    )

    if (confirmResetToDefault) {
        AppAlertDialog(
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
private fun rememberRoutingSearch(
    pagerState: PagerState,
    rulesSearchQuery: TextFieldState,
    appsSearchQuery: TextFieldState,
    ruleResultCount: Int,
    appResultCount: Int,
    searchRevision: Int,
): RoutingSearchState {
    var searchRequested by rememberSaveable { mutableStateOf(false) }
    var searchTab by rememberSaveable { mutableIntStateOf(pagerState.currentPage) }
    var savedSearchRevision by rememberSaveable { mutableIntStateOf(searchRevision) }
    var searchClosing by remember { mutableStateOf(false) }
    val currentSearch = savedSearchRevision == searchRevision
    val searching = currentSearch && (searchRequested || searchClosing)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val searchQuery = if (searchTab == RoutingTab.Apps.ordinal) appsSearchQuery else rulesSearchQuery
    LaunchedEffect(pagerState.currentPage) { searchTab = pagerState.currentPage }
    fun closeSearch() {
        searchClosing = imeBottom > 0
        searchRequested = false
        rulesSearchQuery.edit { replace(0, length, "") }
        appsSearchQuery.edit { replace(0, length, "") }
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    LaunchedEffect(searchRevision) {
        if (!currentSearch) {
            // Another tab may own input focus while this saved search is being reset.
            searchRequested = false
            searchClosing = false
            rulesSearchQuery.edit { replace(0, length, "") }
            appsSearchQuery.edit { replace(0, length, "") }
            savedSearchRevision = searchRevision
        }
    }
    val bottomBarVisibility = LocalBottomBarVisibility.current
    DisposableEffect(bottomBarVisibility, searching) {
        bottomBarVisibility?.value = !searching
        onDispose { bottomBarVisibility?.value = true }
    }
    var keyboardSeen by remember { mutableStateOf(false) }
    LaunchedEffect(searching, imeBottom) {
        if (searching && imeBottom > 0) keyboardSeen = true
        if (imeBottom == 0 && keyboardSeen) {
            keyboardSeen = false
            searchClosing = false
            if (searchRequested && searchQuery.text.isBlank()) closeSearch()
        }
    }
    BackHandler(enabled = currentSearch && searchRequested) {
        if (imeBottom > 0 && searchQuery.text.isNotBlank()) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        } else {
            closeSearch()
        }
    }

    return RoutingSearchState(
        query = searchQuery,
        tab = searchTab,
        requested = currentSearch && searchRequested,
        visible = searching,
        resultCount = if (searchTab == RoutingTab.Apps.ordinal) appResultCount else ruleResultCount,
        open = { target ->
            searchTab = target
            pagerState.requestScrollToPage(target)
            searchRequested = true
        },
        close = ::closeSearch,
    )
}

private data class RoutingSearchState(
    val query: TextFieldState,
    val tab: Int,
    val requested: Boolean,
    val visible: Boolean,
    val resultCount: Int,
    val open: (Int) -> Unit,
    val close: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingLayout(
    pagerState: PagerState,
    showTitleBarLogo: Boolean,
    selectedRuleIds: Set<String>,
    onClearSelection: () -> Unit,
    onSelectTab: (Int) -> Unit,
    ruleActions: @Composable (Set<String>) -> List<ToolbarAction?>,
    rulesTab: @Composable (Dp) -> Unit,
    search: RoutingSearchState,
) {
    val selectedTab = pagerState.currentPage
    val bottomPanelTransition = updateTransition(
        tabsPanelState(search.visible, selectedRuleIds.isNotEmpty()),
        label = "routingBottomPanel",
    )
    val searchFraction by bottomPanelTransition.searchPanelFraction()
    val rulesScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val appsScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val networkScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    BoxWithConstraints {
        val sideBySide = maxWidth >= TwoPaneMinWidth
        val appsSelected = selectedTab == RoutingTab.Apps.ordinal
        val networkSelected = selectedTab == RoutingTab.Network.ordinal
        val scrollBehavior = when {
            networkSelected -> networkScrollBehavior
            !sideBySide && appsSelected -> appsScrollBehavior
            else -> rulesScrollBehavior
        }
        val rulesActions = ruleActions(selectedRuleIds)
        val appsActions = appRoutingToolbarActions()
        val selecting = selectedRuleIds.isNotEmpty() && !networkSelected && (sideBySide || selectedTab == RoutingTab.Rules.ordinal)
        val searchLabel = stringResource(R.string.routing_search)
        fun toolbarActions(tab: RoutingTab) = routingToolbarActions(tab, rulesActions, appsActions, selecting, search, searchLabel)
        val searchHint = stringResource(if (search.tab == RoutingTab.Apps.ordinal) R.string.apps_search else R.string.routing_search_rules)
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                RoutingTopBar(
                    scrollBehavior = scrollBehavior,
                    showTitleBarLogo = showTitleBarLogo,
                    selectionCount = selectedRuleIds.size.takeIf { selecting },
                    onClearSelection = onClearSelection,
                    searching = search.visible,
                    searchResultCount = search.resultCount.takeIf { search.query.text.isNotBlank() },
                    onCloseSearch = search.close,
                    actions = {
                        if (!sideBySide || networkSelected) {
                            AnimatedToolbarActions(toolbarActions(RoutingTab.entries[selectedTab]))
                        }
                    },
                )
            },
            bottomBar = {
                SearchTabsPanel(
                    transition = bottomPanelTransition,
                    state = search.query,
                    requested = search.requested,
                    hint = searchHint,
                ) {
                    FloatingTabRow(
                        tabs = listOf(
                            FloatingTab(stringResource(RoutingTab.Rules.titleResource), R.drawable.rule_24px),
                            FloatingTab(stringResource(RoutingTab.Apps.titleResource), R.drawable.apps_24px),
                            FloatingTab(stringResource(RoutingTab.Network.titleResource), R.drawable.globe_24px),
                        ),
                        pagerState = pagerState,
                        onSelected = routingTabSelection(sideBySide, pagerState, onSelectTab),
                    )
                }
            },
        ) { padding ->
            if (sideBySide && !networkSelected) {
                Row(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                    RoutingPane(
                        title = stringResource(RoutingTab.Rules.titleResource),
                        actions = { AnimatedToolbarActions(toolbarActions(RoutingTab.Rules)) },
                        modifier = Modifier.weight(1f),
                        content = { rulesTab(padding.calculateBottomPadding()) },
                    )
                    VerticalDivider()
                    RoutingPane(
                        title = stringResource(RoutingTab.Apps.titleResource),
                        actions = { AnimatedToolbarActions(toolbarActions(RoutingTab.Apps)) },
                        modifier = Modifier.weight(1f),
                    ) { AppBypassContent(active = true, contentBottomPadding = padding.calculateBottomPadding()) }
                }
            } else {
                val viewportBottom = padding.calculateBottomPadding() * searchFraction
                val bottomPadding = padding.calculateBottomPadding() - viewportBottom
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !search.visible && !selecting,
                    modifier = Modifier.fillMaxSize().padding(PaddingValues(top = padding.calculateTopPadding(), bottom = viewportBottom)),
                ) { page ->
                    when (RoutingTab.entries[page]) {
                        RoutingTab.Rules -> rulesTab(bottomPadding)
                        RoutingTab.Apps -> AppBypassContent(active = selectedTab == RoutingTab.Apps.ordinal, contentBottomPadding = bottomPadding)
                        RoutingTab.Network -> NetworkRoutingContent(contentBottomPadding = bottomPadding)
                    }
                }
            }
        }
    }
}

private fun routingToolbarActions(
    tab: RoutingTab,
    rulesActions: List<ToolbarAction?>,
    appsActions: List<ToolbarAction>,
    selecting: Boolean,
    search: RoutingSearchState,
    searchLabel: String,
): List<ToolbarAction?> {
    if (tab == RoutingTab.Network) return listOf(null, null, null)
    val actions = if (tab == RoutingTab.Apps) appsActions else rulesActions
    val selectingRules = selecting && tab == RoutingTab.Rules
    if (search.visible && search.tab == tab.ordinal && !selectingRules) return listOf(null, null, null)
    val searchAction = if (selectingRules) {
        null
    } else {
        ToolbarAction(
            "search",
            R.drawable.find_in_page_24px,
            searchLabel,
            onClick = { search.open(tab.ordinal) },
        )
    }
    return listOf(actions[0], searchAction, actions[1])
}

private fun routingTabSelection(sideBySide: Boolean, pagerState: PagerState, onSelectTab: (Int) -> Unit): (Int) -> Unit = { index ->
    if (sideBySide) pagerState.requestScrollToPage(index) else onSelectTab(index)
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
    scrollBehavior: TopAppBarScrollBehavior,
    showTitleBarLogo: Boolean,
    selectionCount: Int?,
    onClearSelection: () -> Unit,
    searching: Boolean,
    searchResultCount: Int?,
    onCloseSearch: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val title = when {
        selectionCount != null -> pluralStringResource(R.plurals.routing_rules_selected, selectionCount, selectionCount)
        searching -> searchResultCount?.let { stringResource(R.string.routing_search_found_count, it) }.orEmpty()
        else -> stringResource(R.string.routing_title)
    }
    val closeLabel = when {
        selectionCount != null -> stringResource(R.string.routing_clear_selection)
        searching -> stringResource(R.string.routing_close_search)
        else -> null
    }
    // A navigation icon changes the title's layout inset. Crossfade complete layouts, as on
    // Logs, so the outgoing title keeps its original position. Tab changes keep this key.
    AnimatedContent(
        targetState = Triple(title, showTitleBarLogo && selectionCount == null && !searching, closeLabel),
        contentKey = { it.third != null },
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) using null },
        label = "routingHeaderLayout",
    ) { (text, logo, label) ->
        ScrolledTopAppBar(
            scrollBehavior = scrollBehavior,
            highlighted = selectionCount != null,
            title = {
                AnimatedContent(
                    targetState = text to logo,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) using null },
                    label = "routingTitle",
                ) { (titleText, showLogo) -> AppBarTitle(titleText, showLogo) }
            },
            navigationIcon = {
                if (label != null) {
                    TooltipIconButton(
                        tooltip = label,
                        onClick = {
                            if (closeLabel == label) {
                                if (selectionCount != null) onClearSelection() else onCloseSearch()
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.close_24px), contentDescription = label, modifier = Modifier.size(24.dp))
                    }
                }
            },
            actions = actions,
        )
    }
}

@Composable
private fun ruleToolbarActions(
    selectionMode: Boolean,
    selectedRuleIds: Set<String>,
    rules: List<RoutingRule>,
    onRuleAction: (RoutingRuleAction) -> Unit,
): List<ToolbarAction?> {
    val canRemoveSelected = selectedRuleIds.isNotEmpty() && selectedRuleIds.all { it.startsWith("custom:") }
    if (selectionMode) {
        return listOf(
            null,
            if (canRemoveSelected) {
                ToolbarAction("deleteRules", R.drawable.delete_24px, stringResource(R.string.routing_remove_selected_rules), onClick = {
                    onRuleAction(RoutingRuleAction.Delete(selectedRuleIds.mapTo(mutableSetOf()) { it.removePrefix("custom:") }))
                })
            } else {
                null
            },
        )
    }
    return listOf(
        ToolbarAction("addRule", R.drawable.ic_add_24, stringResource(R.string.routing_add_rule), onClick = { onRuleAction(RoutingRuleAction.Add) }),
        ToolbarAction("rulesMenu", R.drawable.more_horiz_24px, stringResource(R.string.routing_rules_menu), menu = { close ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routing_enable_all)) },
                enabled = rules.any { !it.enabled },
                onClick = {
                    close()
                    onRuleAction(RoutingRuleAction.EnableAll)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routing_disable_all)) },
                enabled = rules.any { it.enabled },
                onClick = {
                    close()
                    onRuleAction(RoutingRuleAction.DisableAll)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.routing_reset_to_default)) },
                onClick = {
                    close()
                    onRuleAction(RoutingRuleAction.ResetToDefault)
                },
            )
        }),
    )
}

@Composable
private fun RemoveRulesDialog(
    ruleIds: Set<String>?,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    if (ruleIds.isNullOrEmpty()) return
    AppAlertDialog(
        icon = { Icon(painterResource(R.drawable.delete_24px), contentDescription = null) },
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (ruleIds.size == 1) R.string.routing_remove_rule_title else R.string.routing_remove_rules_title))
        },
        text = { Text(pluralStringResource(R.plurals.routing_remove_rules_message, ruleIds.size, ruleIds.size)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(ruleIds) }) {
                Text(stringResource(R.string.routing_remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.routing_cancel))
            }
        },
    )
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
    val onScopeReset: (RoutingRuleScope) -> Unit,
)

@Composable
internal fun AutomaticRuleRoutingDialog(
    providerName: String?,
    onDismiss: () -> Unit,
    onSwitchToManual: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.routing_rules_automatic_title),
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
