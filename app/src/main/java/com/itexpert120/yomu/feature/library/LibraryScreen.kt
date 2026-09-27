@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.itexpert120.yomu.feature.library

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuContentMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuReadableMaxWidth
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuContentSwap
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughEnter
import com.itexpert120.yomu.core.designsystem.yomuFadeThroughExit
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import androidx.compose.foundation.lazy.items as lazyListItems

private enum class LibraryContentMode {
    Loading,
    Empty,
    EmptySearch,
    Grid,
    List,
}

private data class LibraryScrollSample(
    val itemIndex: Int,
    val itemOffset: Int,
    val inProgress: Boolean,
)

private val ResumeFabTransformOrigin = TransformOrigin(1f, 1f)
private val ResumeFabScrollThreshold = 32.dp

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
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activeColumns = if (isLandscape) state.landscapeGridColumns else state.portraitGridColumns
    var resumeCollapsed by remember { mutableStateOf(false) }
    val resumeFabScrollThresholdPx = with(LocalDensity.current) {
        ResumeFabScrollThreshold.roundToPx()
    }

    // Require deliberate travel before reversing the FAB; raw per-frame direction changes make
    // small finger corrections repeatedly restart the label animation.
    LaunchedEffect(state.viewMode, resumeFabScrollThresholdPx) {
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
        state.searchActive && state.selectableCount == 0 -> LibraryContentMode.EmptySearch
        state.viewMode != LibraryViewMode.List -> LibraryContentMode.Grid
        else -> LibraryContentMode.List
    }

    val libraryContent: @Composable (LibraryContentMode) -> Unit = { mode ->
        when (mode) {
            LibraryContentMode.Grid -> LibraryGrid(
                state = gridState,
                viewMode = state.viewMode,
                columns = activeColumns,
                groups = state.groups,
                selectedIds = state.selectedIds,
                onBookClick = onCardClick,
                onBookLongPress = onCardLongPress,
            )

            LibraryContentMode.List -> LibraryList(
                state = listState,
                groups = state.groups,
                selectedIds = state.selectedIds,
                onBookClick = onCardClick,
                onBookLongPress = onCardLongPress,
            )

            else -> Unit
        }
    }

    YomuAppSurface {
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
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AnimatedContent(
                        targetState = contentMode,
                        transitionSpec = {
                            if (initialState == LibraryContentMode.Loading) {
                                // Data resolving behind the splash should settle in place; a
                                // directional slide makes the first library frame feel late.
                                yomuFadeThroughEnter() togetherWith yomuFadeThroughExit()
                            } else {
                                val forward = when {
                                    initialState == LibraryContentMode.Grid &&
                                        targetState == LibraryContentMode.List -> false

                                    else -> true
                                }
                                yomuContentSwap(forward = forward)
                            }
                        },
                        label = "libraryContent",
                        modifier = Modifier.fillMaxSize(),
                    ) { mode ->
                        when (mode) {
                            LibraryContentMode.Loading -> LibraryLoading()
                            LibraryContentMode.Empty -> EmptyLibrary(onImport = onImport)
                            LibraryContentMode.EmptySearch -> EmptySearchResults(
                                query = state.searchQuery,
                                onClearSearch = { onSearchQueryChange("") },
                            )

                            LibraryContentMode.Grid, LibraryContentMode.List -> libraryContent(mode)
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
                    )
                }
            }

            ImportNotice(
                importing = state.isImporting,
                notice = state.importNotice,
                canRetry = state.canRetryImport,
                onRetry = onRetryImport,
                modifier = Modifier.align(Alignment.BottomCenter),
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
) {
    Box {
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
            selectionMode = state.selectionMode,
            selectedCount = state.selectedIds.size,
            allSelected = state.selectedIds.isNotEmpty() &&
                state.selectedIds.size == state.selectableCount,
            onExitSelection = onExitSelection,
            onSelectAll = onSelectAll,
            onDeselectAll = onDeselectAll,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LibraryLoading() {
    YomuLoadingState(
        message = "Loading library…",
        modifier = Modifier.align(Alignment.Center),
    )
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero moment: the mark sits on a scalloped Material shape — decorative, never text-bearing.
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(168.dp)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color(0xFF050505)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.open_reader_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Welcome to Open Reader",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "A calm, reader-first EPUB library. Import a book to begin — your themes, fonts, " +
                "reading position and stats all live here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = "EPUB files only · kept privately on your device",
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No books found",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nothing matches “$query”.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onClearSearch, shapes = ButtonDefaults.shapes()) {
            Text("Clear search")
        }
    }
}

@Composable
private fun LibraryGrid(
    state: LazyGridState,
    viewMode: LibraryViewMode,
    columns: Int,
    groups: List<LibraryGroup>,
    selectedIds: Set<String>,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    // Center the grid within a comfortable max width so covers don't stretch edge-to-edge on a
    // wide tablet/desktop; on a phone this is a no-op (screen < max width).
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthClass = YomuWidthClass.fromWidth(maxWidth)
        // In Auto mode, give covers more room to breathe as the screen widens — slightly larger
        // minimum cell on tablets means fewer, larger covers rather than a dense wall of tiny ones.
        val autoMinSize = when (widthClass) {
            YomuWidthClass.Large -> 164.dp
            YomuWidthClass.Expanded -> 150.dp
            YomuWidthClass.Medium -> 132.dp
            YomuWidthClass.Compact -> 118.dp
        }
        // Material 3 feed layout: 24dp window margins and wider gutters from medium up.
        val margin = widthClass.margin
        val gutter = if (widthClass.isWide) 16.dp else 12.dp
        LazyVerticalGrid(
            state = state,
            // Auto (0): adaptive so covers stay a comfortable size — ~3 columns on a phone, more on a
            // tablet. A positive value forces that exact column count.
            columns = if (columns <= LibraryPreferences.AUTO_COLUMNS) {
                GridCells.Adaptive(minSize = autoMinSize)
            } else {
                GridCells.Fixed(columns)
            },
            modifier = Modifier
                .widthIn(max = YomuContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = margin, end = margin, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (widthClass.isWide) 24.dp else 20.dp),
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
}

@Composable
private fun LibraryList(
    state: LazyListState,
    groups: List<LibraryGroup>,
    selectedIds: Set<String>,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    // A full-bleed list of rows reads awkwardly on a wide tablet; keep it to a single readable
    // column centered on screen. Phones are unaffected (screen < max width).
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .widthIn(max = YomuReadableMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                authorLastName = "READER",
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
