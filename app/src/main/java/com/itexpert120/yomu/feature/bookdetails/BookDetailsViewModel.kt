package com.itexpert120.yomu.feature.bookdetails

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReadingState
import com.itexpert120.yomu.data.bookmarks.BookmarkRepository
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Presentation model for the details screen. */
data class BookDetailsUi(
    val id: String,
    val title: String,
    val author: String,
    val series: String?,
    val description: String?,
    val progress: Float,
    val remaining: String,
    // Total time spent reading this book, e.g. "3h 24m"; null when nothing has been read yet.
    val readingTime: String?,
    // Reading-timeline dates, pre-formatted for display; null when the event hasn't happened.
    val addedDate: String?,
    val startedDate: String?,
    val lastReadDate: String?,
    val finishedDate: String?,
    val coverImagePath: String?,
    val coverColors: List<Color>,
    val readingState: ReadingState,
    val currentChapterId: String? = null,
    val resumeLocatorJson: String? = null,
)

/** Distinguishes the initial Room lookup from a completed lookup whose book is genuinely absent. */
data class BookDetailsState(
    val loading: Boolean = true,
    val book: BookDetailsUi? = null,
)

/** TOC ordering, expressed in the book's own rendering order rather than alphabetically. */
enum class TocSortMode(val label: String) {
    Ascending("Ascending"),
    Descending("Descending"),
}

/**
 * A TOC entry projected for display. [uid] is the entry's own position-independent id used for
 * selection (so entries that share a resource href don't select together); [chapterId] is the
 * resource href used for read-state. [percent] is within-chapter reading progress (0..1) for the
 * chapter currently being read, else null.
 */
data class TocEntryUi(
    val uid: Int,
    val chapterId: String,
    val title: String,
    val locatorJson: String?,
    val depth: Int,
    val read: Boolean,
    val selected: Boolean,
    val bookmarked: Boolean,
    val percent: Float?,
) {
    val jumpable: Boolean get() = locatorJson != null
}

