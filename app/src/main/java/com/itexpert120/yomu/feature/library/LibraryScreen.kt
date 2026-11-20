@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.itexpert120.yomu.feature.library

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuBadgeShape
import com.itexpert120.yomu.core.designsystem.YomuBadgeTone
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuContentMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuShapeBadge
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.feature.collections.ManageTabsSheet
import com.itexpert120.yomu.feature.collections.OrganizeSheet
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items as lazyListItems

private enum class LibraryContentMode {
    Loading,
    Empty,
    Shelves,
}

private data class LibraryScrollSample(
    val itemIndex: Int,
    val itemOffset: Int,
    val inProgress: Boolean,
)

private val ResumeFabTransformOrigin = TransformOrigin(1f, 1f)
private val ResumeFabScrollThreshold = 32.dp

// Bottom content padding that lets the last row scroll clear of the floating bottom chrome: the
// selection toolbar / Resume button (up to 64dp) plus its 16dp margin and a little breathing room.
private val LibraryBottomChromeClearance = 104.dp

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onPortraitGridColumnsChange: (Int) -> Unit,
    onLandscapeGridColumnsChange: (Int) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onImport: () -> Unit,
    onEnterSelection: (String) -> Unit = {},
    onToggleSelect: (String) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onRemoveSelected: () -> Unit = {},
    onMarkSelectedRead: () -> Unit = {},
    onMarkSelectedUnread: () -> Unit = {},
    onRetryImport: () -> Unit = {},
    onSelectTab: (String) -> Unit = {},
    onCreateCollection: (name: String, addSelected: Boolean) -> Unit = { _, _ -> },
    onRenameCollection: (id: String, name: String) -> Unit = { _, _ -> },
    onDeleteCollection: (String) -> Unit = {},
    onMoveTab: (key: String, up: Boolean) -> Unit = { _, _ -> },
    onSetTabHidden: (key: String, hidden: Boolean) -> Unit = { _, _ -> },
    onSetSelectedInCollection: (id: String, member: Boolean) -> Unit = { _, _ -> },
    onSetSelectedStatus: (ManualStatus?) -> Unit = {},
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOrganize by remember { mutableStateOf(false) }
    var showManageCollections by remember { mutableStateOf(false) }
    // One scroll position per tab, so swiping between tabs doesn't reset where you were.
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val gridState = gridStates.getOrPut(state.selectedTab) { LazyGridState() }
    val listState = listStates.getOrPut(state.selectedTab) { LazyListState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activeColumns = if (isLandscape) state.landscapeGridColumns else state.portraitGridColumns
    var resumeCollapsed by remember { mutableStateOf(false) }
    val resumeFabScrollThresholdPx = with(LocalDensity.current) {
        ResumeFabScrollThreshold.roundToPx()
    }

    // Require deliberate travel before reversing the FAB; raw per-frame direction changes make
    // small finger corrections repeatedly restart the label animation.
    LaunchedEffect(state.viewMode, resumeFabScrollThresholdPx, gridState, listState) {
        resumeCollapsed = false
        val positions = when (state.viewMode) {
            LibraryViewMode.ComfortableGrid,
            LibraryViewMode.CompactGrid,
            LibraryViewMode.CoverOnlyGrid,
            -> snapshotFlow {
                LibraryScrollSample(
                    itemIndex = gridState.firstVisibleItemIndex,
                    itemOffset = gridState.firstVisibleItemScrollOffset,
                    inProgress = gridState.isScrollInProgress,
                )
            }

            LibraryViewMode.List -> snapshotFlow {
                LibraryScrollSample(
                    itemIndex = listState.firstVisibleItemIndex,
                    itemOffset = listState.firstVisibleItemScrollOffset,
                    inProgress = listState.isScrollInProgress,
                )
            }
        }
        var previous: LibraryScrollSample? = null
        var accumulatedDelta = 0
        positions.collect { current ->
            if (!current.inProgress) {
                previous = current
                accumulatedDelta = 0
                return@collect
            }

            previous?.let { before ->
                val delta = when {
                    current.itemIndex > before.itemIndex -> resumeFabScrollThresholdPx
                    current.itemIndex < before.itemIndex -> -resumeFabScrollThresholdPx
                    else -> current.itemOffset - before.itemOffset
                }
                val directionChanged =
                    (accumulatedDelta > 0 && delta < 0) ||
                        (accumulatedDelta < 0 && delta > 0)
                if (directionChanged) accumulatedDelta = 0
                accumulatedDelta += delta
                when {
                    accumulatedDelta >= resumeFabScrollThresholdPx -> {
                        resumeCollapsed = true
                        accumulatedDelta = 0
                    }

                    accumulatedDelta <= -resumeFabScrollThresholdPx -> {
                        resumeCollapsed = false
                        accumulatedDelta = 0
                    }
                }
            }
            previous = current
        }
    }

    // Scrolling the results means the user is done typing: drop the keyboard so it doesn't cover them.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(gridState, listState) {
        snapshotFlow { gridState.isScrollInProgress || listState.isScrollInProgress }
            .collect { scrolling -> if (scrolling) focusManager.clearFocus() }
    }

    // Tap opens book details; long-press starts multi-select. While selecting, both toggle.
    val onCardClick: (LibraryBook) -> Unit = { book ->
        if (state.selectionMode) onToggleSelect(book.id) else onOpenDetails(book.id)
    }
    val onCardLongPress: (LibraryBook) -> Unit = { book ->
        if (state.selectionMode) onToggleSelect(book.id) else onEnterSelection(book.id)
    }

    // Local library states have priority over route navigation. This is a regular back callback;
    // it deliberately does not expose gesture progress.
    BackHandler(enabled = state.selectionMode || state.searchActive) {
        if (state.selectionMode) {
            onExitSelection()
        } else if (state.searchActive) {
            onSearchToggle()
        }
    }

    val contentMode = when {
        state.isLoading -> LibraryContentMode.Loading
        state.totalCount == 0 -> LibraryContentMode.Empty
        else -> LibraryContentMode.Shelves
    }
    // Created once the shelves exist, so it starts on the remembered tab instead of reporting
    // page 0 as a fresh selection.
    val selectedIndex = state.tabs.indexOfFirst { it.key == state.selectedTab }.coerceAtLeast(0)
    val pagerState = key(contentMode == LibraryContentMode.Shelves) {
        rememberPagerState(initialPage = selectedIndex) { state.tabs.size }
    }
    val pagerScope = rememberCoroutineScope()

    YomuAppSurface {
        // One width class from the measured width (a navigation rail beside the library is already
        // accounted for) drives the header, search row, grid and list, so their edges line up.
        val widthClass = YomuWidthClass.fromWidth(maxWidth)
        val margin = widthClass.margin
        // Capped content is centred: its leading edge is the window margin until the cap bites.
        val gridInset = ((maxWidth - YomuContentMaxWidth) / 2).coerceAtLeast(margin)
        val listInset = ((maxWidth - YomuReadableMaxWidth) / 2).coerceAtLeast(margin)
        val headerInset = if (state.viewMode == LibraryViewMode.List) listInset else gridInset
        val bottomChromeVisible = state.continueReading != null || state.selectionMode
        val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
        // The last row must scroll clear of the Resume button / selection toolbar.
        val contentBottom = navBottom +
            if (bottomChromeVisible) LibraryBottomChromeClearance else YomuTheme.space.lg
        val contentTop = YomuTheme.space.md

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
                LibraryTopBarTransition(
                    state = state,
                    onSearchToggle = onSearchToggle,
                    onSearchQueryChange = onSearchQueryChange,
                    onImport = onImport,
                    onOptionsSheetToggle = { showOptionsSheet = true },
                    onExitSelection = onExitSelection,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    scrollBehavior = scrollBehavior,
                    contentInset = headerInset,
                )
                if (contentMode == LibraryContentMode.Shelves) {
                    LibraryTabRow(
                        tabs = state.tabs,
                        pagerState = pagerState,
                        onSelect = { index -> pagerScope.launch { pagerState.animateScrollToPage(index) } },
                        onEditTabs = { showManageCollections = true },
                        horizontalInset = headerInset - YomuTheme.space.xxs,
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AnimatedContent(
                        targetState = contentMode,
                        transitionSpec = {
                            // Data resolving behind the splash should settle in place; a
                            // directional slide makes the first library frame feel late.
                            yomuFadeThroughEnter() togetherWith yomuFadeThroughExit()
                        },
                        label = "libraryContent",
                        modifier = Modifier.fillMaxSize(),
                    ) { mode ->
                        when (mode) {
                            LibraryContentMode.Loading -> LibraryLoading()
                            LibraryContentMode.Empty -> EmptyLibrary(onImport = onImport)
                            LibraryContentMode.Shelves -> LibraryShelfPager(
                                state = state,
                                pagerState = pagerState,
                                onSelectTab = onSelectTab,
                                onClearSearch = { onSearchQueryChange("") },
                            ) { page ->
                                if (state.viewMode != LibraryViewMode.List) {
                                    LibraryGrid(
                                        state = gridStates.getOrPut(page.key) { LazyGridState() },
                                        viewMode = state.viewMode,
                                        columns = activeColumns,
                                        widthClass = widthClass,
                                        contentPadding = PaddingValues(
                                            start = gridInset,
                                            end = gridInset,
                                            top = contentTop,
                                            bottom = contentBottom,
                                        ),
                                        groups = page.groups,
                                        selectedIds = state.selectedIds,
                                        onBookClick = onCardClick,
                                        onBookLongPress = onCardLongPress,
                                    )
                                } else {
                                    LibraryList(
                                        state = listStates.getOrPut(page.key) { LazyListState() },
                                        contentPadding = PaddingValues(
                                            start = listInset,
                                            end = listInset,
                                            top = contentTop,
                                            bottom = contentBottom,
                                        ),
                                        groups = page.groups,
                                        selectedIds = state.selectedIds,
                                        onBookClick = onCardClick,
                                        onBookLongPress = onCardLongPress,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibraryOptionsSheet(
                visible = showOptionsSheet,
                sortMode = state.sortMode,
                groupMode = state.groupMode,
                viewMode = state.viewMode,
                portraitColumns = state.portraitGridColumns,
                landscapeColumns = state.landscapeGridColumns,
                onSortModeChange = onSortModeChange,
                onGroupModeChange = onGroupModeChange,
                onViewModeChange = onViewModeChange,
                onPortraitColumnsChange = onPortraitGridColumnsChange,
                onLandscapeColumnsChange = onLandscapeGridColumnsChange,
                onDismiss = { showOptionsSheet = false },
            )

            AnimatedVisibility(
                visible = state.selectionMode,
                enter = yomuChromeEnter(),
                exit = yomuChromeExit(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LibrarySelectionToolbar(
                    onOrganize = { showOrganize = true },
                    onMarkRead = onMarkSelectedRead,
                    onMarkUnread = onMarkSelectedUnread,
                    onDelete = { showDeleteConfirm = true },
                )
            }

            val continueReading = state.continueReading
            AnimatedVisibility(
                visible = continueReading != null && !state.selectionMode,
                enter = yomuPopupEnter(ResumeFabTransformOrigin),
                exit = yomuPopupExit(ResumeFabTransformOrigin),
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                continueReading?.let { book ->
                    FloatingResumeButton(
                        book = book,
                        collapsed = resumeCollapsed,
                        onResume = { onOpenReader(book.id) },
                        endMargin = margin,
                    )
                }
            }

            ImportNotice(
                importing = state.isImporting,
                notice = state.importNotice,
                canRetry = state.canRetryImport,
                onRetry = onRetryImport,
                horizontalMargin = margin,
                aboveBottomChrome = bottomChromeVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            OrganizeSheet(
                visible = showOrganize && state.selectionMode,
                title = if (state.selectedIds.size == 1) "Organize book" else "Organize ${state.selectedIds.size} books",
                status = state.organizeStatus,
                collections = state.organizeCollections,
                onSetStatus = onSetSelectedStatus,
                onSetCollection = onSetSelectedInCollection,
                onCreateCollection = { onCreateCollection(it, true) },
                onDismiss = { showOrganize = false },
            )

            ManageTabsSheet(
                visible = showManageCollections,
                tabs = state.managedTabs,
                onCreate = { onCreateCollection(it, false) },
                onRename = onRenameCollection,
                onDelete = onDeleteCollection,
                onMove = onMoveTab,
                onSetHidden = onSetTabHidden,
                onDismiss = { showManageCollections = false },
            )

            ConfirmRemoveDialog(
                visible = showDeleteConfirm,
                count = state.selectedIds.size,
                onCancel = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    onRemoveSelected()
                },
            )
        }
    }
}

@Composable
private fun LibraryTopBarTransition(
    state: LibraryUiState,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOptionsSheetToggle: () -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    contentInset: Dp,
) {
    LibraryTopBar(
        searchActive = state.searchActive,
        searchQuery = state.searchQuery,
        onSearchToggle = onSearchToggle,
        onSearchQueryChange = onSearchQueryChange,
        onImport = onImport,
        onOptionsSheetToggle = onOptionsSheetToggle,
        scrollBehavior = scrollBehavior,
        showImport = state.totalCount > 0,
        resultCount = state.selectableCount,
        bookCount = state.totalCount,
        contentInset = contentInset,
        selectionMode = state.selectionMode,
        selectedCount = state.selectedIds.size,
        allSelected = state.selectedIds.isNotEmpty() &&
            state.selectedIds.size == state.selectableCount,
        onExitSelection = onExitSelection,
        onSelectAll = onSelectAll,
        onDeselectAll = onDeselectAll,
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LibraryLoading() {
    YomuLoadingState(
        message = "Loading library…",
        modifier = Modifier.align(Alignment.Center),
    )
}

/** Whole-pane states stay centred, and scroll when a landscape phone can't fit them. */
@Composable
private fun CenteredStatePane(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = YomuTheme.space.lg, vertical = YomuTheme.space.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    CenteredStatePane {
        // Hero moment: the Yomu mark on the primary badge shape — decorative, never text-bearing.
        YomuShapeBadge(
            icon = null,
            size = 168.dp,
            shape = YomuBadgeShape.Cookie,
            tone = YomuBadgeTone.Primary,
        ) {
            Icon(
                painter = painterResource(R.drawable.open_reader_logo_monochrome),
                contentDescription = null,
                modifier = Modifier.size(88.dp),
            )
        }
        Spacer(Modifier.height(YomuTheme.space.lg))
        Text(
            text = "Welcome to Open Reader",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(YomuTheme.space.xs))
        Text(
            text = "A calm, reader-first EPUB library. Import a book to begin — your themes, fonts, " +
                "reading position and stats all live here.",
            modifier = Modifier.widthIn(max = 360.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(YomuTheme.space.lg))
        val buttonHeight = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = onImport,
            shapes = ButtonDefaults.shapesFor(buttonHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            modifier = Modifier.heightIn(min = buttonHeight),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
            )
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
            Text("Import EPUB", style = ButtonDefaults.textStyleFor(buttonHeight))
        }
        Spacer(Modifier.height(YomuTheme.space.sm))
        Text(
            text = "EPUB files only · kept privately on your device",
            modifier = Modifier.widthIn(max = 360.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptySearchResults(
    query: String,
    onClearSearch: () -> Unit,
) {
    CenteredStatePane {
        YomuEmptyState(
            icon = Icons.Rounded.SearchOff,
            title = "No books found",
            body = "Nothing matches “$query”.",
            actions = {
                YomuButton(
                    text = "Clear search",
                    onClick = onClearSearch,
                    emphasis = YomuButtonEmphasis.Secondary,
                )
            },
        )
    }
}

/**
 * One page per library tab, swipeable. The pager and the tab row stay in step: swiping settles on
 * a tab, and choosing a tab scrolls the pager. Each page owns its empty and no-results state.
 */
@Composable
private fun LibraryShelfPager(
    state: LibraryUiState,
    pagerState: PagerState,
    onSelectTab: (String) -> Unit,
    onClearSearch: () -> Unit,
    pageContent: @Composable (LibraryPage) -> Unit,
) {
    val tabs = state.tabs
    val selectedIndex = tabs.indexOfFirst { it.key == state.selectedTab }.coerceAtLeast(0)
    val currentTabs by rememberUpdatedState(tabs)
    val currentOnSelect by rememberUpdatedState(onSelectTab)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentTabs.getOrNull(page)?.let { currentOnSelect(it.key) }
        }
    }
    // Only for selections that didn't come from the pager, e.g. the open collection was deleted.
    LaunchedEffect(selectedIndex) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }
    HorizontalPager(
        state = pagerState,
        key = { tabs.getOrNull(it)?.key ?: it },
        // Exposed to UiAutomator for the macrobenchmarks / Baseline Profile generator.
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag("library-pager"),
    ) { index ->
        val page = state.pages.getOrNull(index) ?: return@HorizontalPager
        val tab = tabs[index]
        when {
            page.bookCount > 0 -> pageContent(page)
            state.searchActive && state.searchQuery.isNotBlank() -> EmptySearchResults(
                query = state.searchQuery,
                onClearSearch = onClearSearch,
            )
            else -> EmptyShelf(tab)
        }
    }
}

@Composable
private fun EmptyShelf(tab: LibraryTab) {
    val (title, body) = when (tab.key) {
        LibraryTabKeys.READING -> "Nothing in progress" to "Books you've started reading show up here."
        LibraryTabKeys.TO_READ -> "Nothing waiting" to "Books you haven't opened yet line up here."
        LibraryTabKeys.FINISHED -> "No finished books yet" to "Books you finish move here."
        LibraryTabKeys.ON_HOLD -> "Nothing on hold" to "Set a book to On hold from Organize to park it here."
        LibraryTabKeys.DROPPED -> "Nothing dropped" to "Books you set to Dropped are kept here."
        else -> "“${tab.label}” is empty" to "Long-press books in the library, then tap Organize to add them."
    }
    CenteredStatePane {
        YomuEmptyState(
            icon = if (tab.isCollection) Icons.Rounded.CollectionsBookmark else Icons.Rounded.AutoStories,
            title = title,
            body = body,
            tone = YomuBadgeTone.Neutral,
        )
    }
}

@Composable
private fun LibraryGrid(
    state: LazyGridState,
    viewMode: LibraryViewMode,
    columns: Int,
    widthClass: YomuWidthClass,
    contentPadding: PaddingValues,
    groups: List<LibraryGroup>,
    selectedIds: Set<String>,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    // In Auto mode, give covers more room to breathe as the screen widens — slightly larger
    // minimum cell on tablets means fewer, larger covers rather than a dense wall of tiny ones.
    // Compact is sized so 360–411dp phones (328–379dp between margins, 12dp gutters) fit 3 columns.
    val autoMinSize = when (widthClass) {
        YomuWidthClass.Large -> 164.dp
        YomuWidthClass.Expanded -> 150.dp
        YomuWidthClass.Medium -> 132.dp
        YomuWidthClass.Compact -> 100.dp
    }
    // Material 3 feed layout: wider gutters from medium up.
    val gutter = if (widthClass.isWide) YomuTheme.space.md else YomuTheme.space.sm
    // The grid spans the pane (so it scrolls from anywhere) and the horizontal content padding
    // centres the columns within the content cap on wide tablets/desktops; a no-op on phones.
    LazyVerticalGrid(
        state = state,
        // Auto (0): adaptive so covers stay a comfortable size — 3 columns on a phone, more on a
        // tablet. A positive value forces that exact column count.
        columns = if (columns <= LibraryPreferences.AUTO_COLUMNS) {
            GridCells.Adaptive(minSize = autoMinSize)
        } else {
            GridCells.Fixed(columns)
        },
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(if (widthClass.isWide) YomuTheme.space.lg else YomuTheme.space.md),
        horizontalArrangement = Arrangement.spacedBy(gutter),
    ) {
        groups.forEach { group ->
            if (group.label.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GroupSectionHeader(title = group.label)
                }
            }
            items(group.books, key = { it.id }) { book ->
                // Re-sorting, grouping, or filtering slides covers to their new slots.
                val itemModifier = Modifier.animateItem(
                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                )
                when (viewMode) {
                    LibraryViewMode.ComfortableGrid -> GridBookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongPress = { onBookLongPress(book) },
                        selected = book.id in selectedIds,
                        modifier = itemModifier,
                    )

                    LibraryViewMode.CompactGrid -> CompactGridBookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongPress = { onBookLongPress(book) },
                        selected = book.id in selectedIds,
                        modifier = itemModifier,
                    )

                    LibraryViewMode.CoverOnlyGrid -> CoverOnlyGridBookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongPress = { onBookLongPress(book) },
                        selected = book.id in selectedIds,
                        modifier = itemModifier,
                    )

                    LibraryViewMode.List -> Unit
                }
            }
        }
    }
}

@Composable
private fun LibraryList(
    state: LazyListState,
    contentPadding: PaddingValues,
    groups: List<LibraryGroup>,
    selectedIds: Set<String>,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    // A full-bleed list of rows reads awkwardly on a wide tablet; the horizontal content padding
    // keeps it to a single centred, readable column. Phones are unaffected (screen < max width).
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
    ) {
        groups.forEach { group ->
            if (group.label.isNotEmpty()) {
                item { GroupSectionHeader(title = group.label) }
            }
            lazyListItems(group.books, key = { it.id }) { book ->
                BookListRow(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongPress = { onBookLongPress(book) },
                    selected = book.id in selectedIds,
                    modifier = Modifier.animateItem(
                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ),
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun LibraryPhonePreview() {
    LibraryPreview(mode = LibraryViewMode.ComfortableGrid)
}

@Preview(widthDp = 600, heightDp = 900, showBackground = true)
@Composable
private fun LibraryRailPreview() {
    LibraryPreview(mode = LibraryViewMode.CompactGrid)
}

@Preview(widthDp = 720, heightDp = 900, showBackground = true)
@Composable
private fun LibraryTwoPanePreview() {
    LibraryPreview(mode = LibraryViewMode.CoverOnlyGrid)
}

@Preview(widthDp = 1280, heightDp = 900, showBackground = true)
@Composable
private fun LibraryWidePreview() {
    LibraryPreview(mode = LibraryViewMode.List)
}

@Composable
private fun LibraryPreview(mode: LibraryViewMode) {
    YomuDesignTheme {
        LibraryScreen(
            state = LibraryUiState(
                isLoading = false,
                totalCount = 6,
                selectableCount = 6,
                viewMode = mode,
                groups = listOf(
                    LibraryGroup(
                        "",
                        List(6) { index ->
                            LibraryBook(
                                id = index.toString(),
                                title = listOf(
                                    "The Wind in the Willows",
                                    "A Long Way Home",
                                    "The Quiet Archive",
                                    "Letters from the Coast",
                                    "The Glass Garden",
                                    "Small Things, Brightly",
                                )[index],
                                shortTitle = "Preview book",
                                author = "Open Reader",
                                authorLastName = "PRESS",
                                progress = (index + 1) / 8f,
                                remaining = "12 min",
                                coverImagePath = null,
                                coverColors = listOf(
                                    Color(0xFF284B63),
                                    Color(0xFF9B5DE5),
                                ),
                                lastOpenedAt = 0L,
                                series = null,
                            )
                        },
                    ),
                ),
            ),
            onSearchToggle = {},
            onSearchQueryChange = {},
            onSortModeChange = {},
            onGroupModeChange = {},
            onViewModeChange = {},
            onPortraitGridColumnsChange = {},
            onLandscapeGridColumnsChange = {},
            onOpenReader = {},
            onOpenDetails = {},
            onImport = {},
        )
    }
}
