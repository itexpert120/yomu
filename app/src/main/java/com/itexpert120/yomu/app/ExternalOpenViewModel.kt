package com.itexpert120.yomu.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.domain.imports.ImportBooksUseCase
import com.itexpert120.yomu.domain.imports.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Buffered so an open emitted before the nav host starts collecting is not dropped.
    private val openBookChannel = Channel<String>(Channel.BUFFERED)
    val openBook = openBookChannel.receiveAsFlow()
    private val importMutex = Mutex()

    /** Called by the Activity for both cold-start (onCreate) and warm (onNewIntent) external opens. */
    fun onExternalUri(uri: Uri) {
        viewModelScope.launch {
            importMutex.withLock {
                _isImporting.value = true
                _error.value = null
                try {
                    when (val result = importBooks.importSingle(uri)) {
                        is ImportResult.Imported -> openBookChannel.send(result.bookId)
                        is ImportResult.Duplicate -> openBookChannel.send(result.bookId)
                        is ImportResult.Failed -> _error.value = result.reason
                    }
                } catch (_: Throwable) {
                    _error.value = "The EPUB couldn't be opened."
                } finally {
                    _isImporting.value = false
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