/** TOC section state: loading until extracted, then the (sorted) entries + selection. */
data class TocUiState(
    val loading: Boolean = true,
    val sort: TocSortMode = TocSortMode.Ascending,
    val selectionMode: Boolean = false,
    val items: List<TocEntryUi> = emptyList(),
) {
    val selectedCount: Int get() = items.count { it.selected }
    val readCount: Int get() = items.count { it.jumpable && it.read }
    val unreadCount: Int get() = items.count { it.jumpable && !it.read }
}

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    // "bookId" is the property name from the type-safe BookDetails route.
    private val bookId: String = requireNotNull(savedStateHandle["bookId"])

    val state: StateFlow<BookDetailsState> =
        combine(
            repository.observeBook(BookId(bookId)),
            statsRepository.bookReadingSeconds(BookId(bookId)),
        ) { book, readingSeconds ->
            BookDetailsState(
                loading = false,
                book = book?.toUi(readingSeconds),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailsState())

    // One-shot user messages (e.g. gallery-save result), surfaced as a transient notice.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Prime from the in-memory cache when available so re-opening this book shows the TOC instantly
    // (no loading flash); only a genuine first-load this session starts in the loading state.
    private val cachedToc = repository.cachedTableOfContents(BookId(bookId))
    private val tocLoading = MutableStateFlow(cachedToc == null)
    private val tocItems = MutableStateFlow(cachedToc ?: emptyList())
    private val tocSort = MutableStateFlow(TocSortMode.Ascending)
    private val selectedUids = MutableStateFlow<Set<Int>>(emptySet())
    private val selectionMode = MutableStateFlow(false)
    private val readChapters = repository.observeReadChapters(BookId(bookId))
    private val bookmarks = bookmarkRepository.observeForBook(BookId(bookId))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectionFlow = combine(selectedUids, selectionMode) { uids, mode -> uids to mode }
    private val readingFlow = combine(
        readChapters,
        repository.observeChapterProgress(BookId(bookId)),
    ) { read, progress -> read to progress }
    private val readingAndBookmarksFlow = combine(readingFlow, bookmarks) { reading, savedBookmarks ->
        reading to savedBookmarks
    }

    private val configFlow = combine(tocLoading, tocSort) { loading, sort -> loading to sort }

    val toc: StateFlow<TocUiState> = combine(
        configFlow,
        tocItems,
        selectionFlow,
        readingAndBookmarksFlow,
        state,
    ) { config, items, selection, reading, currentState ->
        val (loading, sort) = config
        val (selected, inSelection) = selection
        val (read, progress) = reading.first
        val savedBookmarks = reading.second
        // uid = document-order index, so selection is per-entry even when hrefs repeat.
        val ordered = items.withIndex().toList()
            .let { if (sort == TocSortMode.Descending) it.asReversed() else it }
        TocUiState(
            loading = loading,
            sort = sort,
            selectionMode = inSelection,
            items = ordered.map { (uid, it) ->
                val storedProgress = progress[it.id]
                // Logical progress is authoritative once present. The old read table remains a
                // fallback for pre-v10/manual rows that have not acquired a progress row yet.
                val isRead = storedProgress?.let { value -> value >= 0.999f } ?: (it.id in read)
                TocEntryUi(
                    uid = uid,
                    chapterId = it.id,
                    title = it.title,
                    locatorJson = if (currentState.book?.currentChapterId == it.id) {
                        currentState.book.resumeLocatorJson ?: it.locatorJson
                    } else {
                        it.locatorJson
                    },
                    depth = it.depth,
                    read = isRead,
                    selected = uid in selected,
                    bookmarked = savedBookmarks.any { bookmark -> bookmark.href == it.id },
                    percent = if (it.locatorJson == null) {
                        null
                    } else if (isRead) {
                        1f
                    } else {
                        storedProgress?.coerceIn(0f, 1f) ?: 0f
                    },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TocUiState())

    init {
        if (cachedToc == null) {
            viewModelScope.launch {
                val items = withContext(Dispatchers.IO) {
                    repository.tableOfContents(BookId(bookId))
                }
                tocItems.value = items
                tocLoading.value = false
            }
        }
    }

    fun onTocSortChange(sort: TocSortMode) = tocSort.update { sort }

    // region TOC selection (keyed by per-entry uid; mapped to resource hrefs when persisting)

    fun onEnterChapterSelection(uid: Int) {
        selectionMode.value = true
        selectedUids.value = setOf(uid)
    }

    fun onToggleChapterSelection(uid: Int) {
        selectedUids.update { if (uid in it) it - uid else it + uid }
    }

    fun onExitChapterSelection() {
        selectionMode.value = false
        selectedUids.value = emptySet()
    }

    fun onSelectAllChapters() {
        selectedUids.value = tocItems.value
            .mapIndexedNotNull { index, item -> index.takeIf { item.locatorJson != null } }
            .toSet()
    }

    fun onDeselectAllChapters() {
        selectedUids.value = emptySet()
    }

    fun onToggleChapterBookmark(uid: Int) {
        val entry = toc.value.items.firstOrNull { it.uid == uid } ?: return
        val existing = bookmarks.value.firstOrNull { it.href == entry.chapterId }
        viewModelScope.launch {
            if (existing != null) {
                bookmarkRepository.delete(existing.id)
            } else {
                val locatorJson = entry.locatorJson ?: return@launch
                bookmarkRepository.toggle(
                    bookId = BookId(bookId),
                    locatorJson = locatorJson,
                    href = entry.chapterId,
                    chapterTitle = entry.title,
                    progression = null,
                )
            }
        }
    }

    fun onMarkSelectedChapters(read: Boolean) {
        val hrefs = selectedUids.value
            .mapNotNull { tocItems.value.getOrNull(it)?.id }
            .distinct()
        viewModelScope.launch { repository.setChaptersRead(BookId(bookId), hrefs, read) }
        onExitChapterSelection()
    }

    /** Per-row quick toggle of a single chapter's read state. */
    fun onSetChapterRead(uid: Int, read: Boolean) {
        val href = tocItems.value.getOrNull(uid)?.id ?: return
        viewModelScope.launch { repository.setChaptersRead(BookId(bookId), listOf(href), read) }
    }

    /** Marks every chapter up to and including the last-selected one (in reading order) as read. */
    fun onMarkPreviousRead() {
        val upTo = selectedUids.value.maxOrNull() ?: return
        val hrefs = tocItems.value.take(upTo + 1)
            .mapNotNull { if (it.locatorJson != null) it.id else null }
            .distinct()
        viewModelScope.launch { repository.setChaptersRead(BookId(bookId), hrefs, read = true) }
        onExitChapterSelection()
    }

    private fun allChapterHrefs(): List<String> = tocItems.value.mapNotNull { if (it.locatorJson != null) it.id else null }.distinct()

    // endregion

    fun saveCoverToGallery() {
        val book = state.value.book ?: return
        val path = book.coverImagePath ?: return
        viewModelScope.launch {
            val name =
                book.title.ifBlank { "cover" }.take(60).replace(Regex("[^A-Za-z0-9 ._-]"), "_")
            val ok = saveImageToGallery(context, File(path), "$name.png")
            _messages.emit(if (ok) "Saved to gallery" else "Couldn't save cover")
        }
    }

    fun markRead() {
        viewModelScope.launch {
            repository.markRead(BookId(bookId))
        }
    }

    fun markUnread() {
        viewModelScope.launch {
            repository.markUnread(BookId(bookId))
        }
    }

    fun remove() {
        viewModelScope.launch { repository.remove(listOf(BookId(bookId))) }
    }
}

/** Current reading position projected for the TOC: which resource and how far through it. */
private fun Book.toUi(readingSeconds: Long): BookDetailsUi = BookDetailsUi(
    id = id.value,
    title = title,
    author = author,
    series = series,
    description = description,
    progress = progress,
    remaining = remainingLabel,
    readingTime = formatReadingDuration(readingSeconds),
    addedDate = formatDate(addedAt),
    startedDate = formatDate(startedAt),
    lastReadDate = formatDate(lastOpenedAt),
    finishedDate = formatDate(finishedAt),
    coverImagePath = coverImagePath,
    coverColors = coverPalette.map { Color(it) },
    readingState = readingState,
    currentChapterId = currentChapterId,
    resumeLocatorJson = locatorJson,
)

/** "3h 24m" / "12m" / "<1m" for any positive duration; null when nothing has been read. */
private fun formatReadingDuration(seconds: Long): String? {
    if (seconds <= 0L) return null
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

/** Friendly absolute date + time like "Jun 21, 2026 · 3:45 PM"; null for unset (0) timestamps. */
private fun formatDate(millis: Long): String? {
    if (millis <= 0L) return null
    return java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}
