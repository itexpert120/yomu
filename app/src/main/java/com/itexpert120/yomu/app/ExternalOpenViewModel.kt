package com.itexpert120.yomu.app

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.domain.imports.ImportBooksUseCase
import com.itexpert120.yomu.domain.imports.ImportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Handles EPUBs opened from outside the app (file-manager "Open with", or a WhatsApp/Gmail share).
 * The Activity feeds the incoming [Uri] here; the import runs off the main thread (in the use case)
 * and acknowledged pending requests drive navigation to the imported (or already-present) book.
 */
@HiltViewModel
class ExternalOpenViewModel @Inject constructor(
    private val importBooks: ImportBooksUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val opens = PendingBookOpens(savedState["pendingBooks"] ?: emptyList()) {
        savedState["pendingBooks"] = it
    }
    val pendingBooks = opens.state
    fun acknowledgeOpen(bookId: String) = opens.acknowledge(bookId)
    private val importMutex = Mutex()

    init {
        savedState.get<List<String>>("incomingUris").orEmpty().forEach { importUri(Uri.parse(it)) }
    }

    /** Called by the Activity for both cold-start (onCreate) and warm (onNewIntent) external opens. */
    fun onExternalUri(uri: Uri) {
        savedState["incomingUris"] = savedState.get<List<String>>("incomingUris").orEmpty() + uri.toString()
        importUri(uri)
    }

    private fun importUri(uri: Uri) {
        viewModelScope.launch {
            importMutex.withLock {
                _isImporting.value = true
                _error.value = null
                var completed = false
                try {
                    when (val result = importBooks.importSingle(uri)) {
                        is ImportResult.Imported -> opens.add(result.bookId)
                        is ImportResult.Duplicate -> opens.add(result.bookId)
                        is ImportResult.Failed -> _error.value = result.reason
                    }
                    completed = true
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _error.value = "The EPUB couldn't be opened."
                    completed = true
                } finally {
                    if (completed) savedState["incomingUris"] = savedState.get<List<String>>("incomingUris").orEmpty().drop(1)
                    _isImporting.value = false
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
