package com.itexpert120.yomu.feature.library

import android.net.Uri
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookCollection
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.collections.CollectionRepository
import com.itexpert120.yomu.data.reader.ReaderPreloader
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import com.itexpert120.yomu.domain.imports.ImportBooksUseCase
import com.itexpert120.yomu.domain.imports.ImportSummary
import com.itexpert120.yomu.feature.collections.ManagedTab
import com.itexpert120.yomu.feature.collections.OrganizeCollection
import com.itexpert120.yomu.feature.collections.StatusChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.IdentityHashMap
import javax.inject.Inject

/** Transient search state — intentionally not persisted across launches. */
private data class SearchState(val active: Boolean = false, val query: String = "")

/** Transient import progress/result, surfaced as an inline notice. */
private data class ImportState(
    val isImporting: Boolean = false,
    val notice: String? = null,
    val retryUris: List<Uri> = emptyList(),
)

/** Transient multi-select state. */
private data class SelectionState(val active: Boolean = false, val ids: Set<String> = emptySet())

/** Everything the library shows, straight from storage. */
private data class LibraryData(
    val books: List<Book>,
    val collections: List<BookCollection>,
    val memberships: Map<String, Set<String>>,
)

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
    private val collections: CollectionRepository,
    private val libraryPrefs: LibraryPrefsRepository,
    private val importBooks: ImportBooksUseCase,
    private val readerPreloader: ReaderPreloader,
) : ViewModel() {

    private val search = MutableStateFlow(SearchState())
    private val importState = MutableStateFlow(ImportState())
    private val selection = MutableStateFlow(SelectionState())
    private var importNoticeDismissJob: Job? = null

    // The open tab is remembered across launches (device-local). The pending value covers the few
    // milliseconds before DataStore reports the write, so a swipe never snaps back.
    private val pendingTab = MutableStateFlow<String?>(null)
    private val selectedTab = combine(libraryPrefs.preferences, pendingTab) { prefs, pending -> pending ?: prefs.lastTab }

    private val data = combine(
        repository.observeBooks(),
        collections.observeCollections(),
        collections.observeMemberships(),
        ::LibraryData,
    )

    private val inputs = combine(search, importState, selection, selectedTab, ::LibraryInputs)

    // Projections of the current Book instances, reused while the library data is unchanged so a
    // keystroke or selection change hands the grid the same LibraryBook instances (cheap skipping).
    // Only touched from the serial state pipeline below.
    private var projections = IdentityHashMap<Book, LibraryBook>()

    val state: StateFlow<LibraryUiState> =
        combine(data, libraryPrefs.preferences, inputs) { d, prefs, i ->
            buildState(d, prefs, i)
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
        launchSafely {
            val id = collections.create(name)
            if (ids.isNotEmpty()) collections.setMembership(id, ids, member = true)
        }
    }

    fun onRenameCollection(id: String, name: String) = launchSafely { collections.rename(id, name) }

    fun onDeleteCollection(id: String) {
        if (state.value.selectedTab == LibraryTabKeys.collection(id)) onSelectTab(LibraryTabKeys.ALL)
        launchSafely { collections.delete(id) }
    }

    fun onSetTabHidden(key: String, hidden: Boolean) {
        if (!isHideableTab(key)) return
        launchSafely { libraryPrefs.setTabHidden(key, hidden) }
    }

    /** Moves any tab (status or collection) one place; the whole order is then remembered. */
    fun onMoveTab(key: String, up: Boolean) {
        val order = state.value.managedTabs.map { it.key }.toMutableList()
        val from = order.indexOf(key)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in order.indices) return
        order.add(to, order.removeAt(from))
        launchSafely { libraryPrefs.setTabOrder(order) }
    }

    fun onSetSelectedInCollection(collectionId: String, member: Boolean) {
        val ids = selectedBookIds()
        launchSafely { collections.setMembership(collectionId, ids, member) }
    }

    fun onSetSelectedStatus(status: ManualStatus?) {
        val ids = selectedBookIds()
        launchSafely { collections.setStatus(ids, status) }
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
            importState.value = ImportState(isImporting = true)
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

    private fun buildState(data: LibraryData, prefs: LibraryPreferences, inputs: LibraryInputs): LibraryUiState {
        val (books, collectionList, memberships) = data
        val projected = project(books)
        val search = inputs.search
        val selection = inputs.selection
        // Surface "Continue reading" only when not searching, so the hero doesn't fight the query.
        val query = if (search.active) search.query else ""
        val continueBook = if (query.isBlank()) {
            books.filter { it.lastOpenedAt > 0L }.maxByOrNull { it.lastOpenedAt }
        } else {
            null
        }
        // Counts are computed once here and reused for both the strip and the tab editor.
        val allTabs = allLibraryTabs(books, collectionList, memberships, prefs.tabOrder)
        val tabs = visibleLibraryTabs(allTabs, inputs.tab, prefs.hiddenTabs)
        // A deleted collection (here or on a synced device) falls back to All.
        val selectedTab = inputs.tab.takeIf { key -> tabs.any { it.key == key } } ?: LibraryTabKeys.ALL
        val searched = books.matching(query).sortedBy(prefs.sortMode)
        val pages = tabs.map { tab ->
            val tabBooks = searched.mapNotNull { book ->
                projected.getValue(book).takeIf { book.belongsTo(tab.key, memberships[book.id.value].orEmpty()) }
            }
            LibraryPage(tab.key, tabBooks)
        }
        val current = pages.first { it.key == selectedTab }
        val selectedBooks = books.filter { it.id.value in selection.ids }
        return LibraryUiState(
            isLoading = false,
            totalCount = books.size,
            selectableCount = current.bookCount,
            continueReading = continueBook?.let { projected.getValue(it) },
            books = current.books,
            sortMode = prefs.sortMode,
            viewMode = prefs.viewMode,
            portraitGridColumns = prefs.portraitGridColumns,
            landscapeGridColumns = prefs.landscapeGridColumns,
            searchActive = search.active,
            searchQuery = search.query,
            isImporting = inputs.import.isImporting,
            importNotice = inputs.import.notice,
            canRetryImport = inputs.import.retryUris.isNotEmpty(),
            selectionMode = selection.active,
            selectedIds = selection.ids,
            tabs = tabs,
            selectedTab = selectedTab,
            pages = pages,
            managedTabs = allTabs.map { tab ->
                ManagedTab(
                    key = tab.key,
                    label = tab.label,
                    count = tab.count,
                    collectionId = LibraryTabKeys.collectionId(tab.key),
                    showsOnlyWhenFilled = isOptionalTab(tab.key),
                    hideable = isHideableTab(tab.key),
                    hidden = tab.key in prefs.hiddenTabs,
                )
            },
            organizeStatus = StatusChoice.of(selectedBooks.map { it.manualStatus }),
            organizeCollections = collectionList.map { c ->
                val inIt = selectedBooks.count { c.id in memberships[it.id.value].orEmpty() }
                val state = when {
                    inIt == 0 -> ToggleableState.Off
                    inIt == selectedBooks.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                OrganizeCollection(c.id, c.name, state)
            },
        )
    }
}

private fun ImportSummary.toNotice(): String = buildList {
    if (imported > 0) add("Imported $imported")
    if (duplicates > 0) add("$duplicates duplicate${if (duplicates == 1) "" else "s"}")
    if (failed > 0) add("$failed failed")
}.joinToString(" · ").ifEmpty { "Nothing imported" }

private const val IMPORT_NOTICE_DURATION_MS = 3_500L

// Long enough for the first library frames (and the splash hand-off) to finish first.
private const val PRELOAD_DELAY_MS = 800L
