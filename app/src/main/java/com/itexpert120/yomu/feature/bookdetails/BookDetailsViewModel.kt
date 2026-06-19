package com.itexpert120.yomu.feature.bookdetails

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReadingState
import com.itexpert120.yomu.data.books.BookRepository
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
    val coverImagePath: String?,
    val coverColors: List<Color>,
    val readingState: ReadingState,
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
}

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
) : ViewModel() {

    // "bookId" is the property name from the type-safe BookDetails route.
    private val bookId: String = requireNotNull(savedStateHandle["bookId"])

    val state: StateFlow<BookDetailsUi?> =
        repository.observeBook(BookId(bookId))
            .map { it?.toUi() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private val selectionFlow = combine(selectedUids, selectionMode) { uids, mode -> uids to mode }
    private val positionFlow = repository.observeBook(BookId(bookId))
        .map { ReadingPosition(it?.currentHref, it?.currentChapterProgress) }

    private val configFlow = combine(tocLoading, tocSort) { loading, sort -> loading to sort }

    val toc: StateFlow<TocUiState> = combine(
        configFlow, tocItems, readChapters, selectionFlow, positionFlow,
    ) { config, items, read, selection, position ->
        val (loading, sort) = config
        val (selected, inSelection) = selection
        // uid = document-order index, so selection is per-entry even when hrefs repeat.
        val ordered = items.withIndex().toList()
            .let { if (sort == TocSortMode.Descending) it.asReversed() else it }
        TocUiState(
            loading = loading,
            sort = sort,
            selectionMode = inSelection,
            items = ordered.map { (uid, it) ->
                val isRead = it.id in read
                TocEntryUi(
                    uid = uid,
                    chapterId = it.id,
                    title = it.title,
                    locatorJson = it.locatorJson,
                    depth = it.depth,
                    read = isRead,
                    selected = uid in selected,
                    // Show progress only for the chapter currently being read and not yet finished.
                    percent = if (!isRead && it.id == position.href) position.chapterProgress else null,
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

    private fun allChapterHrefs(): List<String> =
        tocItems.value.mapNotNull { if (it.locatorJson != null) it.id else null }.distinct()

    // endregion

    fun saveCoverToGallery() {
        val book = state.value ?: return
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
            // Marking the whole book read should reflect across every chapter in the TOC.
            repository.setChaptersRead(BookId(bookId), allChapterHrefs(), read = true)
        }
    }

    fun markUnread() {
        viewModelScope.launch {
            repository.markUnread(BookId(bookId))
            repository.setChaptersRead(BookId(bookId), allChapterHrefs(), read = false)
        }
    }

    fun remove() {
        viewModelScope.launch { repository.remove(listOf(BookId(bookId))) }
    }
}

/** Current reading position projected for the TOC: which resource and how far through it. */
private data class ReadingPosition(val href: String?, val chapterProgress: Float?)

private fun Book.toUi(): BookDetailsUi = BookDetailsUi(
    id = id.value,
    title = title,
    author = author,
    series = series,
    description = description,
    progress = progress,
    remaining = remainingLabel,
    coverImagePath = coverImagePath,
    coverColors = coverPalette.map { Color(it) },
    readingState = readingState,
)
