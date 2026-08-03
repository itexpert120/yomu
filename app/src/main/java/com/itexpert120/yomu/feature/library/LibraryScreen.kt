package com.itexpert120.yomu.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.R
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuMotion
import com.itexpert120.yomu.core.designsystem.YomuWidthClass
import com.itexpert120.yomu.core.designsystem.yomuChromeEnter
import com.itexpert120.yomu.core.designsystem.yomuChromeExit
import com.itexpert120.yomu.core.designsystem.yomuScrollEdgeShadow
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import androidx.compose.foundation.lazy.items as lazyListItems

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onOpenReader: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onImport: () -> Unit,
    onEnterSelection: (String) -> Unit = {},
    onToggleSelect: (String) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onInvertSelection: () -> Unit = {},
    onRemoveSelected: () -> Unit = {},
    onMarkSelectedRead: () -> Unit = {},
    onMarkSelectedUnread: () -> Unit = {},
    onRetryImport: () -> Unit = {},
) {
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val elevated = when (state.viewMode) {
        LibraryViewMode.Grid -> gridState.canScrollBackward
        LibraryViewMode.List -> listState.canScrollBackward
    }

    // Tap opens book details; long-press starts multi-select. While selecting, both toggle.
    val onCardClick: (LibraryBook) -> Unit = { book ->
        if (state.selectionMode) onToggleSelect(book.id) else onOpenDetails(book.id)
    }
    val onCardLongPress: (LibraryBook) -> Unit = { book ->
        if (state.selectionMode) onToggleSelect(book.id) else onEnterSelection(book.id)
    }

    if (state.selectionMode) {
        BackHandler(onBack = onExitSelection)
    }

    val libraryContent: @Composable () -> Unit = {
        Crossfade(
            targetState = state.viewMode,
            animationSpec = tween(YomuMotion.FadeInMillis, easing = YomuMotion.EmphasizedDecel),
            label = "libraryViewMode",
        ) { mode ->
            when (mode) {
                LibraryViewMode.Grid -> LibraryGrid(
                    state = gridState,
                    columns = state.gridColumns,
                    groups = state.groups,
                    selectedIds = state.selectedIds,
                    onBookClick = onCardClick,
                    onBookLongPress = onCardLongPress,
                )

                LibraryViewMode.List -> LibraryList(
                    state = listState,
                    groups = state.groups,
                    selectedIds = state.selectedIds,
                    onBookClick = onCardClick,
                    onBookLongPress = onCardLongPress,
                )
            }
        }
    }

    YomuAppSurface {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                LibraryTopBar(
                    searchActive = state.searchActive,
                    searchQuery = state.searchQuery,
                    onSearchToggle = onSearchToggle,
                    onSearchQueryChange = onSearchQueryChange,
                    onOptionsSheetToggle = { showOptionsSheet = true },
                    elevated = elevated,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        state.isLoading -> LibraryLoading()
                        state.totalCount == 0 -> EmptyLibrary(onImport = onImport)
                        else -> libraryContent()
                    }
                }
            }

            LibraryOptionsSheet(
                visible = showOptionsSheet,
                sortMode = state.sortMode,
                groupMode = state.groupMode,
                viewMode = state.viewMode,
                columns = state.gridColumns,
                onSortModeChange = onSortModeChange,
                onGroupModeChange = onGroupModeChange,
                onViewModeChange = onViewModeChange,
                onColumnsChange = onGridColumnsChange,
                onDismiss = { showOptionsSheet = false },
            )

            if (!state.selectionMode && state.totalCount > 0) {
                FloatingAddBookButton(
                    onImport = onImport,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            val continueReading = state.continueReading
            if (continueReading != null) {
                AnimatedVisibility(
                    visible = !state.selectionMode,
                    enter = yomuChromeEnter(),
                    exit = yomuChromeExit(),
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    FloatingResumeButton(
                        book = continueReading,
                        onResume = { onOpenReader(continueReading.id) },
                        bottomOffset = if (state.totalCount > 0) 72.dp else 0.dp,
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

            AnimatedVisibility(
                visible = state.selectionMode,
                enter = yomuChromeEnter(),
                exit = yomuChromeExit(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LibrarySelectionDock(
                    allSelected = state.selectedIds.isNotEmpty() &&
                        state.selectedIds.size == state.selectableCount,
                    onClose = onExitSelection,
                    onSelectAll = onSelectAll,
                    onDeselectAll = onDeselectAll,
                    onInvert = onInvertSelection,
                    onMarkRead = onMarkSelectedRead,
                    onMarkUnread = onMarkSelectedUnread,
                    onDelete = { showDeleteConfirm = true },
                    onOpenDetails = if (state.selectedIds.size == 1) {
                        { onOpenDetails(state.selectedIds.first()) }
                    } else {
                        null
                    },
                )
            }

            ConfirmRemoveDialog(
                visible = showDeleteConfirm,
                count = state.selectedIds.size,
                onCancel = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    onRemoveSelected()
                },
            )

            SystemBarTopScrim(Modifier.align(Alignment.TopCenter))
            SystemBarBottomScrim(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LibraryLoading() {
    Text(
        text = "Loading library…",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
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
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(MaterialTheme.shapes.large)
                .background(Color(0xFF050505))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.large,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.open_reader_logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Welcome to Open Reader",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge,
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
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import EPUB")
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
private fun LibraryGrid(
    state: LazyGridState,
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
            YomuWidthClass.Expanded -> 150.dp
            YomuWidthClass.Medium -> 132.dp
            YomuWidthClass.Compact -> 118.dp
        }
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
                .fillMaxSize()
                .yomuScrollEdgeShadow(
                    color = MaterialTheme.colorScheme.background,
                    bottom = state.canScrollForward,
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            groups.forEach { group ->
                if (group.label.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GroupSectionHeader(title = group.label, modifier = Modifier.animateItem())
                    }
                }
                items(group.books, key = { it.id }) { book ->
                    GridBookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongPress = { onBookLongPress(book) },
                        selected = book.id in selectedIds,
                        modifier = Modifier.animateItem(),
                    )
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
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .yomuScrollEdgeShadow(
                    color = MaterialTheme.colorScheme.background,
                    bottom = state.canScrollForward,
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            groups.forEach { group ->
                if (group.label.isNotEmpty()) {
                    item { GroupSectionHeader(title = group.label, modifier = Modifier.animateItem()) }
                }
                lazyListItems(group.books, key = { it.id }) { book ->
                    BookListRow(
                        book = book,
                        onClick = { onBookClick(book) },
                        onLongPress = { onBookLongPress(book) },
                        selected = book.id in selectedIds,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    YomuDesignTheme {
        LibraryScreen(
            state = LibraryUiState(
                isLoading = false,
                totalCount = 6,
                groups = listOf(LibraryGroup("", emptyList())),
            ),
            onSearchToggle = {},
            onSearchQueryChange = {},
            onSortModeChange = {},
            onGroupModeChange = {},
            onViewModeChange = {},
            onGridColumnsChange = {},
            onOpenReader = {},
            onOpenDetails = {},
            onImport = {},
        )
    }
}
