package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.data.books.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val title: String = "",
    val chromeVisible: Boolean = true,
    val progressPercent: Int? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: ReaderEngine,
    private val repository: BookRepository,
) : ViewModel() {

    private val bookId: String = requireNotNull(savedStateHandle["bookId"])

    private val _session = MutableStateFlow<ReaderSession?>(null)
    val session: StateFlow<ReaderSession?> = _session.asStateFlow()

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val target = repository.readingTarget(BookId(bookId))
            val opened = target?.let { engine.open(it.storagePath, it.locatorJson) }
            if (opened == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _session.value = opened
            _state.update { it.copy(loading = false, title = opened.title) }

            launch {
                opened.currentLocator.collect { locator ->
                    if (locator != null) {
                        val progression = locator.totalProgression
                        _state.update { it.copy(progressPercent = progression?.let { p -> (p * 100).toInt() }) }
                        if (progression != null) {
                            repository.saveProgress(BookId(bookId), locator.locatorJson, progression)
                        }
                    }
                }
            }
            launch {
                opened.tapEvents.collect {
                    _state.update { it.copy(chromeVisible = !it.chromeVisible) }
                }
            }
        }
    }

    override fun onCleared() {
        _session.value?.close()
    }
}
