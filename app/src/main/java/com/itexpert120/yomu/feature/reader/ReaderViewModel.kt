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

    // A TOC jump passes the target location here; otherwise we resume the saved position.
    private val locatorOverride: String? = savedStateHandle["locator"]

    // The resource being read; a chapter is marked read only once the reader leaves it for another.
    private var currentHref: String? = null
    private val markedChapters = mutableSetOf<String>()

    private val _session = MutableStateFlow<ReaderSession?>(null)
    val session: StateFlow<ReaderSession?> = _session.asStateFlow()

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val target = repository.readingTarget(BookId(bookId))
            val opened = target?.let { engine.open(it.storagePath, locatorOverride ?: it.locatorJson) }
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
                        // Mark a chapter read only when the reader moves on from it (A -> B), not
                        // the moment it's opened. Write once per chapter to avoid churn.
                        val href = locator.href
                        if (href != currentHref) {
                            val left = currentHref
                            if (left != null && markedChapters.add(left)) {
                                repository.setChaptersRead(BookId(bookId), listOf(left), read = true)
                            }
                            currentHref = href
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
