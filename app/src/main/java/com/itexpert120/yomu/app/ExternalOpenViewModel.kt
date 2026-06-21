package com.itexpert120.yomu.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.domain.imports.ImportBooksUseCase
import com.itexpert120.yomu.domain.imports.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles EPUBs opened from outside the app (file-manager "Open with", or a WhatsApp/Gmail share).
 * The Activity feeds the incoming [Uri] here; the import runs off the main thread (in the use case)
 * and a one-shot [openBook] event drives navigation to the imported (or already-present) book.
 */
@HiltViewModel
class ExternalOpenViewModel @Inject constructor(
    private val importBooks: ImportBooksUseCase,
) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Buffered so an open emitted before the nav host starts collecting is not dropped.
    private val _openBook = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val openBook: SharedFlow<String> = _openBook.asSharedFlow()

    /** Called by the Activity for both cold-start (onCreate) and warm (onNewIntent) external opens. */
    fun onExternalUri(uri: Uri) {
        viewModelScope.launch {
            _isImporting.update { true }
            val result = importBooks.importSingle(uri)
            val bookId = when (result) {
                is ImportResult.Imported -> result.bookId
                is ImportResult.Duplicate -> result.bookId
                ImportResult.Failed -> null
            }
            _isImporting.update { false }
            if (bookId != null) _openBook.emit(bookId)
        }
    }
}
