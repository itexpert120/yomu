package com.itexpert120.yomu.feature.bookdetails

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.text.HtmlCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.ShelfStatus
import com.itexpert120.yomu.core.model.shelfStatus
import com.itexpert120.yomu.core.reader.BookmarkAnchor
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.bookmarks.BookmarkRepository
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.ChapterReadState
import com.itexpert120.yomu.data.books.ChapterReading
import com.itexpert120.yomu.data.books.PublicationIndex
import com.itexpert120.yomu.data.books.TocLoad
import com.itexpert120.yomu.data.collections.Catalog
import com.itexpert120.yomu.data.collections.LibraryCatalog
import com.itexpert120.yomu.data.collections.Organize
import com.itexpert120.yomu.data.collections.StatusChoice
import com.itexpert120.yomu.data.reader.ReaderPreloader
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
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
    // Total time spent reading this book, e.g. "3h 24m"; null when nothing has been read yet.
    val readingTime: String?,
    // Reading-timeline dates, pre-formatted for display; null when the event hasn't happened.
    val addedDate: String?,
    val startedDate: String?,
    val lastReadDate: String?,
    val finishedDate: String?,
    val coverImagePath: String?,
    val coverColors: List<Color>,
    val shelfStatus: ShelfStatus,
    val currentChapterId: String? = null,
    val resumeLocatorJson: String? = null,
    val collectionNames: List<String> = emptyList(),
) {
    /** A started, unfinished book reads "Resume" (whatever its shelf status). */
    val canResume: Boolean get() = progress > 0f && progress < 1f
}

/** Distinguishes the initial Room lookup from a completed lookup whose book is genuinely absent. */
data class BookDetailsState(
    val loading: Boolean = true,
    val book: BookDetailsUi? = null,
    val organize: Organize = Organize(StatusChoice.Auto, emptyList()),
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
    // 1-based position among the contents' entries, shown only when the contents are one flat list.
    val number: Int? = null,
    // The entry the reading position is in (the first one, when entries share a resource).
    val current: Boolean = false,
    // Entries nested under this one (a volume's chapters); > 0 makes it collapsible.
    val children: Int = 0,
    val expanded: Boolean = true,
    // Jumpable entries under this one, and how many of them are read.
    val chapterCount: Int = 0,
    val chapterReadCount: Int = 0,
    // The reading position is somewhere under this entry.
    val holdsCurrent: Boolean = false,
) {
    val jumpable: Boolean get() = locatorJson != null
}

/**
 * TOC section state: loading until extracted, then the (sorted) entries + selection. The counts are
 * computed once with [items] (which can hold thousands of entries) rather than on every read.
 */
data class TocUiState(
    val loading: Boolean = true,
    val sort: TocSortMode = TocSortMode.Ascending,
    val selectionMode: Boolean = false,
    val items: List<TocEntryUi> = emptyList(),
    val selectedCount: Int = 0,
    // Jumpable (selectable) entries; read + unread.
    val selectableCount: Int = 0,
    val readCount: Int = 0,
    val unreadCount: Int = 0,
    // Where the reading position is among the jumpable entries (1-based), if it is in the contents.
    val currentNumber: Int? = null,
    val currentTitle: String? = null,
    // Every jumpable entry sits at the top level, so entries can be counted as chapters.
    val flat: Boolean = false,
    // Entries with others nested under them (volumes), and how many of those are collapsed.
    val collapsibleCount: Int = 0,
    val collapsedCount: Int = 0,
)

