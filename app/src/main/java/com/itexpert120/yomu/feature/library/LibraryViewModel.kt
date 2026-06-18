package com.itexpert120.yomu.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Transient search state — intentionally not persisted across launches. */
private data class SearchState(val active: Boolean = false, val query: String = "")

class LibraryViewModel(
    private val repository: BookRepository,
    private val libraryPrefs: LibraryPrefsRepository,
) : ViewModel() {

    private val search = MutableStateFlow(SearchState())

    val state: StateFlow<LibraryUiState> =
        combine(repository.observeBooks(), libraryPrefs.preferences, search) { books, prefs, s ->
            buildState(books, prefs, s)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onSearchToggle() = search.update {
        if (it.active) SearchState() else it.copy(active = true)
    }

    fun onSearchQueryChange(query: String) = search.update { it.copy(query = query) }

    fun onSortModeChange(mode: SortMode) = persist { libraryPrefs.setSortMode(mode) }
    fun onGroupModeChange(mode: GroupMode) = persist { libraryPrefs.setGroupMode(mode) }
    fun onViewModeChange(mode: LibraryViewMode) = persist { libraryPrefs.setViewMode(mode) }
    fun onGridColumnsChange(columns: Int) = persist { libraryPrefs.setGridColumns(columns) }
    fun onCoverCropChange(crop: Boolean) = persist { libraryPrefs.setCoverCrop(crop) }

    fun onMarkRead(bookId: String) {
        viewModelScope.launch { repository.markRead(BookId(bookId)) }
    }

    fun onRemove(bookId: String) {
        viewModelScope.launch { repository.remove(BookId(bookId)) }
    }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun buildState(
        books: List<Book>,
        prefs: LibraryPreferences,
        search: SearchState,
    ): LibraryUiState {
        // Surface "Continue reading" only when not searching, so the hero doesn't fight the query.
        val query = if (search.active) search.query else ""
        val continueBook = if (query.isBlank()) {
            books.filter { it.lastOpenedAt > 0L }.maxByOrNull { it.lastOpenedAt }
        } else {
            null
        }
        val gridBooks = books.filterNot { it.id == continueBook?.id }
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
        )
    }
}
