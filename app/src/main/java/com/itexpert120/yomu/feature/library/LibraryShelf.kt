package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.itexpert120.yomu.core.designsystem.yomuScrollEdgeRule
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.lazy.items as lazyListItems

/**
 * One page per library tab, swipeable. The pager and the tab row stay in step: swiping settles on
 * a tab, and choosing a tab scrolls the pager. Each page owns its empty and no-results state.
 */
@Composable
internal fun LibraryTabPager(
    state: LibraryUiState,
    pagerState: PagerState,
    onSelectTab: (String) -> Unit,
    onClearSearch: () -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (LibraryPage) -> Unit,
) {
    val tabs = state.tabs
    val selectedIndex = tabs.indexOfFirst { it.key == state.selectedTab }.coerceAtLeast(0)
    val currentTabs by rememberUpdatedState(tabs)
    val currentOnSelect by rememberUpdatedState(onSelectTab)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pagerState) {
        // The first value is wherever the pager already rests (possibly stale if the tab changed
        // while it wasn't on screen, e.g. from search); only real settles select a tab.
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            currentTabs.getOrNull(page)?.let {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                currentOnSelect(it.key)
            }
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
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag("library-pager"),
    ) { index ->
        val page = state.pages.getOrNull(index) ?: return@HorizontalPager
        val tab = tabs[index]
        when {
            page.bookCount > 0 -> pageContent(page)
            state.searchActive && state.searchQuery.isNotBlank() -> NoSearchResults(
                query = state.searchQuery,
                tabLabel = null,
                onShowAll = null,
                onClear = onClearSearch,
            )
            else -> EmptyTab(tab = tab, onShowAll = onShowAll)
        }
    }
}

@Composable
internal fun LibraryGrid(
    state: LazyGridState,
    viewMode: LibraryViewMode,
    columns: Int,
    spec: GridSpec,
    contentPadding: PaddingValues,
    books: List<LibraryBook>,
    selectedIds: Set<String>,
    selecting: Boolean,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    // The grid spans the pane (so it scrolls from anywhere) and the horizontal content padding
    // centres the columns within the content cap on wide tablets/desktops; a no-op on phones.
    LazyVerticalGrid(
        state = state,
        // Automatic (0): adaptive, so covers stay a comfortable size — 3 columns on a phone, 4–6
        // on a tablet. A positive value forces that exact column count.
        columns = if (columns <= LibraryPreferences.AUTO_COLUMNS) {
            GridCells.Adaptive(minSize = spec.autoMinSize)
        } else {
            GridCells.Fixed(columns)
        },
        // Covers dissolve under the pinned tab row rather than cut off at its rule.
        modifier = Modifier
            .fillMaxSize()
            .yomuScrollEdgeRule(top = state.canScrollBackward),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spec.rowGap),
        horizontalArrangement = Arrangement.spacedBy(spec.columnGap),
    ) {
        items(books, key = { it.id }, contentType = { viewMode }) { book ->
            // Re-sorting or filtering slides covers to their new slots.
            val itemModifier = Modifier.animateItem(
                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            )
            val selected = book.id in selectedIds
            when (viewMode) {
                LibraryViewMode.CompactGrid -> CompactGridBookCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongPress = { onBookLongPress(book) },
                    selected = selected,
                    selecting = selecting,
                    modifier = itemModifier,
                )

                LibraryViewMode.CoverOnlyGrid -> CoverOnlyGridBookCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongPress = { onBookLongPress(book) },
                    selected = selected,
                    selecting = selecting,
                    modifier = itemModifier,
                )

                else -> GridBookCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    onLongPress = { onBookLongPress(book) },
                    selected = selected,
                    selecting = selecting,
                    modifier = itemModifier,
                )
            }
        }
    }
}

@Composable
internal fun LibraryList(
    state: LazyListState,
    contentPadding: PaddingValues,
    books: List<LibraryBook>,
    selectedIds: Set<String>,
    selecting: Boolean,
    onBookClick: (LibraryBook) -> Unit,
    onBookLongPress: (LibraryBook) -> Unit,
) {
    // A full-bleed list of rows reads awkwardly on a wide tablet; the horizontal content padding
    // keeps it to a single centred, readable column. Phones are unaffected (screen < max width).
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .yomuScrollEdgeRule(top = state.canScrollBackward),
        contentPadding = contentPadding,
    ) {
        lazyListItems(books, key = { it.id }, contentType = { LibraryBookContentType }) { book ->
            BookListRow(
                book = book,
                onClick = { onBookClick(book) },
                onLongPress = { onBookLongPress(book) },
                selected = book.id in selectedIds,
                selecting = selecting,
                divider = book !== books.last(),
                modifier = Modifier.animateItem(
                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                ),
            )
        }
    }
}

private const val LibraryBookContentType = "book"
