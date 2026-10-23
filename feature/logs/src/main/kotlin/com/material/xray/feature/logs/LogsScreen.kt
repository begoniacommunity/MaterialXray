package com.material.xray.feature.logs

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.material.xray.core.common.log.LogEntry
import com.material.xray.core.common.log.LogSource
import com.material.xray.core.common.log.displayMessage
import com.material.xray.core.ui.R
import com.material.xray.core.ui.components.AnimatedDropdownMenu
import com.material.xray.core.ui.components.AppAlertDialog
import com.material.xray.core.ui.components.AppBarTitle
import com.material.xray.core.ui.components.FloatingButtonShadow
import com.material.xray.core.ui.components.FloatingTab
import com.material.xray.core.ui.components.FloatingTabFadeHeight
import com.material.xray.core.ui.components.FloatingTabRow
import com.material.xray.core.ui.components.LocalBottomBarVisibility
import com.material.xray.core.ui.components.ScrollFadeEdges
import com.material.xray.core.ui.components.ScrolledTopAppBar
import com.material.xray.core.ui.components.SearchTabsPanel
import com.material.xray.core.ui.components.TabsPanelState
import com.material.xray.core.ui.components.TooltipIconButton
import com.material.xray.core.ui.components.rememberLazyListSelectionGesture
import com.material.xray.core.ui.components.searchPanelFraction
import com.material.xray.core.ui.components.tabsPanelState
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class LogFilter(@param:StringRes val labelRes: Int, val source: LogSource?) {
    ALL(R.string.logs_filter_all, null),
    INTERNAL(R.string.logs_filter_internal, LogSource.APP),
    CORE(R.string.logs_filter_core, LogSource.XRAY),
}

internal enum class LogSeverity { ERROR, WARNING, NORMAL }

internal fun LogEntry.severity(): LogSeverity = when (source) {
    LogSource.XRAY -> when {
        displayMessage.startsWith("[Error]") -> LogSeverity.ERROR
        displayMessage.startsWith("[Warning]") -> LogSeverity.WARNING
        else -> LogSeverity.NORMAL
    }
    LogSource.APP -> if (message.startsWith("ERROR:", ignoreCase = true) || message.contains("fail", ignoreCase = true)) {
        LogSeverity.ERROR
    } else {
        LogSeverity.NORMAL
    }
}

private data class LogsActions(
    val onCancel: () -> Unit,
    val onCopy: (List<LogEntry>) -> Unit,
    val onSave: (Set<Long>) -> Unit,
    val onShare: (Set<Long>) -> Unit,
    val onClear: () -> Unit,
    val onSearch: () -> Unit,
    val onSelectAll: () -> Unit,
    val onRemoveSelection: () -> Unit,
)

private data class LogsHeaderState(
    val selecting: Boolean,
    val searching: Boolean,
    val emptyQuery: Boolean,
    val selectedEntries: List<LogEntry>,
    val resultCount: Int,
    val allSelected: Boolean,
)

