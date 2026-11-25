package com.itexpert120.yomu.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.itexpert120.yomu.core.designsystem.YomuDesignTheme
import com.itexpert120.yomu.core.designsystem.YomuThemeMode
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ShelfStatus
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.data.collections.LibraryTab
import com.itexpert120.yomu.data.collections.LibraryTabKeys

private val PreviewTitles = listOf(
    "The Wind in the Willows" to "Kenneth Grahame",
    "Frankenstein" to "Mary Shelley",
    "Pride and Prejudice" to "Jane Austen",
    "Mrs. Dalloway" to "Virginia Woolf",
    "The Great Gatsby" to "F. Scott Fitzgerald",
    "Moby Dick" to "Herman Melville",
    "The Hound of the Baskervilles" to "Arthur Conan Doyle",
    "The Age of Innocence" to "Edith Wharton",
    "Anna Karenina" to "Leo Tolstoy",
)
private val PreviewProgress = listOf(0.42f, 0.71f, 0.08f, 1f, 0f, 0f, 1f, 0f, 0.03f)
private val PreviewColors = listOf(
    Color(0xFF2F4A3A), Color(0xFF5A2E2A), Color(0xFF8A6A3A), Color(0xFF2C3E5C), Color(0xFF1F5E63),
    Color(0xFF3B3A55), Color(0xFF6B3F2A), Color(0xFF7A5C6E), Color(0xFF41553A),
)

private val PreviewBooks = PreviewTitles.mapIndexed { index, (title, author) ->
    LibraryBook(
        id = index.toString(),
        title = title,
        shortTitle = title,
        author = author,
        authorLastName = author.substringAfterLast(' ').uppercase(),
        progress = PreviewProgress[index],
        coverImagePath = null,
        coverColors = listOf(PreviewColors[index]),
        lastOpenedAt = if (PreviewProgress[index] in 0.01f..0.99f) 10L - index else 0L,
        series = null,
    )
}

private val PreviewTabs = listOf(
    LibraryTab(LibraryTabKeys.ALL, "All", 9),
    LibraryTab(LibraryTabKeys.READING, "Reading", 4, status = ShelfStatus.Reading),
    LibraryTab(LibraryTabKeys.TO_READ, "To read", 3, status = ShelfStatus.ToRead),
    LibraryTab(LibraryTabKeys.FINISHED, "Finished", 0, status = ShelfStatus.Finished),
    LibraryTab(LibraryTabKeys.collection("c1"), "Classics", 2, isCollection = true),
)

private fun previewState(
    viewMode: LibraryViewMode = LibraryViewMode.ComfortableGrid,
    selectedTab: String = LibraryTabKeys.ALL,
    selecting: Boolean = false,
    searchQuery: String? = null,
    empty: Boolean = false,
    loading: Boolean = false,
): LibraryUiState {
    val query = searchQuery.orEmpty()
    val matching = PreviewBooks.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    val pages = PreviewTabs.map { tab ->
        LibraryPage(
            tab.key,
            when (tab.key) {
                LibraryTabKeys.ALL -> matching
                LibraryTabKeys.READING -> matching.filter { it.progress in 0.01f..0.99f }
                LibraryTabKeys.TO_READ -> matching.filter { it.progress == 0f }
                LibraryTabKeys.FINISHED -> emptyList()
                else -> matching.take(2)
            },
        )
    }
    val current = pages.first { it.key == selectedTab }
    return LibraryUiState(
        isLoading = loading,
        totalCount = if (empty) 0 else PreviewBooks.size,
        selectableCount = current.bookCount,
        pile = if (searchQuery == null && !empty) PreviewBooks.filter { it.progress in 0.01f..0.99f }.take(3) else emptyList(),
        pileChapters = mapOf("0" to "Chapter VII", "1" to "Chapter 17"),
        books = current.books,
        viewMode = viewMode,
        searchActive = searchQuery != null,
        searchQuery = query,
        recentSearches = listOf("grahame", "dalloway", "the river bank"),
        selectionMode = selecting,
        selectedIds = if (selecting) setOf("0", "3", "5") else emptySet(),
        tabs = PreviewTabs,
        selectedTab = selectedTab,
        pages = pages,
        allTabs = PreviewTabs,
    )
}

@Composable
private fun LibraryPreview(state: LibraryUiState, mode: YomuThemeMode = YomuThemeMode.Light, pinned: Boolean = false) {
    YomuDesignTheme(themeMode = mode) {
        LibraryScreen(
            state = state,
            onSearchToggle = {},
            onSearchQueryChange = {},
            onSortModeChange = {},
            onViewModeChange = {},
            onPortraitGridColumnsChange = {},
            onLandscapeGridColumnsChange = {},
            onOpenReader = {},
            onOpenDetails = {},
            onImport = {},
            onHaveSyncWords = {},
            startPinned = pinned,
        )
    }
}

