package com.itexpert120.yomu.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import android.net.Uri
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import com.itexpert120.yomu.domain.imports.ImportBooksUseCase
import com.itexpert120.yomu.domain.imports.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient search state — intentionally not persisted across launches. */
private data class SearchState(val active: Boolean = false, val query: String = "")

/** Transient import progress/result, surfaced as an inline notice. */
private data class ImportState(val isImporting: Boolean = false, val notice: String? = null)

/** Transient multi-select state. */
private data class SelectionState(val active: Boolean = false, val ids: Set<String> = emptySet())

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    private val libraryPrefs: LibraryPrefsRepository,
    private val importBooks: ImportBooksUseCase,
) : ViewModel() {

    private val search = MutableStateFlow(SearchState())
    private val importState = MutableStateFlow(ImportState())
    private val selection = MutableStateFlow(SelectionState())

    val state: StateFlow<LibraryUiState> =
        combine(
            repository.observeBooks(),
            libraryPrefs.preferences,
            search,
            importState,
            selection,
        ) { books, prefs, s, imp, sel ->
            buildState(books, prefs, s, imp, sel)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onEnterSelection(bookId: String) {
        selection.value = SelectionState(active = true, ids = setOf(bookId))
    }

    // Stays in selection mode even when the set empties; the user exits explicitly.
    fun onToggleSelect(bookId: String) = selection.update { current ->
        val ids = if (bookId in current.ids) current.ids - bookId else current.ids + bookId
        current.copy(active = true, ids = ids)
    }

    fun onExitSelection() {
        selection.value = SelectionState()
    }

    fun onSelectAll() {
        selection.value = SelectionState(active = true, ids = visibleBookIds())
    }

    fun onDeselectAll() = selection.update { it.copy(active = true, ids = emptySet()) }

    fun onInvertSelection() {
        val all = visibleBookIds()
        selection.update { it.copy(active = true, ids = all - it.ids) }
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
        viewModelScope.launch { ids.forEach { repository.markRead(it) } }
    }

    fun onMarkSelectedUnread() {
        val ids = selection.value.ids.map { BookId(it) }
        viewModelScope.launch { ids.forEach { repository.markUnread(it) } }
    }

    private fun visibleBookIds(): Set<String> = buildSet {
        state.value.continueReading?.id?.let { add(it) }
        state.value.groups.forEach { group -> group.books.forEach { add(it.id) } }
    }

    fun onImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importState.update { it.copy(isImporting = true, notice = null) }
            val summary = importBooks.import(uris)
            importState.value = ImportState(isImporting = false, notice = summary.toNotice())
            delay(4_000)
            importState.update { if (it.isImporting) it else it.copy(notice = null) }
        }
    }

    fun onSearchToggle() = search.update {
        if (it.active) SearchState() else it.copy(active = true)
    }

    fun onSearchQueryChange(query: String) = search.update { it.copy(query = query) }

    fun onSortModeChange(mode: SortMode) = persist { libraryPrefs.setSortMode(mode) }
    fun onGroupModeChange(mode: GroupMode) = persist { libraryPrefs.setGroupMode(mode) }
    fun onViewModeChange(mode: LibraryViewMode) = persist { libraryPrefs.setViewMode(mode) }
    fun onGridColumnsChange(columns: Int) = persist { libraryPrefs.setGridColumns(columns) }
    fun onCoverCropChange(crop: Boolean) = persist { libraryPrefs.setCoverCrop(crop) }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun buildState(
        books: List<Book>,
        prefs: LibraryPreferences,
        search: SearchState,
        import: ImportState,
        selection: SelectionState,
    ): LibraryUiState {
        // Surface "Continue reading" only when not searching, so the hero doesn't fight the query.
        val query = if (search.active) search.query else ""
        val continueBook = if (query.isBlank()) {
            books.filter { it.lastOpenedAt > 0L }.maxByOrNull { it.lastOpenedAt }
        } else {
            null
        }
        // The continue-reading book stays in the grid too, so it's openable from there as well.
        val gridBooks = books
            .matching(query)
            .sortedBy(prefs.sortMode)
        return LibraryUiState(
            isLoading = false,
            totalCount = books.size,
            continueReading = continueBook?.toLibraryBook(),
            groups = gridBooks.groupedBy(prefs.groupMode),
            sortMode = prefs.sortMode,
            groupMode = prefs.groupMode,
            viewMode = prefs.viewMode,
            gridColumns = prefs.gridColumns,
            coverCrop = prefs.coverCrop,
            searchActive = search.active,
            searchQuery = search.query,
            isImporting = import.isImporting,
            importNotice = import.notice,
            selectionMode = selection.active,
            selectedIds = selection.ids,
        )
    }
}

private fun ImportSummary.toNotice(): String = buildList {
    if (imported > 0) add("Imported $imported")
    if (duplicates > 0) add("$duplicates duplicate${if (duplicates == 1) "" else "s"}")
    if (failed > 0) add("$failed failed")
}.joinToString(" · ").ifEmpty { "Nothing imported" }
