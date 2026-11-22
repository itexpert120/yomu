package com.itexpert120.yomu.feature.library

import androidx.compose.ui.graphics.Color
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookCollection
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.ReadingState
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.feature.collections.ManagedTab
import com.itexpert120.yomu.feature.collections.OrganizeCollection
import com.itexpert120.yomu.feature.collections.StatusChoice
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
    val remaining: String,
    val coverImagePath: String?,
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
    coverImagePath = coverImagePath,
    coverColors = coverPalette.map { Color(it) },
    lastOpenedAt = lastOpenedAt,
    series = series,
)

private fun String.lastNameKey(): String = trim().substringAfterLast(' ').uppercase()

/** A library tab: a reading-status shelf or one of the user's collections. */
data class LibraryTab(val key: String, val label: String, val count: Int, val isCollection: Boolean = false)

/** The books shown under one tab, already searched and sorted. */
data class LibraryPage(val key: String, val books: List<LibraryBook>) {
    val bookCount: Int get() = books.size
}

object LibraryTabKeys {
    const val ALL = "all"
    const val READING = "reading"
    const val TO_READ = "toRead"
    const val FINISHED = "finished"
    const val ON_HOLD = "onHold"
    const val DROPPED = "dropped"
    private const val COLLECTION_PREFIX = "c:"

    fun collection(id: String) = COLLECTION_PREFIX + id

    /** The collection id a tab key refers to, or null for a status tab. */
    fun collectionId(key: String): String? = if (key.startsWith(COLLECTION_PREFIX)) key.substring(COLLECTION_PREFIX.length) else null
}

data class LibraryUiState(
    val isLoading: Boolean = true,
    // True size of the library (for the header count + empty state).
    val totalCount: Int = 0,
    // Number of selectable grid items (excludes the continue-reading hero); for "select all".
    val selectableCount: Int = 0,
    val continueReading: LibraryBook? = null,
    val books: List<LibraryBook> = emptyList(),
    val sortMode: SortMode = SortMode.Recent,
    val viewMode: LibraryViewMode = LibraryViewMode.ComfortableGrid,
    val portraitGridColumns: Int = LibraryPreferences.AUTO_COLUMNS,
    val landscapeGridColumns: Int = LibraryPreferences.AUTO_COLUMNS,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val importNotice: String? = null,
    val canRetryImport: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val tabs: List<LibraryTab> = emptyList(),
    val selectedTab: String = LibraryTabKeys.ALL,
    val pages: List<LibraryPage> = emptyList(),
    val managedTabs: List<ManagedTab> = emptyList(),
    // The Organize sheet's view of the current selection.
    val organizeStatus: StatusChoice = StatusChoice.Auto,
    val organizeCollections: List<OrganizeCollection> = emptyList(),
)

// region Pure query logic

fun List<Book>.matching(query: String): List<Book> {
    if (query.isBlank()) return this
    return filter {
        it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
    }
}

fun List<Book>.sortedBy(mode: SortMode): List<Book> = when (mode) {
    SortMode.Recent -> sortedByDescending { it.lastOpenedAt }
    SortMode.Title -> sortedByCollated { it.title }
    SortMode.Author -> sortedByCollated { it.author.lastNameKey() }
    SortMode.Unread -> sortedBy { it.progress }
}

/**
 * Locale-aware, case-insensitive text sort. Each book's collation key is built once, so the
 * comparator never re-derives strings.
 */
private inline fun List<Book>.sortedByCollated(text: (Book) -> String): List<Book> {
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    return map { collator.getCollationKey(text(it)) to it }
        .sortedBy { it.first }
        .map { it.second }
}

/**
 * Whether [this] book is shown under the tab [key]. A manual status takes precedence over progress:
 * an On hold book is not also listed under Reading. Collections and All ignore status.
 */
fun Book.belongsTo(key: String, collectionIds: Set<String>): Boolean = when (key) {
    LibraryTabKeys.ALL -> true
    LibraryTabKeys.READING -> manualStatus == null && readingState == ReadingState.Reading
    LibraryTabKeys.TO_READ -> manualStatus == null && readingState == ReadingState.Unread
    LibraryTabKeys.FINISHED -> manualStatus == null && readingState == ReadingState.Finished
    LibraryTabKeys.ON_HOLD -> manualStatus == ManualStatus.OnHold
    LibraryTabKeys.DROPPED -> manualStatus == ManualStatus.Dropped
    else -> LibraryTabKeys.collectionId(key)?.let { it in collectionIds } ?: false
}

/** The built-in status tabs, in their default order. */
val StatusTabs = listOf(
    LibraryTabKeys.ALL to "All",
    LibraryTabKeys.READING to "Reading",
    LibraryTabKeys.TO_READ to "To read",
    LibraryTabKeys.FINISHED to "Finished",
    LibraryTabKeys.ON_HOLD to ManualStatus.OnHold.label,
    LibraryTabKeys.DROPPED to ManualStatus.Dropped.label,
)

/** On hold / Dropped only show while they hold a book. */
fun isOptionalTab(key: String) = key == LibraryTabKeys.ON_HOLD || key == LibraryTabKeys.DROPPED

/**
 * Every tab, shown or not, in the user's [order]: the status tabs then collections by default, with
 * tabs the order doesn't mention (a new collection) keeping their default place after it.
 */
fun allLibraryTabs(
    books: List<Book>,
    collections: List<BookCollection>,
    memberships: Map<String, Set<String>>,
    order: List<String>,
): List<LibraryTab> {
    fun count(key: String) = books.count { it.belongsTo(key, memberships[it.id.value].orEmpty()) }
    val defaults = StatusTabs.map { (key, label) -> LibraryTab(key, label, count(key)) } +
        collections.map { c ->
            val key = LibraryTabKeys.collection(c.id)
            LibraryTab(key, c.name, count(key), isCollection = true)
        }
    val rank = order.withIndex().associate { (index, key) -> key to index }
    return defaults.withIndex()
        .sortedWith(compareBy({ rank[it.value.key] ?: (order.size + it.index) }, { it.index }))
        .map { it.value }
}

/** Status tabs the user may turn off; All always stays. */
fun isHideableTab(key: String) = key != LibraryTabKeys.ALL && StatusTabs.any { it.first == key }

/**
 * The tabs the strip shows: minus status tabs the user hid, and On hold / Dropped only while they
 * hold a book or are open (so emptying one doesn't yank it away mid-use).
 */
fun visibleLibraryTabs(all: List<LibraryTab>, selectedKey: String, hidden: Set<String>): List<LibraryTab> = all.filter { tab ->
    val hiddenByUser = isHideableTab(tab.key) && tab.key in hidden
    !hiddenByUser && (!isOptionalTab(tab.key) || tab.count > 0 || tab.key == selectedKey)
}

fun libraryTabs(
    books: List<Book>,
    collections: List<BookCollection>,
    memberships: Map<String, Set<String>>,
    selectedKey: String,
    order: List<String> = emptyList(),
    hidden: Set<String> = emptySet(),
): List<LibraryTab> = visibleLibraryTabs(allLibraryTabs(books, collections, memberships, order), selectedKey, hidden)

// endregion
