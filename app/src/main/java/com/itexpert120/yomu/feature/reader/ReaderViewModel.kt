package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTap
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
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
    val chapterTitle: String? = null,
    val progressPercent: Int? = null,
    val totalProgression: Double = 0.0,
    val coverImagePath: String? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val sheetVisible: Boolean = false,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: ReaderEngine,
    private val repository: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    private val bookId: String = requireNotNull(savedStateHandle["bookId"])
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

            // Resolve effective settings (per-book override or global) and keep them applied live.
            launch {
                settingsRepository.effective(BookId(bookId)).collect { settings ->
                    _state.update { it.copy(settings = settings) }
                    opened.applySettings(settings)
                }
            }
            launch {
                opened.currentLocator.collect { locator ->
                    if (locator != null) {
                        val progression = locator.totalProgression
                        _state.update {
                            it.copy(
                                chapterTitle = locator.chapterTitle,
                                totalProgression = progression ?: it.totalProgression,
                                progressPercent = progression?.let { p -> (p * 100).toInt() },
                            )
                        }
                        if (progression != null) {
                            repository.saveProgress(BookId(bookId), locator.locatorJson, progression)
                        }
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
                opened.tapEvents.collect { tap -> handleTap(tap, opened) }
            }
            launch {
                repository.observeBook(BookId(bookId)).collect { book ->
                    _state.update { it.copy(coverImagePath = book?.coverImagePath) }
                }
            }
        }
    }

    // L-shaped zones: the centre opens the sheet; the top-left L goes back, the bottom-right L
    // goes forward. Identical for scroll and paged (the engine interprets forward/back per mode).
    private fun handleTap(tap: ReaderTap, session: ReaderSession) {
        val inCentre = tap.xFraction in CENTRE_MIN..CENTRE_MAX &&
            tap.yFraction in CENTRE_MIN..CENTRE_MAX
        when {
            inCentre -> _state.update { it.copy(sheetVisible = true) }
            tap.xFraction + tap.yFraction < 1f -> session.goBackward()
            else -> session.goForward()
        }
    }

    fun onOpenSheet() = _state.update { it.copy(sheetVisible = true) }
    fun onCloseSheet() = _state.update { it.copy(sheetVisible = false) }

    fun onSeek(totalProgression: Double) {
        _session.value?.goToProgression(totalProgression)
    }

    fun onNextChapter() = _session.value?.nextChapter() ?: Unit
    fun onPreviousChapter() = _session.value?.previousChapter() ?: Unit

    /** Per-book-on-edit: changing a setting writes this book's override. */
    fun onUpdateSettings(settings: ReaderSettings) {
        viewModelScope.launch { settingsRepository.setForBook(BookId(bookId), settings) }
    }

    override fun onCleared() {
        _session.value?.close()
    }

    private companion object {
        const val CENTRE_MIN = 0.30f
        const val CENTRE_MAX = 0.70f
    }
}
