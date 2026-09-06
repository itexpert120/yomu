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
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class EditBookUiState(
    val loaded: Boolean = false,
    val available: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val author: String = "",
    val description: String = "",
    val coverImagePath: String? = null,
) {
    val editable: Boolean get() = loaded && available && !busy && !saved
}

@HiltViewModel
class EditBookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: BookRepository,
    private val fileStorage: FileStorage,
    @ApplicationScope applicationScope: CoroutineScope,
) : ViewModel() {
    private val bookId: String = requireNotNull(savedStateHandle["bookId"])
    private val editor = BookEditor(
        viewModelScope,
        applicationScope,
        load = { repository.observeBook(BookId(bookId)).first() },
        commit = { s ->
            repository.updateMetadata(
                BookId(bookId),
                s.title.ifBlank { "Untitled" },
                s.subtitle.ifBlank { null },
                s.author.ifBlank { "Unknown author" },
                s.description.ifBlank { null },
                s.coverImagePath,
            )
        },
        deleteCover = fileStorage::deleteCover,
    )
    val state = editor.state
    fun onTitleChange(value: String) = editor.edit { it.copy(title = value) }
    fun onSubtitleChange(value: String) = editor.edit { it.copy(subtitle = value) }
    fun onAuthorChange(value: String) = editor.edit { it.copy(author = value) }
    fun onDescriptionChange(value: String) = editor.edit { it.copy(description = value) }
    fun onCoverPicked(uri: Uri, stamp: Long) {
        editor.pickCover { fileStorage.saveCoverFromUri(bookId, uri, stamp) }
    }
    fun save() {
        editor.save()
    }
    override fun onCleared() {
        editor.close()
    }
}
