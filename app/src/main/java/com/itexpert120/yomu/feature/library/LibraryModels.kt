package com.itexpert120.yomu.feature.library

import androidx.compose.ui.graphics.Color
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode

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
    val remaining: String,
    val coverColors: List<Color>,
    val lastOpenedAt: Long,
    val series: String?,
)

fun Book.toLibraryBook(): LibraryBook = LibraryBook(
    id = id.value,
    title = title,
    shortTitle = title,
    author = author,
    authorLastName = author.lastNameKey(),
    progress = progress,
    remaining = remainingLabel,
    coverColors = coverPalette.map { Color(it) },
    lastOpenedAt = lastOpenedAt,
    series = series,
)

private fun String.lastNameKey(): String = trim().substringAfterLast(' ').uppercase()

/** An ordered, labeled section of books for the grouped library grid. */
data class LibraryGroup(val label: String, val books: List<LibraryBook>)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val continueReading: LibraryBook? = null,
    val groups: List<LibraryGroup> = emptyList(),
    val sortMode: SortMode = SortMode.Recent,
    val groupMode: GroupMode = GroupMode.None,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    val gridColumns: Int = 3,
    val coverCrop: Boolean = true,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
)

// region Pure query logic (unit-tested in LibraryQueryTest)

fun List<Book>.matching(query: String): List<Book> {
    if (query.isBlank()) return this
    return filter {
        it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
    }
}

fun List<Book>.sortedBy(mode: SortMode): List<Book> = when (mode) {
    SortMode.Recent -> sortedByDescending { it.lastOpenedAt }
    SortMode.Title -> sortedBy { it.title.lowercase() }
    SortMode.Author -> sortedBy { it.author.lastNameKey().lowercase() }
    SortMode.Unread -> sortedBy { it.progress }
}

/** Ordered grouping. [GroupMode.None] yields a single unlabeled group. */
fun List<Book>.groupedBy(mode: GroupMode): List<LibraryGroup> = when (mode) {
    GroupMode.None -> listOf(LibraryGroup("", map { it.toLibraryBook() }))
    GroupMode.Author -> groupToSections { it.author.lastNameKey() }
    GroupMode.Series -> groupToSections { it.series ?: "Standalone" }
}

private inline fun List<Book>.groupToSections(key: (Book) -> String): List<LibraryGroup> =
    groupBy(key).map { (label, books) -> LibraryGroup(label, books.map { it.toLibraryBook() }) }

// endregion
