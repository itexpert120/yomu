package com.itexpert120.yomu.feature.library

import androidx.compose.ui.graphics.Color
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ShelfStatus
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.shelfStatus
import com.itexpert120.yomu.data.collections.CatalogEntry
import com.itexpert120.yomu.data.collections.LibraryTab
import com.itexpert120.yomu.data.collections.LibraryTabKeys
import com.itexpert120.yomu.data.collections.Organize
import com.itexpert120.yomu.data.collections.StatusChoice
import java.text.Collator

/**
 * Presentation projection of a core [Book] for the library feature. Holds Compose colors and
 * pre-derived display strings so composables stay free of mapping logic.
 */
data class LibraryBook(
    val id: String,
    val title: String,
    val shortTitle: String,
    val author: String,
    val authorLastName: String,
    val progress: Float,
    val coverImagePath: String?,
    val coverColors: List<Color>,
    val lastOpenedAt: Long,
    val series: String?,
    // The one shelf the book sits on (manual On hold / Dropped win over progress).
    val shelf: ShelfStatus = ShelfStatus.of(progress, null),
)

fun Book.toLibraryBook(): LibraryBook = LibraryBook(
    id = id.value,
    title = title,
    shortTitle = title,
    author = author,
    authorLastName = author.lastNameKey(),
    progress = progress,
    coverImagePath = coverImagePath,
    coverColors = coverPalette.map { Color(it) },
    lastOpenedAt = lastOpenedAt,
    series = series,
    shelf = shelfStatus,
)

private fun String.lastNameKey(): String = trim().substringAfterLast(' ').uppercase()

/** The books shown under one tab, already searched and sorted. */
data class LibraryPage(val key: String, val books: List<LibraryBook>) {
    val bookCount: Int get() = books.size
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    // True size of the library (for the header count + empty state).
    val totalCount: Int = 0,
    // Number of selectable grid items (excludes the continue-reading hero); for "select all".
    val selectableCount: Int = 0,
    val continueReading: LibraryBook? = null,
    // Books in progress for the pile: opened, not finished, most recent first (at most three).
    val pile: List<LibraryBook> = emptyList(),
    val books: List<LibraryBook> = emptyList(),
    val sortMode: SortMode = SortMode.Recent,
    val viewMode: LibraryViewMode = LibraryViewMode.ComfortableGrid,
    val portraitGridColumns: Int = LibraryPreferences.AUTO_COLUMNS,
    val landscapeGridColumns: Int = LibraryPreferences.AUTO_COLUMNS,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    // Newest first; shown while the search field is empty.
    val recentSearches: List<String> = emptyList(),
    val isImporting: Boolean = false,
    // Files in the running import (0 when unknown).
    val importingCount: Int = 0,
    val importNotice: String? = null,
    val canRetryImport: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val tabs: List<LibraryTab> = emptyList(),
    val selectedTab: String = LibraryTabKeys.ALL,
    val pages: List<LibraryPage> = emptyList(),
    // Every tab in order, shown or not, plus the hidden ones: the Library tabs sheet.
    val allTabs: List<LibraryTab> = emptyList(),
    val hiddenTabs: Set<String> = emptySet(),
    // The Organize sheet's view of the current selection.
    val organize: Organize = Organize(StatusChoice.Auto, emptyList()),
)

// region Pure query logic

fun List<CatalogEntry>.matching(query: String): List<CatalogEntry> {
    if (query.isBlank()) return this
    return filter {
        it.book.title.contains(query, ignoreCase = true) || it.book.author.contains(query, ignoreCase = true)
    }
}

fun List<CatalogEntry>.sortedBy(mode: SortMode): List<CatalogEntry> = when (mode) {
    SortMode.Recent -> sortedByDescending { it.book.lastOpenedAt }
    SortMode.Title -> sortedByCollated { it.book.title }
    SortMode.Author -> sortedByCollated { it.book.author.lastNameKey() }
    SortMode.Unread -> sortedBy { it.book.progress }
}

/**
 * Locale-aware, case-insensitive text sort. Each book's collation key is built once, so the
 * comparator never re-derives strings.
 */
private inline fun List<CatalogEntry>.sortedByCollated(text: (CatalogEntry) -> String): List<CatalogEntry> {
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    return map { collator.getCollationKey(text(it)) to it }
        .sortedBy { it.first }
        .map { it.second }
}

// endregion
