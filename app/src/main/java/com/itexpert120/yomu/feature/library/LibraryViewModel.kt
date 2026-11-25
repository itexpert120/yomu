package com.itexpert120.yomu.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.PublicationIndex
import com.itexpert120.yomu.data.collections.Catalog
import com.itexpert120.yomu.data.collections.LibraryCatalog
import com.itexpert120.yomu.data.collections.LibraryTabKeys
import com.itexpert120.yomu.data.collections.isHideableTab
import com.itexpert120.yomu.data.reader.ReaderPreloader
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import com.itexpert120.yomu.domain.imports.ImportBooksUseCase
import com.itexpert120.yomu.domain.imports.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.IdentityHashMap
import javax.inject.Inject

/** The pile: opened, unfinished books, most recently opened first, at most three. */
internal fun List<Book>.pileBooks(): List<Book> = asSequence()
    .filter { it.lastOpenedAt > 0L && it.progress < 0.995f }
    .sortedByDescending { it.lastOpenedAt }
    .take(3)
    .toList()

/** Where a pile book is, to name its chapter. */
private data class PilePosition(val id: BookId, val chapterId: String?, val href: String?) {
    /** The contents entry the book is in (by chapter id, else by the resource it is reading). */
    fun chapterIn(toc: List<ReaderTocItem>): String? {
        val byId = chapterId?.let { id -> toc.firstOrNull { it.id == id } }
        val resource = href?.substringBefore('#')
        val item = byId ?: resource?.let { r -> toc.firstOrNull { it.resourceHref.substringBefore('#') == r } }
        return item?.title?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Transient search state — intentionally not persisted across launches. */
private data class SearchState(val active: Boolean = false, val query: String = "")

/** Transient import progress/result, surfaced as an inline notice. */
private data class ImportState(
    val isImporting: Boolean = false,
    // How many files the running import was given, for its progress notice.
    val count: Int = 0,
    val notice: String? = null,
    val retryUris: List<Uri> = emptyList(),
)

/** Transient multi-select state. */
private data class SelectionState(val active: Boolean = false, val ids: Set<String> = emptySet())

/** Transient screen inputs. */
private data class LibraryInputs(
    val search: SearchState,
    val import: ImportState,
    val selection: SelectionState,
    val tab: String,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    private val catalog: LibraryCatalog,
    private val libraryPrefs: LibraryPrefsRepository,
    private val importBooks: ImportBooksUseCase,
    private val readerPreloader: ReaderPreloader,
    private val recentSearches: RecentLibrarySearches,
    private val publicationIndex: PublicationIndex,
) : ViewModel() {

    private val search = MutableStateFlow(SearchState())
    private val importState = MutableStateFlow(ImportState())
    private val selection = MutableStateFlow(SelectionState())
    private var importNoticeDismissJob: Job? = null

    // The open tab is remembered across launches (device-local). The pending value covers the few
    // milliseconds before DataStore reports the write, so a swipe never snaps back.
    private val pendingTab = MutableStateFlow<String?>(null)
    private val selectedTab = combine(libraryPrefs.preferences, pendingTab) { prefs, pending -> pending ?: prefs.lastTab }

    private val inputs = combine(search, importState, selection, selectedTab, ::LibraryInputs)

    // Projections of the current Book instances, reused while the library data is unchanged so a
    // keystroke or selection change hands the grid the same LibraryBook instances (cheap skipping).
    // Only touched from the serial state pipeline below.
    private var projections = IdentityHashMap<Book, LibraryBook>()

    // The chapter each pile book is in, from its already-indexed contents (never parses an EPUB).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pileChapters = catalog.observe()
        .map { catalog -> catalog.entries.map { it.book }.pileBooks().map { PilePosition(it.id, it.currentChapterId, it.currentHref) } }
        .distinctUntilChanged()
        .mapLatest { positions ->
            positions.mapNotNull { position ->
                val toc = publicationIndex.publicationCache(position.id)?.toc ?: return@mapNotNull null
                position.chapterIn(toc)?.let { position.id.value to it }
            }.toMap()
        }
        .onStart { emit(emptyMap()) }

    val state: StateFlow<LibraryUiState> =
        combine(catalog.observe(), libraryPrefs.preferences, inputs, recentSearches.searches, pileChapters) { d, prefs, i, recent, chapters ->
            buildState(d, prefs, i, recent).copy(pileChapters = chapters)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private var preloadJob: Job? = null

    /**
     * The library is on screen with [resumeId] as its Resume book: once it has settled, warm the
     * reader engine and open that book so Resume skips parsing.
     */
    fun onLibraryShown(resumeId: String?) {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            delay(PRELOAD_DELAY_MS)
            readerPreloader.warmUp()
            resumeId?.let { readerPreloader.preload(BookId(it)) }
        }
    }

    fun onSelectTab(key: String) {
        if (key == state.value.selectedTab && pendingTab.value == null) return
        pendingTab.value = key
        viewModelScope.launch {
            libraryPrefs.setLastTab(key)
            pendingTab.compareAndSet(key, null)
        }
    }

    // region Collections and status

    fun onCreateCollection(name: String, addSelected: Boolean = false) {
        val ids = if (addSelected) selectedBookIds() else emptyList()
        launchSafely { catalog.createCollection(name, add = ids) }
    }

    fun onRenameCollection(id: String, name: String) = launchSafely { catalog.renameCollection(id, name) }

    fun onDeleteCollection(id: String) {
        if (state.value.selectedTab == LibraryTabKeys.collection(id)) onSelectTab(LibraryTabKeys.ALL)
        launchSafely { catalog.deleteCollection(id) }
    }

    fun onSetTabHidden(key: String, hidden: Boolean) {
        if (!isHideableTab(key)) return
        launchSafely { libraryPrefs.setTabHidden(key, hidden) }
    }

    /** Moves any tab (status or collection) one place; the whole order is then remembered. */
    fun onMoveTab(key: String, up: Boolean) {
        val order = state.value.allTabs.map { it.key }.toMutableList()
        val from = order.indexOf(key)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in order.indices) return
        order.add(to, order.removeAt(from))
        launchSafely { libraryPrefs.setTabOrder(order) }
    }

    fun onSetSelectedInCollection(collectionId: String, member: Boolean) {
        val ids = selectedBookIds()
        launchSafely { catalog.setMembership(collectionId, ids, member) }
    }

    fun onSetSelectedStatus(status: ManualStatus?) {
        val ids = selectedBookIds()
        launchSafely { catalog.setManualStatus(ids, status) }
    }

    private fun selectedBookIds() = selection.value.ids.map { BookId(it) }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showImportNotice(ImportState(notice = "Couldn't update your collections. Please retry."))
            }
        }
    }

    // endregion

    fun onEnterSelection(bookId: String) {
        selection.value = SelectionState(active = true, ids = setOf(bookId))
    }

    fun onToggleSelect(bookId: String) = selection.update { current ->
        val ids = if (bookId in current.ids) current.ids - bookId else current.ids + bookId
        SelectionState(active = ids.isNotEmpty(), ids = ids)
    }

    fun onExitSelection() {
        selection.value = SelectionState()
    }

    fun onSelectAll() {
        selection.value = SelectionState(active = true, ids = visibleBookIds())
    }

    fun onDeselectAll() {
        selection.value = SelectionState()
    }

    fun onRemoveSelected() {
        val ids = selection.value.ids.map { BookId(it) }
        viewModelScope.launch {
            repository.remove(ids)
            selection.value = SelectionState()
        }
    }

    fun onMarkSelectedRead() {
        val ids = selection.value.ids.map { BookId(it) }
        viewModelScope.launch {
            try {
                repository.markRead(ids)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showImportNotice(ImportState(notice = "Couldn't mark selected books read. Please retry."))
            }
        }
    }

    fun onMarkSelectedUnread() {
        val ids = selection.value.ids.map { BookId(it) }
        viewModelScope.launch {
            try {
                repository.markUnread(ids)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showImportNotice(ImportState(notice = "Couldn't mark selected books unread. Please retry."))
            }
        }
    }

    // The continue-reading hero isn't a selectable grid item, so it's excluded here to keep
    // select-all consistent with totalCount.
    private fun visibleBookIds(): Set<String> = buildSet {
        state.value.books.forEach { add(it.id) }
    }

    fun onImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        performImport(uris)
    }

    fun onRetryImport() {
        importState.value.retryUris.takeIf { it.isNotEmpty() }?.let(::performImport)
    }

    private fun performImport(uris: List<Uri>) {
        viewModelScope.launch {
            importNoticeDismissJob?.cancel()
            importState.value = ImportState(isImporting = true, count = uris.size)
            try {
                val summary = importBooks.import(uris)
                showImportNotice(
                    ImportState(
                        notice = summary.toNotice(),
                        retryUris = if (summary.failed > 0) uris else emptyList(),
                    ),
                )
            } catch (_: Throwable) {
                showImportNotice(
                    ImportState(
                        notice = "Import failed. Check the EPUB and try again.",
                        retryUris = uris,
                    ),
                )
            }
        }
    }

    private fun showImportNotice(notice: ImportState) {
        importState.value = notice
        importNoticeDismissJob?.cancel()
        importNoticeDismissJob = viewModelScope.launch {
            delay(IMPORT_NOTICE_DURATION_MS)
            if (importState.value == notice) importState.value = ImportState()
        }
    }

    fun onSearchToggle() = search.update {
        if (it.active) SearchState() else it.copy(active = true)
    }

    fun onSearchQueryChange(query: String) = search.update { it.copy(query = query) }

    /** The query was submitted or led to a book: keep it among the recent searches. */
    fun onRememberSearch(query: String) {
        viewModelScope.launch { recentSearches.remember(query) }
    }

    fun onForgetSearch(query: String) {
        viewModelScope.launch { recentSearches.forget(query) }
    }

    fun onSortModeChange(mode: SortMode) = persist { libraryPrefs.setSortMode(mode) }
    fun onViewModeChange(mode: LibraryViewMode) = persist { libraryPrefs.setViewMode(mode) }
    fun onPortraitGridColumnsChange(columns: Int) = persist { libraryPrefs.setPortraitGridColumns(columns) }
    fun onLandscapeGridColumnsChange(columns: Int) = persist { libraryPrefs.setLandscapeGridColumns(columns) }
    fun onCoverCropChange(crop: Boolean) = persist { libraryPrefs.setCoverCrop(crop) }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun project(books: List<Book>): IdentityHashMap<Book, LibraryBook> {
        val previous = projections
        val next = IdentityHashMap<Book, LibraryBook>(books.size)
        books.forEach { book -> next[book] = previous[book] ?: book.toLibraryBook() }
        projections = next
        return next
    }

    private fun buildState(
        catalog: Catalog,
        prefs: LibraryPreferences,
        inputs: LibraryInputs,
        recent: List<String>,
    ): LibraryUiState {
        val entries = catalog.entries
        val projected = project(entries.map { it.book })
        val search = inputs.search
        val selection = inputs.selection
        // Surface "Continue reading" only when not searching, so the hero doesn't fight the query.
        val query = if (search.active) search.query else ""
        val continueBook = if (query.isBlank()) {
            entries.asSequence().map { it.book }.filter { it.lastOpenedAt > 0L }.maxByOrNull { it.lastOpenedAt }
        } else {
            null
        }
        val pile = if (query.isBlank()) {
            entries.map { it.book }.pileBooks().map { projected.getValue(it) }
        } else {
            emptyList()
        }
        val tabs = catalog.tabs(prefs.tabOrder, prefs.hiddenTabs, inputs.tab)
        val searched = entries.matching(query).sortedBy(prefs.sortMode)
        val pages = tabs.visible.map { tab ->
            LibraryPage(tab.key, searched.mapNotNull { entry -> projected.getValue(entry.book).takeIf { entry.belongsTo(tab.key) } })
        }
        val current = pages.first { it.key == tabs.selected }
        return LibraryUiState(
            isLoading = false,
            totalCount = entries.size,
            selectableCount = current.bookCount,
            continueReading = continueBook?.let { projected.getValue(it) },
            pile = pile,
            books = current.books,
            sortMode = prefs.sortMode,
            viewMode = prefs.viewMode,
            portraitGridColumns = prefs.portraitGridColumns,
            landscapeGridColumns = prefs.landscapeGridColumns,
            searchActive = search.active,
            searchQuery = search.query,
            isImporting = inputs.import.isImporting,
            importingCount = inputs.import.count,
            recentSearches = recent,
            importNotice = inputs.import.notice,
            canRetryImport = inputs.import.retryUris.isNotEmpty(),
            selectionMode = selection.active,
            selectedIds = selection.ids,
            tabs = tabs.visible,
            selectedTab = tabs.selected,
            pages = pages,
            allTabs = tabs.all,
            hiddenTabs = prefs.hiddenTabs,
            organize = catalog.organize(selection.ids.map { BookId(it) }),
        )
    }
}

private fun ImportSummary.toNotice(): String = buildList {
    if (imported > 0) add("${books(imported)} added")
    if (duplicates > 0) add("$duplicates already in your library")
    if (failed > 0) add("$failed couldn't be opened")
}.joinToString(" · ").ifEmpty { "Nothing imported" }

private fun books(count: Int) = if (count == 1) "1 book" else "$count books"

private const val IMPORT_NOTICE_DURATION_MS = 3_500L

// Long enough for the first library frames (and the splash hand-off) to finish first.
private const val PRELOAD_DELAY_MS = 800L