@Preview(name = "Library", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryPhonePreview() = LibraryPreview(previewState())

@Preview(name = "Library dark", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryPhoneDarkPreview() = LibraryPreview(previewState(), YomuThemeMode.Dark)

@Preview(name = "Library scrolled", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryScrolledPreview() = LibraryPreview(previewState(), pinned = true)

@Preview(name = "Library scrolled dark", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryScrolledDarkPreview() = LibraryPreview(previewState(), YomuThemeMode.Dark, pinned = true)

@Preview(name = "Pile of one", widthDp = 390, heightDp = 520)
@Composable
private fun LibraryPileOnePreview() = LibraryPreview(previewState().let { it.copy(pile = it.pile.take(1)) })

@Preview(name = "Pile of two dark", widthDp = 390, heightDp = 520)
@Composable
private fun LibraryPileTwoDarkPreview() = LibraryPreview(previewState().let { it.copy(pile = it.pile.take(2)) }, YomuThemeMode.Dark)

@Preview(name = "Library compact grid", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryCompactPreview() = LibraryPreview(previewState(viewMode = LibraryViewMode.CompactGrid))

@Preview(name = "Library covers only dark", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryCoversPreview() = LibraryPreview(previewState(viewMode = LibraryViewMode.CoverOnlyGrid), YomuThemeMode.Dark)

@Preview(name = "Library list", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryListPreview() = LibraryPreview(previewState(viewMode = LibraryViewMode.List))

@Preview(name = "Selection", widthDp = 390, heightDp = 844)
@Composable
private fun LibrarySelectionPreview() = LibraryPreview(previewState(selecting = true))

@Preview(name = "Selection dark", widthDp = 390, heightDp = 844)
@Composable
private fun LibrarySelectionDarkPreview() = LibraryPreview(previewState(selecting = true), YomuThemeMode.Dark)

@Preview(name = "Search recent", widthDp = 390, heightDp = 844)
@Composable
private fun LibrarySearchRecentPreview() = LibraryPreview(previewState(searchQuery = ""))

@Preview(name = "Search results dark", widthDp = 390, heightDp = 844)
@Composable
private fun LibrarySearchResultsPreview() = LibraryPreview(previewState(searchQuery = "the"), YomuThemeMode.Dark)

@Preview(name = "Search none", widthDp = 390, heightDp = 844)
@Composable
private fun LibrarySearchNonePreview() = LibraryPreview(previewState(searchQuery = "lighthouse", selectedTab = LibraryTabKeys.TO_READ))

@Preview(name = "Empty shelf", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryEmptyShelfPreview() = LibraryPreview(previewState(selectedTab = LibraryTabKeys.FINISHED))

@Preview(name = "Empty library", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryEmptyPreview() = LibraryPreview(previewState(empty = true))

@Preview(name = "Empty library black", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryEmptyBlackPreview() = LibraryPreview(previewState(empty = true), YomuThemeMode.Oled)

@Preview(name = "Loading", widthDp = 390, heightDp = 844)
@Composable
private fun LibraryLoadingPreview() = LibraryPreview(previewState(loading = true))

@Preview(name = "Landscape phone", widthDp = 844, heightDp = 390)
@Composable
private fun LibraryLandscapePreview() = LibraryPreview(previewState())

@Preview(name = "Tablet", widthDp = 1200, heightDp = 800)
@Composable
private fun LibraryTabletPreview() = LibraryPreview(previewState())

@Preview(name = "Tablet library column (beside a book pane)", widthDp = 752, heightDp = 768)
@Composable
private fun LibraryTabletColumnPreview() = LibraryPreview(previewState())

@Preview(name = "Tablet portrait", widthDp = 720, heightDp = 1248)
@Composable
private fun LibraryTabletPortraitPreview() = LibraryPreview(previewState())

@Preview(name = "Arrange sheet", widthDp = 390, heightDp = 844)
@Composable
private fun ArrangeSheetPreview() {
    YomuDesignTheme(themeMode = YomuThemeMode.Light) {
        LibraryOptionsSheet(
            visible = true,
            sortMode = SortMode.Recent,
            viewMode = LibraryViewMode.ComfortableGrid,
            portraitColumns = 0,
            landscapeColumns = 5,
            onSortModeChange = {},
            onViewModeChange = {},
            onPortraitColumnsChange = {},
            onLandscapeColumnsChange = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Tablet dark", widthDp = 752, heightDp = 1000)
@Composable
private fun LibraryTabletDarkPreview() = LibraryPreview(previewState(), YomuThemeMode.Dark)