/** The book's reading position, as far as the TOC needs it. */
private data class TocResumePosition(val chapterId: String?, val locatorJson: String?)

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val statsRepository: StatsRepository,
    private val catalog: LibraryCatalog,
    private val chapterReading: ChapterReading,
    publications: PublicationIndex,
    private val readerPreloader: ReaderPreloader,
) : ViewModel() {

    // "bookId" is the property name from the type-safe BookDetails route.
    private val bookId: String = requireNotNull(savedStateHandle["bookId"])

    val state: StateFlow<BookDetailsState> =
        combine(
            catalog.observe(),
            statsRepository.bookReadingSeconds(BookId(bookId)),
        ) { catalog, readingSeconds -> detailsState(catalog, readingSeconds) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), seededState())

    private fun detailsState(catalog: Catalog, readingSeconds: Long): BookDetailsState {
        val entry = catalog.entry(BookId(bookId))
        return BookDetailsState(
            loading = false,
            book = entry?.book?.toUi(readingSeconds)?.copy(
                collectionNames = catalog.collections.filter { it.id in entry.collectionIds }.map { it.name },
            ),
            organize = catalog.organize(listOf(BookId(bookId))),
        )
    }

    // The library has usually just loaded this book, so the page opens with its content rather than
    // a loading frame that would sit in the middle of the screen transition.
    private fun seededState(): BookDetailsState {
        val known = catalog.latest?.takeIf { it.entry(BookId(bookId)) != null } ?: return BookDetailsState()
        return detailsState(known, statsRepository.peekBookReadingSeconds(BookId(bookId)) ?: 0L)
    }

    // One-shot user messages (e.g. gallery-save result), surfaced as a transient notice.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Starts Ready when the book's TOC is already in memory, so a repeat open shows no loading flash.
    private val tocLoad = publications.observeToc(BookId(bookId))
    private val tocItems: List<ReaderTocItem> get() = (tocLoad.value as? TocLoad.Ready)?.items.orEmpty()
    private val tocSort = MutableStateFlow(TocSortMode.Ascending)

    // Collapsed volumes by uid; kept across process death so the contents come back as left.
    private val collapsed = MutableStateFlow(savedStateHandle.get<IntArray>(COLLAPSED_KEY)?.toSet().orEmpty())
    private val savedState = savedStateHandle
    private val layout = combine(tocSort, collapsed) { sort, collapsed -> TocLayout(sort, collapsed) }
    private val selectedUids = MutableStateFlow<Set<Int>>(emptySet())
    private val selectionMode = MutableStateFlow(false)
    private val bookmarks = bookmarkRepository.observeForBook(BookId(bookId))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectionFlow = combine(selectedUids, selectionMode) { uids, mode -> uids to mode }
    private val bookmarkedHrefs = bookmarks.map { saved -> saved.mapTo(HashSet()) { it.href } }
    private val readingAndBookmarksFlow = combine(chapterReading.observe(BookId(bookId)), bookmarkedHrefs) { reading, hrefs ->
        reading to hrefs
    }

    // Only the reading position matters to the TOC, so stats/collection changes don't rebuild it.
    private val resumePosition = state
        .map { TocResumePosition(it.book?.currentChapterId, it.book?.resumeLocatorJson) }
        .distinctUntilChanged()

    val toc: StateFlow<TocUiState> = combine(
        tocLoad,
        layout,
        selectionFlow,
        readingAndBookmarksFlow,
        resumePosition,
    ) { load, layout, selection, reading, resume ->
        tocState(load, layout, selection.first, selection.second, reading.first, reading.second, resume)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), seededToc())

    // A book reopened in this session still has its contents and read marks in memory.
    private fun seededToc(): TocUiState {
        val load = tocLoad.value as? TocLoad.Ready ?: return TocUiState()
        val chapters = chapterReading.peek(BookId(bookId)) ?: return TocUiState()
        val book = state.value.book
        return tocState(
            load = load,
            layout = TocLayout(TocSortMode.Ascending, collapsed.value),
            selected = emptySet(),
            inSelection = false,
            chapters = chapters,
            bookmarked = emptySet(),
            resume = TocResumePosition(book?.currentChapterId, book?.resumeLocatorJson),
        )
    }

    private fun tocState(
        load: TocLoad,
        layout: TocLayout,
        selected: Set<Int>,
        inSelection: Boolean,
        chapters: ChapterReadState,
        bookmarked: Set<String?>,
        resume: TocResumePosition,
    ): TocUiState {
        val items = (load as? TocLoad.Ready)?.items.orEmpty()
        val outline = TocOutline.of(items.map { it.depth })
        // One pass in document order: numbers over the jumpable entries, which one holds the reading
        // position, and running read / jumpable counts so a volume's tallies are a subtraction.
        val flat = items.all { it.locatorJson == null || it.depth == 0 }
        val numbers = IntArray(items.size)
        val read = BooleanArray(items.size)
        val jumpableBefore = IntArray(items.size + 1)
        val readBefore = IntArray(items.size + 1)
        var currentUid = -1
        items.forEachIndexed { index, item ->
            val jumpable = item.locatorJson != null
            read[index] = chapters.isRead(item.id)
            jumpableBefore[index + 1] = jumpableBefore[index] + if (jumpable) 1 else 0
            readBefore[index + 1] = readBefore[index] + if (jumpable && read[index]) 1 else 0
            if (jumpable) {
                numbers[index] = jumpableBefore[index + 1]
                if (currentUid < 0 && resume.chapterId != null && item.id == resume.chapterId) currentUid = index
            }
        }
        val readCount = readBefore[items.size]
        val selectableCount = jumpableBefore[items.size]
        // Only collapsed entries that still exist and still have something under them count.
        val collapsed = layout.collapsed.filterTo(HashSet()) { it in items.indices && outline.hasChildren(it) }
        val shown = outline.visible(outline.order(layout.sort == TocSortMode.Descending), collapsed)
        // uid = document-order index, so selection is per-entry even when hrefs repeat.
        val entries = ArrayList<TocEntryUi>(shown.size)
        for (uid in shown) {
            val it = items[uid]
            val children = outline.descendants[uid]
            val subtreeEnd = uid + children + 1
            entries += TocEntryUi(
                uid = uid,
                chapterId = it.id,
                title = it.title,
                locatorJson = if (resume.chapterId == it.id) {
                    resume.locatorJson ?: it.locatorJson
                } else {
                    it.locatorJson
                },
                depth = it.depth,
                read = read[uid],
                selected = uid in selected,
                bookmarked = it.id in bookmarked,
                percent = if (it.locatorJson == null) null else chapters.percent(it.id),
                number = if (flat && it.locatorJson != null) numbers[uid] else null,
                current = uid == currentUid,
                children = children,
                expanded = uid !in collapsed,
                chapterCount = jumpableBefore[subtreeEnd] - jumpableBefore[uid + 1],
                chapterReadCount = readBefore[subtreeEnd] - readBefore[uid + 1],
                holdsCurrent = children > 0 && currentUid >= 0 && outline.isUnder(currentUid, uid),
            )
        }
        return TocUiState(
            loading = load is TocLoad.Loading,
            sort = layout.sort,
            selectionMode = inSelection,
            items = entries,
            selectedCount = selected.count { it in items.indices },
            selectableCount = selectableCount,
            readCount = readCount,
            unreadCount = selectableCount - readCount,
            currentNumber = numbers.getOrNull(currentUid),
            currentTitle = items.getOrNull(currentUid)?.title,
            flat = flat,
            collapsibleCount = items.indices.count { outline.hasChildren(it) },
            collapsedCount = collapsed.size,
        )
    }

    init {
        // Read is the likely next tap: open the book (and warm the engine) once the page has settled.
        viewModelScope.launch {
            delay(PRELOAD_DELAY_MS)
            readerPreloader.warmUp()
            readerPreloader.preload(BookId(bookId))
        }
    }

    fun onTocSortChange(sort: TocSortMode) = tocSort.update { sort }

    // region Volumes (collapsible entries, by uid)

    fun onToggleVolume(uid: Int) = setCollapsed { if (uid in it) it - uid else it + uid }

    fun onCollapseAllVolumes() {
        val outline = TocOutline.of(tocItems.map { it.depth })
        setCollapsed { tocItems.indices.filterTo(HashSet()) { outline.hasChildren(it) } }
    }

    fun onExpandAllVolumes() = setCollapsed { emptySet() }

    private fun setCollapsed(change: (Set<Int>) -> Set<Int>) {
        val next = collapsed.updateAndGet(change)
        savedState[COLLAPSED_KEY] = next.toIntArray()
    }

    // endregion

    // region TOC selection (keyed by per-entry uid; mapped to resource hrefs when persisting)

    fun onEnterChapterSelection(uid: Int) {
        selectionMode.value = true
        selectedUids.value = setOf(uid)
    }

    /** Selection mode with nothing selected yet (from the book's options menu). */
    fun onStartChapterSelection() {
        selectionMode.value = true
        selectedUids.value = emptySet()
    }

    fun onToggleChapterSelection(uid: Int) {
        selectedUids.update { if (uid in it) it - uid else it + uid }
    }

    fun onExitChapterSelection() {
        selectionMode.value = false
        selectedUids.value = emptySet()
    }

    fun onSelectAllChapters() {
        selectedUids.value = tocItems
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
                bookmarkRepository.setBookmarked(
                    bookId = BookId(bookId),
                    at = BookmarkAnchor(anchor = entry.chapterId, locatorJson = locatorJson, progression = null),
                    chapterTitle = entry.title,
                    bookmarked = true,
                )
            }
        }
    }

    fun onMarkSelectedChapters(read: Boolean) {
        val hrefs = selectedUids.value
            .mapNotNull { tocItems.getOrNull(it)?.id }
            .distinct()
        viewModelScope.launch { chapterReading.mark(BookId(bookId), hrefs, read) }
        onExitChapterSelection()
    }

    /** Per-row quick toggle of a single chapter's read state. */
    fun onSetChapterRead(uid: Int, read: Boolean) {
        val href = tocItems.getOrNull(uid)?.id ?: return
        viewModelScope.launch { chapterReading.mark(BookId(bookId), listOf(href), read) }
    }

    /** Marks every chapter up to and including the last-selected one (in reading order) as read. */
    fun onMarkPreviousRead() {
        val upTo = selectedUids.value.maxOrNull() ?: return
        val toc = tocItems
        viewModelScope.launch { chapterReading.markThrough(BookId(bookId), toc, upTo) }
        onExitChapterSelection()
    }

    // endregion

    fun saveCoverToGallery() {
        val book = state.value.book ?: return
        val path = book.coverImagePath ?: return
        viewModelScope.launch {
            val name =
                book.title.ifBlank { "cover" }.take(60).replace(Regex("[^A-Za-z0-9 ._-]"), "_")
            val ok = saveImageToGallery(context, File(path), name)
            _messages.emit(if (ok) "Cover saved to Pictures / Open Reader" else "Couldn't save the cover")
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

    fun onSetStatus(status: ManualStatus?) {
        viewModelScope.launch { catalog.setManualStatus(listOf(BookId(bookId)), status) }
    }

    fun onSetCollection(collectionId: String, member: Boolean) {
        viewModelScope.launch { catalog.setMembership(collectionId, listOf(BookId(bookId)), member) }
    }

    /** Makes a collection and puts this book in it. */
    fun onCreateCollection(name: String) {
        viewModelScope.launch { catalog.createCollection(name, add = listOf(BookId(bookId))) }
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
    description = description?.toPlainDescription(),
    progress = progress,
    readingTime = formatReadingDuration(readingSeconds),
    addedDate = formatDate(addedAt),
    startedDate = formatDate(startedAt),
    lastReadDate = formatDate(lastOpenedAt),
    finishedDate = formatDate(finishedAt),
    coverImagePath = coverImagePath,
    coverColors = coverPalette.map { Color(it) },
    shelfStatus = shelfStatus,
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
        // Past 100 hours the minutes are noise, and they keep the chip narrow on small phones.
        hours >= 100 -> "${hours}h"
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

/**
 * EPUB descriptions are often HTML (`<p><b>…`), sometimes entity-escaped (`&lt;p&gt;`), so render
 * them as plain text: decode up to twice, while tags are still present.
 */
private fun String.toPlainDescription(): String? {
    var text = this
    repeat(2) {
        if (!HTML_TAG.containsMatchIn(text) && !text.contains('&')) return@repeat
        text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
    }
    return text.replace(BLANK_LINES, "\n\n").trim().ifBlank { null }
}

private val HTML_TAG = Regex("</?[a-zA-Z][^>]*>")
private val BLANK_LINES = Regex("\n{3,}")

private const val PRELOAD_DELAY_MS = 400L

private const val COLLAPSED_KEY = "collapsedVolumes"

/** How the contents are laid out: their order and which volumes are collapsed. */
private data class TocLayout(val sort: TocSortMode, val collapsed: Set<Int>)
