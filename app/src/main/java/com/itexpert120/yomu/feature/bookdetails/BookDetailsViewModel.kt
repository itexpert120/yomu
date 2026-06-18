package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReadingState
import com.itexpert120.yomu.data.books.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
) : ViewModel() {

    // "bookId" is the property name from the type-safe BookDetails route.
    private val bookId: String = requireNotNull(savedStateHandle["bookId"])

    val state: StateFlow<BookDetailsUi?> =
        repository.observeBook(BookId(bookId))
            .map { it?.toUi() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markRead() {
        viewModelScope.launch { repository.markRead(BookId(bookId)) }
    }

    fun markUnread() {
        viewModelScope.launch { repository.markUnread(BookId(bookId)) }
    }

    fun remove() {
        viewModelScope.launch { repository.remove(listOf(BookId(bookId))) }
    }
}

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
