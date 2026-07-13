package com.itexpert120.yomu.feature.bookedit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.storage.FileStorage
import com.itexpert120.yomu.data.books.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditBookUiState(
    val loaded: Boolean = false,
    val saved: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val author: String = "",
    val description: String = "",
    val coverImagePath: String? = null,
)

@HiltViewModel
class EditBookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val fileStorage: FileStorage,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val bookId: String = requireNotNull(savedStateHandle["bookId"])
    private var originalCoverPath: String? = null
    private var pendingCoverPath: String? = null

    private val _state = MutableStateFlow(EditBookUiState())
    val state: StateFlow<EditBookUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val book = repository.observeBook(BookId(bookId)).first()
            _state.value = if (book != null) {
                originalCoverPath = book.coverImagePath
                EditBookUiState(
                    loaded = true,
                    title = book.title,
                    subtitle = book.subtitle.orEmpty(),
                    author = book.author,
                    description = book.description.orEmpty(),
                    coverImagePath = book.coverImagePath,
                )
            } else {
                EditBookUiState(loaded = true)
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }
    fun onSubtitleChange(value: String) = _state.update { it.copy(subtitle = value) }
    fun onAuthorChange(value: String) = _state.update { it.copy(author = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onCoverPicked(uri: Uri, stamp: Long) {
        viewModelScope.launch {
            val path = fileStorage.saveCoverFromUri(bookId, uri, stamp)
            pendingCoverPath?.let { fileStorage.deleteCover(it) }
            pendingCoverPath = path
            _state.update { it.copy(coverImagePath = path) }
        }
    }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            repository.updateMetadata(
                id = BookId(bookId),
                title = s.title.ifBlank { "Untitled" },
                subtitle = s.subtitle.ifBlank { null },
                author = s.author.ifBlank { "Unknown author" },
                description = s.description.ifBlank { null },
                coverImagePath = s.coverImagePath,
            )
            val previous = originalCoverPath
            val current = s.coverImagePath
            if (previous != null && previous != current) fileStorage.deleteCover(previous)
            originalCoverPath = current
            pendingCoverPath = null
            _state.update { it.copy(saved = true) }
        }
    }

    override fun onCleared() {
        pendingCoverPath?.let { path ->
            applicationScope.launch { runCatching { fileStorage.deleteCover(path) } }
        }
        pendingCoverPath = null
    }
}