@Composable
fun LogsScreen(showTitleBarLogo: Boolean, viewModel: LogsViewModel = koinViewModel()) {
    val allEntries by viewModel.entries.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { LogFilter.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val listStates = LogFilter.entries.map { rememberLazyListState() }
    val followTailStates = listStates.map { it.rememberFollowTail() }
    val density = LocalDensity.current
    var tabsPanelHeight by remember { mutableStateOf(0.dp) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val searchQuery = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    var searchRequested by rememberSaveable { mutableStateOf(false) }
    var searchClosing by remember { mutableStateOf(false) }
    val searching = searchRequested || searchClosing
    val bottomBarVisibility = LocalBottomBarVisibility.current
    DisposableEffect(bottomBarVisibility, searching) {
        bottomBarVisibility?.value = !searching
        onDispose { bottomBarVisibility?.value = true }
    }
    val query = if (searching) searchQuery.text.toString() else ""
    var isExporting by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectedEntries = remember(allEntries, selectedIds) { allEntries.filter { it.id in selectedIds } }
    val selectionMode = selectedIds.isNotEmpty()
    val bottomPanelTransition = updateTransition(
        tabsPanelState(searching, selectionMode),
        label = "logsBottomPanel",
    )
    val searchFraction by bottomPanelTransition.searchPanelFraction()
    val selectedFilter by remember { derivedStateOf { LogFilter.entries[pagerState.targetPage] } }
    val visibleEntries = remember(allEntries, selectedFilter, query) { visibleLogEntries(allEntries, selectedFilter.source, query) }
    val activePage = pagerState.currentPage
    val activeEntries = remember(allEntries, activePage, query) { visibleLogEntries(allEntries, LogFilter.entries[activePage].source, query) }

    fun closeSearch() {
        searchClosing = imeBottom > 0
        searchRequested = false
        selectedIds = emptySet()
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }
    LogSearchNavigation(searchRequested, searching, searchQuery.text.isNotEmpty(), imeBottom, ::closeSearch) {
        searchClosing = false
    }
    LaunchedEffect(allEntries) {
        selectedIds = selectedIds.intersect(allEntries.mapTo(mutableSetOf(), LogEntry::id))
    }
    LaunchedEffect(selectedFilter, query) { selectedIds = emptySet() }
    val saveLogsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { destination ->
        if (destination != null) {
            coroutineScope.launch {
                isExporting = true
                try {
                    viewModel.saveLogs(destination)
                    Toast.makeText(context, R.string.logs_saved, Toast.LENGTH_SHORT).show()
                } catch (_: IOException) {
                    Toast.makeText(context, R.string.logs_save_failed, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, R.string.logs_save_failed, Toast.LENGTH_SHORT).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }
    val actions = LogsActions(
        onCancel = { if (searching) closeSearch() else selectedIds = emptySet() },
        onCopy = { entries ->
            viewModel.copyEntries(entries)
            Toast.makeText(context, R.string.logs_copied, Toast.LENGTH_SHORT).show()
            selectedIds = emptySet()
        },
        onSave = { ids ->
            val fileName = viewModel.prepareExport(allEntries, ids, selectedFilter.source)
            try {
                saveLogsLauncher.launch(fileName)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.logs_save_failed, Toast.LENGTH_SHORT).show()
            }
        },
        onShare = { ids ->
            val snapshot = logExportSnapshot(allEntries, ids, selectedFilter.source)
            val fileName = logExportFileName(selectedFilter.source, ids.isNotEmpty())
            coroutineScope.launch {
                isExporting = true
                try {
                    shareLogFile(context, viewModel.createShareFile(snapshot, fileName))
                } catch (_: IOException) {
                    Toast.makeText(context, R.string.logs_share_failed, Toast.LENGTH_SHORT).show()
                } catch (_: IllegalArgumentException) {
                    Toast.makeText(context, R.string.logs_share_failed, Toast.LENGTH_SHORT).show()
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.logs_share_failed, Toast.LENGTH_SHORT).show()
                } finally {
                    isExporting = false
                }
            }
        },
        onClear = { confirmClear = true },
        onSelectAll = { selectedIds = visibleEntries.mapTo(mutableSetOf(), LogEntry::id) },
        onRemoveSelection = { selectedIds = emptySet() },
        onSearch = {
            selectedIds = emptySet()
            searchQuery.edit { replace(0, length, "") }
            searchClosing = false
            searchRequested = true
        },
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                AnimatedContent(
                    targetState = LogsHeaderState(
                        selectionMode,
                        searching,
                        query.isBlank(),
                        selectedEntries,
                        visibleEntries.size,
                        areAllLogEntriesSelected(visibleEntries, selectedIds),
                    ),
                    contentKey = { Triple(it.selecting, it.searching, it.emptyQuery) },
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) using null },
                    label = "logsHeader",
                ) { header ->
                    LogsTopBar(showTitleBarLogo, header, isExporting, actions)
                }
            },
            bottomBar = {
                SearchTabsPanel(
                    transition = bottomPanelTransition,
                    state = searchQuery,
                    requested = searchRequested,
                    hint = stringResource(R.string.logs_search),
                ) {
                    FloatingTabRow(
                        tabs = listOf(
                            FloatingTab(stringResource(LogFilter.ALL.labelRes), R.drawable.notes_24px),
                            FloatingTab(stringResource(LogFilter.INTERNAL.labelRes), R.drawable.android_24px),
                            FloatingTab(stringResource(LogFilter.CORE.labelRes), R.drawable.manufacturing_24px),
                        ),
                        pagerState = pagerState,
                        onSelected = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        modifier = Modifier.onSizeChanged { tabsPanelHeight = with(density) { it.height.toDp() } },
                    )
                }
            },
        ) { padding ->
            val viewportBottom = padding.calculateBottomPadding() * searchFraction
            val bottomPadding = padding.calculateBottomPadding() - viewportBottom
            Box(modifier = Modifier.fillMaxSize().padding(PaddingValues(top = padding.calculateTopPadding(), bottom = viewportBottom))) {
                HorizontalPager(state = pagerState, userScrollEnabled = !searching && !selectionMode, modifier = Modifier.fillMaxSize()) { page ->
                    val pageFilter = LogFilter.entries[page]
                    val entries = remember(allEntries, pageFilter, query) {
                        visibleLogEntries(allEntries, pageFilter.source, query)
                    }
                    LogEntriesList(
                        entries = entries,
                        listState = listStates[page],
                        followTail = followTailStates[page],
                        contentBottomPadding = bottomPadding,
                        selectedIds = selectedIds,
                        searching = searching,
                        searchQuery = query,
                        onSelectionChange = { selectedIds = it },
                        onSelect = { entry ->
                            selectedIds = if (entry.id in selectedIds) selectedIds - entry.id else selectedIds + entry.id
                        },
                    )
                    if (searching && query.isNotBlank() && entries.isEmpty()) {
                        Text(
                            stringResource(R.string.logs_search_no_matches),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ScrollFadeEdges(bottom = searching, top = !selectionMode)
            }
        }
        // Paint above the Scaffold, including its bottom-bar gradient, without changing its size.
        ScrollToBottomButton(
            visible = !followTailStates[activePage].value && activeEntries.isNotEmpty() && bottomPanelTransition.targetState == TabsPanelState.Tabs,
            onClick = {
                coroutineScope.launch {
                    listStates[activePage].animateScrollToItem(activeEntries.lastIndex)
                    followTailStates[activePage].value = true
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(bottom = (tabsPanelHeight - FloatingTabFadeHeight - 16.dp).coerceAtLeast(0.dp)),
        )
    }

    if (confirmClear) {
        AppAlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(painterResource(R.drawable.delete_24px), contentDescription = null) },
            title = { Text(stringResource(R.string.logs_clear_title)) },
            text = { Text(stringResource(R.string.logs_clear_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clear()
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.logs_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsTopBar(
    showLogo: Boolean,
    header: LogsHeaderState,
    exporting: Boolean,
    actions: LogsActions,
) {
    val selecting = header.selecting
    val searching = header.searching
    val emptyQuery = header.emptyQuery
    val searchHasResults = !emptyQuery && header.resultCount > 0
    val entries = header.selectedEntries
    ScrolledTopAppBar(
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        highlighted = selecting,
        title = { LogsTitle(showLogo, header) },
        navigationIcon = {
            if (searching) {
                TooltipIconButton(tooltip = stringResource(R.string.logs_close_search), onClick = actions.onCancel) {
                    Icon(painterResource(R.drawable.close_24px), contentDescription = stringResource(R.string.logs_close_search), modifier = Modifier.size(24.dp))
                }
            } else if (selecting) {
                TooltipIconButton(tooltip = stringResource(R.string.logs_cancel_selection), onClick = actions.onCancel) {
                    Icon(painterResource(R.drawable.close_24px), contentDescription = stringResource(R.string.logs_cancel_selection), modifier = Modifier.size(24.dp))
                }
            }
        },
        actions = {
            Row(modifier = Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selecting || !searching || searchHasResults) {
                    if (selecting && header.resultCount > 1) {
                        LogSelectionButton(header.allSelected, actions)
                    }
                    if (selecting) {
                        LogsExportMenu(exporting, actions, entries.mapTo(mutableSetOf(), LogEntry::id))
                        TooltipIconButton(tooltip = stringResource(R.string.logs_copy_selected), onClick = { actions.onCopy(entries) }) {
                            Icon(painterResource(R.drawable.content_copy_24px), contentDescription = stringResource(R.string.logs_copy_selected), modifier = Modifier.size(24.dp))
                        }
                    } else {
                        TooltipIconButton(tooltip = stringResource(R.string.logs_clear), onClick = actions.onClear) {
                            Icon(painterResource(R.drawable.delete_24px), contentDescription = stringResource(R.string.logs_clear), modifier = Modifier.size(24.dp))
                        }
                        if (!searching) {
                            TooltipIconButton(tooltip = stringResource(R.string.logs_search), onClick = actions.onSearch) {
                                Icon(painterResource(R.drawable.find_in_page_24px), contentDescription = stringResource(R.string.logs_search), modifier = Modifier.size(24.dp))
                            }
                        }
                        LogsExportMenu(exporting, actions)
                    }
                }
            }
        },
    )
}

@Composable
private fun LogSelectionButton(allSelected: Boolean, actions: LogsActions) {
    val label = stringResource(if (allSelected) R.string.logs_deselect else R.string.logs_select_all)
    TooltipIconButton(tooltip = label, onClick = if (allSelected) actions.onRemoveSelection else actions.onSelectAll) {
        Icon(
            painterResource(if (allSelected) R.drawable.deselect_24px else R.drawable.select_all_24px),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LogsTitle(showLogo: Boolean, header: LogsHeaderState) {
    if (header.searching && header.emptyQuery && !header.selecting) return
    val found = stringResource(R.string.logs_found_count, header.resultCount)
    Column {
        AppBarTitle(
            when {
                header.selecting -> stringResource(R.string.logs_selected_count, header.selectedEntries.size)
                header.searching && !header.emptyQuery -> found
                else -> stringResource(R.string.navigation_logs)
            },
            showLogo && !header.selecting && !header.searching,
        )
        if (header.selecting && header.searching && !header.emptyQuery) {
            Text(found, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LogsExportMenu(exporting: Boolean, actions: LogsActions, selectedIds: Set<Long> = emptySet()) {
    val exportLabel = stringResource(if (selectedIds.isEmpty()) R.string.logs_export else R.string.logs_export_selected)
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        TooltipIconButton(tooltip = exportLabel, enabled = !exporting, onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.upload_24px), contentDescription = exportLabel, modifier = Modifier.size(24.dp))
        }
        AnimatedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.logs_save_to_file)) },
                leadingIcon = { Icon(painterResource(R.drawable.file_save_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                onClick = {
                    expanded = false
                    actions.onSave(selectedIds)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.logs_share)) },
                leadingIcon = { Icon(painterResource(R.drawable.share_24px), contentDescription = null, modifier = Modifier.size(24.dp)) },
                onClick = {
                    expanded = false
                    actions.onShare(selectedIds)
                },
            )
        }
    }
}

private fun shareLogFile(context: Context, uri: Uri) {
    val label = context.getString(
        R.string.clipboard_label_logs,
        context.getString(R.string.app_name),
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, label)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(label, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.logs_share)))
}

@Composable
private fun LogSearchNavigation(
    searchRequested: Boolean,
    searching: Boolean,
    hasQuery: Boolean,
    imeBottom: Int,
    onClose: () -> Unit,
    onClosed: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = searchRequested) {
        if (imeBottom > 0 && hasQuery) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        } else {
            onClose()
        }
    }
    var keyboardSeen by remember { mutableStateOf(false) }
    LaunchedEffect(searching, imeBottom) {
        if (searching && imeBottom > 0) keyboardSeen = true
        if (imeBottom == 0 && keyboardSeen) {
            keyboardSeen = false
            onClosed()
            if (searchRequested && !hasQuery) onClose()
        }
    }
}

@Composable
private fun ScrollToFirstSearchResult(state: LazyListState, query: String, searching: Boolean, hasEntries: Boolean) {
    LaunchedEffect(query) {
        if (searching && query.isNotBlank() && hasEntries) state.scrollToItem(0)
    }
}

@Composable
private fun FollowLatestLogEntry(state: LazyListState, entries: List<LogEntry>, enabled: Boolean) {
    LaunchedEffect(entries.lastOrNull()?.id, enabled) {
        if (enabled && entries.isNotEmpty()) state.scrollToItem(entries.lastIndex)
    }
}

@Composable
private fun LogEntriesList(
    entries: List<LogEntry>,
    listState: LazyListState,
    followTail: MutableState<Boolean>,
    contentBottomPadding: Dp,
    selectedIds: Set<Long>,
    searching: Boolean,
    searchQuery: String,
    onSelectionChange: (Set<Long>) -> Unit,
    onSelect: (LogEntry) -> Unit,
) {
    val selectionGesture = rememberLazyListSelectionGesture(
        listState = listState,
        keys = remember(entries) { entries.map(LogEntry::id) },
        selectedKeys = selectedIds,
        onSelectionChange = onSelectionChange,
    )

    val selectionMode = selectedIds.isNotEmpty()
    val autoScroll = followTail.value && !selectionMode && !selectionGesture.dragging && !searching
    FollowLatestLogEntry(listState, entries, autoScroll)

    ScrollToFirstSearchResult(listState, searchQuery, searching, entries.isNotEmpty())
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(selectionGesture.modifier),
            contentPadding = PaddingValues(top = 4.dp, bottom = contentBottomPadding),
        ) {
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.id },
                contentType = { _, entry -> entry.source },
            ) { index, entry ->
                LogEntryRow(
                    entry = entry,
                    showDivider = index < entries.lastIndex,
                    selected = entry.id in selectedIds,
                    onClick = { if (selectionMode) onSelect(entry) },
                    onLongClick = { onSelect(entry) },
                )
            }
        }
    }
}

