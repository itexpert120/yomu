package com.itexpert120.yomu.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.dictionary.DictionaryRepository
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
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
    // Chrome (top bar) visibility. Starts visible, toggled by a center tap, hidden on scroll/reading.
    val chromeVisible: Boolean = true,
    val sheetVisible: Boolean = false,
    val customThemes: List<CustomReaderTheme> = emptyList(),
    val customSheetVisible: Boolean = false,
    // Chapter-boundary state, for the next/previous-chapter buttons.
    val chapterProgression: Double = 0.0,
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    // In-reader table of contents.
    val toc: List<ReaderTocItem> = emptyList(),
    val tocSheetVisible: Boolean = false,
    val currentHref: String? = null,
    // Word lookup: the active lookup sheet (null = closed). Triggered from the native "Look up"
    // text-selection menu item.
    val lookup: WordLookupUiState? = null,
    // Footnote popup content (HTML), or null when closed. Shown when a footnote ref is tapped.
    val footnoteHtml: String? = null,
)

/** State of the word-definition popup. */
data class WordLookupUiState(
    val word: String,
    val loading: Boolean = true,
    val result: DictionaryResult? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: ReaderEngine,
    private val repository: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val dictionary: DictionaryRepository,
    private val stats: StatsRepository,
) : ViewModel() {

    // Wall-clock start of the current foreground reading stretch, or null when paused/closed.
    private var readingStart: Long? = null

    private val bookId: String = requireNotNull(savedStateHandle["bookId"])
    private val locatorOverride: String? = savedStateHandle["locator"]

    // The resource being read; a chapter is marked read only once the reader leaves it for another.
    private var currentHref: String? = null
    private val markedChapters = mutableSetOf<String>()

    // Last progression seen for chrome auto-hide; lets us hide the top bar once the reader scrolls.
    private var lastChromeProgression: Double? = null

    // Resource href -> chapter title, so the top bar can show the current chapter name even when
    // the engine locator carries no title. Retains the last known title to avoid blanking out.
    private var tocTitles: Map<String, String> = emptyMap()
    private var lastChapterTitle: String? = null

    private val _session = MutableStateFlow<ReaderSession?>(null)
    val session: StateFlow<ReaderSession?> = _session.asStateFlow()

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        // App-global saved custom themes, independent of the reading session.
        viewModelScope.launch {
            settingsRepository.customThemes.collect { themes ->
                _state.update { it.copy(customThemes = themes) }
            }
        }
        viewModelScope.launch {
            val target = repository.readingTarget(BookId(bookId))
            val initialSettings =
                target?.let { settingsRepository.effective(BookId(bookId)).first() }
                    ?: ReaderSettings()
            _state.update { it.copy(settings = initialSettings) }
            val opened = target?.let {
                engine.open(
                    filePath = it.storagePath,
                    initialLocatorJson = locatorOverride ?: it.locatorJson,
                    initialSettings = initialSettings,
                )
            }
            if (opened == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _session.value = opened
            _state.update { it.copy(loading = false, title = opened.title) }
            // If timing already started during the loading spinner, re-arm from now so only actual
            // reading time (post-open) is counted.
            if (readingStart != null) readingStart = System.currentTimeMillis()

            // Build href -> chapter-title lookup (first entry per resource wins). Served from the
            // persistent TOC cache so it's instant on subsequent opens.
            launch {
                val items =
                    withContext(Dispatchers.IO) { repository.tableOfContents(BookId(bookId)) }
                val map = LinkedHashMap<String, String>()
                items.forEach { map.putIfAbsent(it.id, it.title) }
                tocTitles = map
                _state.update { it.copy(toc = items) }
            }

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
                        // Prefer the TOC chapter title for the current resource; fall back to the
                        // engine's locator title, then the last known one (never the book name).
                        val resolved = locator.href?.let { tocTitles[it] } ?: locator.chapterTitle
                        if (!resolved.isNullOrBlank()) lastChapterTitle = resolved
                        _state.update {
                            it.copy(
                                chapterTitle = lastChapterTitle,
                                totalProgression = progression ?: it.totalProgression,
                                progressPercent = progression?.let { p -> (p * 100).toInt() },
                                chapterProgression = locator.chapterProgression
                                    ?: it.chapterProgression,
                                hasPreviousChapter = locator.hasPreviousChapter,
                                hasNextChapter = locator.hasNextChapter,
                                currentHref = locator.href,
                            )
                        }
                        if (progression != null) {
                            // Hide the chrome once the reader actually moves (scroll/page turn). The
                            // first emission only seeds the baseline so chrome stays up on open.
                            val last = lastChromeProgression
                            if (last != null &&
                                _state.value.chromeVisible &&
                                kotlin.math.abs(progression - last) > 0.0005
                            ) {
                                _state.update { it.copy(chromeVisible = false) }
                            }
                            lastChromeProgression = progression
                            repository.saveProgress(
                                BookId(bookId),
                                locator.locatorJson,
                                progression
                            )
                        }
                        val href = locator.href
                        if (href != currentHref) {
                            val left = currentHref
                            if (left != null && markedChapters.add(left)) {
                                repository.setChaptersRead(
                                    BookId(bookId),
                                    listOf(left),
                                    read = true
                                )
                            }
                            currentHref = href
                        }
                    }
                }
            }
            launch {
                // A center tap reveals/hides the chrome instead of opening the sheet directly; the
                // controls sheet is reached from the revealed top bar's controls button.
                opened.centerTaps.collect { _state.update { it.copy(chromeVisible = !it.chromeVisible) } }
            }
            launch {
                opened.lookUpRequests.collect { text -> lookUp(text) }
            }
            launch {
                opened.footnotes.collect { html -> _state.update { it.copy(footnoteHtml = html) } }
            }
            launch {
                repository.observeBook(BookId(bookId)).collect { book ->
                    _state.update { it.copy(coverImagePath = book?.coverImagePath) }
                }
            }
        }
    }

    fun onOpenSheet() = _state.update { it.copy(sheetVisible = true) }
    fun onCloseSheet() = _state.update { it.copy(sheetVisible = false) }

    fun onSeek(totalProgression: Double) {
        _session.value?.goToProgression(totalProgression)
    }

    fun onNextChapter() = _session.value?.nextChapter() ?: Unit
    fun onPreviousChapter() = _session.value?.previousChapter() ?: Unit

    fun onOpenToc() = _state.update { it.copy(tocSheetVisible = true, sheetVisible = false) }
    fun onCloseToc() = _state.update { it.copy(tocSheetVisible = false) }

    /** Jump to a TOC entry and close the contents sheet. */
    fun onJumpToLocator(locatorJson: String) {
        _session.value?.goToLocator(locatorJson)
        _state.update { it.copy(tocSheetVisible = false) }
    }

    /** Reader edits write this book's per-book override; global defaults live in Settings. */
    fun onUpdateSettings(settings: ReaderSettings) {
        viewModelScope.launch { settingsRepository.setForBook(BookId(bookId), settings) }
    }

    /** Drops this book's override so it follows the global Reading Defaults again. */
    fun onResetBookSettings() {
        viewModelScope.launch { settingsRepository.clearForBook(BookId(bookId)) }
    }

    fun onOpenCustomTheme() =
        _state.update { it.copy(customSheetVisible = true, sheetVisible = false) }

    fun onCloseCustomTheme() = _state.update { it.copy(customSheetVisible = false) }

    /** Loads a saved custom palette into the current settings (and switches to Custom). */
    fun onApplyCustomTheme(theme: CustomReaderTheme) {
        onUpdateSettings(
            state.value.settings.copy(
                theme = ReaderThemeMode.Custom,
                customBackground = theme.background,
                customText = theme.text,
            ),
        )
    }

    /** Saves the current custom colours as a named, reusable theme. */
    fun onSaveCustomTheme(name: String) {
        val s = state.value.settings
        val theme = CustomReaderTheme(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Custom" },
            background = s.backgroundArgb,
            text = s.textArgb,
        )
        viewModelScope.launch { settingsRepository.saveCustomTheme(theme) }
    }

    fun onDeleteCustomTheme(id: String) {
        viewModelScope.launch { settingsRepository.deleteCustomTheme(id) }
    }

    /** Looks up the selected text's first word in the dictionary and opens the result popup. */
    private fun lookUp(rawText: String) {
        val word = sanitizeWord(rawText) ?: return
        _state.update { it.copy(lookup = WordLookupUiState(word = word, loading = true)) }
        viewModelScope.launch {
            val result = dictionary.lookup(word)
            _state.update { st ->
                val current = st.lookup ?: return@update st
                // Ignore a stale response if the user has since looked up a different word.
                if (current.word != word) return@update st
                st.copy(lookup = current.copy(loading = false, result = result))
            }
        }
    }

    fun onCloseLookup() = _state.update { it.copy(lookup = null) }

    fun onCloseFootnote() = _state.update { it.copy(footnoteHtml = null) }

    /** Start counting reading time (reader brought to the foreground). */
    fun onReadingResumed() {
        if (!state.value.failed) readingStart = System.currentTimeMillis()
    }

    /** Stop counting and bank the elapsed foreground time toward today's stats. */
    fun onReadingPaused() {
        val start = readingStart ?: return
        readingStart = null
        // Don't count time spent on a spinner / failed open.
        if (state.value.failed || _session.value == null) return
        val seconds = (System.currentTimeMillis() - start) / 1000L
        // Guard against clock changes / absurd spans.
        if (seconds in 1..MAX_SESSION_SECONDS) {
            viewModelScope.launch { stats.recordSession(BookId(bookId), start, seconds) }
        }
    }

    /** Reduces a selection to a single, punctuation-free word the dictionary API can resolve. */
    private fun sanitizeWord(raw: String): String? =
        raw.trim()
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.lowercase()
            ?.filter { it.isLetter() || it == '-' || it == '\'' }
            ?.takeIf { it.isNotBlank() }

    override fun onCleared() {
        onReadingPaused()
        _session.value?.close()
    }

    private companion object {
        const val MAX_SESSION_SECONDS = 24L * 60 * 60
    }
}
