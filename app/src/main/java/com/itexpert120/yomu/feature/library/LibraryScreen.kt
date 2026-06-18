package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuAppSurface
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.ThemePreference

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    themePreference: ThemePreference = ThemePreference.System,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onGroupModeChange: (GroupMode) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onMarkRead: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBookClick: (String) -> Unit,
    onResume: (String) -> Unit,
    onImport: () -> Unit,
    onThemeToggle: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    // Ephemeral UI state stays local to the screen; everything persistent lives in the VM.
    var selectedBook by remember { mutableStateOf<LibraryBook?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showDisplaySheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val elevated = when (state.viewMode) {
        LibraryViewMode.Grid -> gridState.canScrollBackward
        LibraryViewMode.List -> listState.canScrollBackward
    }

    YomuAppSurface {
        Box(Modifier.fillMaxSize()) {
            if (!state.isLoading && state.totalCount == 0) {
                EmptyLibrary(onImport = onImport)
            } else {
                Column(Modifier.fillMaxSize()) {
                    LibraryHeader(
                        bookCount = state.totalCount,
                        searchActive = state.searchActive,
                        searchQuery = state.searchQuery,
                        sortMode = state.sortMode,
                        groupMode = state.groupMode,
                        themePreference = themePreference,
                        showSortSheet = showSortSheet,
                        showGroupSheet = showGroupSheet,
                        onSearchToggle = onSearchToggle,
                        onSearchQueryChange = onSearchQueryChange,
                        onSortModeChange = onSortModeChange,
                        onGroupModeChange = onGroupModeChange,
                        viewMode = state.viewMode,
                        onSortSheetToggle = { showSortSheet = !showSortSheet },
                        onGroupSheetToggle = { showGroupSheet = !showGroupSheet },
                        onDisplaySheetToggle = { showDisplaySheet = !showDisplaySheet },
                        onImport = onImport,
                        onThemeToggle = onThemeToggle,
                        onOpenSettings = onOpenSettings,
                        elevated = elevated,
                    )

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        // Crossfade grid<->list; within each, items animate placement on
                        // sort/group changes (see animateItem in LibraryGrid/LibraryList).
                        Crossfade(
                            targetState = state.viewMode,
                            animationSpec = tween(300),
                            label = "libraryViewMode",
                        ) { mode ->
                            when (mode) {
                                LibraryViewMode.Grid -> LibraryGrid(
                                    state = gridState,
                                    continueReading = state.continueReading,
                                    columns = state.gridColumns,
                                    groups = state.groups,
                                    onBookClick = { onBookClick(it.id) },
                                    onBookLongPress = { selectedBook = it },
                                )

                                LibraryViewMode.List -> LibraryList(
                                    state = listState,
                                    continueReading = state.continueReading,
                                    groups = state.groups,
                                    onBookClick = { onBookClick(it.id) },
                                    onBookLongPress = { selectedBook = it },
                                )
                            }
                        }
                    }
                }
            }

            LibraryDisplaySheet(
                visible = showDisplaySheet,
                viewMode = state.viewMode,
                columns = state.gridColumns,
                onViewModeChange = onViewModeChange,
                onColumnsChange = onGridColumnsChange,
                onDismiss = { showDisplaySheet = false },
            )

            AnimatedVisibility(
                visible = selectedBook != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                selectedBook?.let { book ->
                    BookContextPanel(
                        book = book,
                        onDismiss = { selectedBook = null },
                        onMarkRead = {
                            onMarkRead(book.id)
                            selectedBook = null
                        },
                        onRemove = {
                            onRemove(book.id)
                            selectedBook = null
                        },
                    )
                }
            }

            val continueReading = state.continueReading
            if (continueReading != null && selectedBook == null) {
                FloatingResumeButton(
                    book = continueReading,
                    onResume = { onResume(continueReading.id) },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            SystemBarTopScrim(Modifier.align(Alignment.TopCenter))
            SystemBarBottomScrim(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ImportEmptyCard(onImport = onImport)
    }
}

@Composable
private fun LibraryGrid(
    state: LazyGridState,
    continueReading: LibraryBook?,
    columns: Int,
    groups: List<LibraryGroup>,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (continueReading != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingCard(book = continueReading, modifier = Modifier.animateItem())
            }
        }

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
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun LibraryList(
    state: LazyListState,
    continueReading: LibraryBook?,
    groups: List<LibraryGroup>,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (continueReading != null) {
            item { ContinueReadingCard(book = continueReading, modifier = Modifier.animateItem()) }
        }

        groups.forEach { group ->
            if (group.label.isNotEmpty()) {
                item { GroupSectionHeader(title = group.label, modifier = Modifier.animateItem()) }
            }
            lazyListItems(group.books, key = { it.id }) { book ->
                BookListRow(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongPress = { onBookLongPress(book) },
                    modifier = Modifier.animateItem(),
                )
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
            onMarkRead = {},
            onRemove = {},
            onBookClick = {},
            onResume = {},
            onImport = {},
            onThemeToggle = {},
        )
    }
}