@Composable
private fun ScrollToBottomButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        // Keep the drawn shadow inside the fading layer, just like AnimatedDropdownMenu.
        Box(
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .padding(16.dp),
        ) {
            SmallFloatingActionButton(
                onClick = onClick,
                modifier = Modifier.size(46.dp).dropShadow(
                    FloatingActionButtonDefaults.smallShape,
                    FloatingButtonShadow,
                ),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.logs_scroll_to_bottom),
                    modifier = Modifier.size(27.6.dp),
                )
            }
        }
    }
}

/**
 * Whether the list should follow new entries: true while the user is at the bottom. Scrolling up
 * clears it, and reaching the bottom again, by hand or with the button, sets it.
 */
@Composable
private fun LazyListState.rememberFollowTail(): MutableState<Boolean> {
    val followTail = remember { mutableStateOf(true) }
    LaunchedEffect(this) {
        snapshotFlow { canScrollForward to lastScrolledBackward }
            .collect { (canScrollForward, scrolledBackward) ->
                if (!canScrollForward) {
                    followTail.value = true
                } else if (scrolledBackward) {
                    followTail.value = false
                }
            }
    }
    return followTail
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogEntryRow(
    entry: LogEntry,
    showDivider: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val time = remember(entry.timestamp) { timeFormat.format(Date(entry.timestamp)) }
    val warningColor = if (isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFF9A6700)
    val messageColor = when (entry.severity()) {
        LogSeverity.ERROR -> MaterialTheme.colorScheme.error
        LogSeverity.WARNING -> warningColor
        LogSeverity.NORMAL -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                onClick {
                    onClick()
                    true
                }
                onLongClick {
                    onLongClick()
                    true
                }
            },
    ) {
        Text(
            text = "$time [${entry.source.tag}] ${entry.displayMessage}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = messageColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 3.dp, end = 8.dp, bottom = 4.dp),
        )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}
