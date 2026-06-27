package com.itexpert120.yomu.feature.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.model.ReaderThemeMode
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderHighlightDraft
import com.itexpert120.yomu.core.reader.ReaderLocator
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.bookmarks.BookmarkRepository
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.dictionary.DictionaryRepository
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.highlights.HighlightRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
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
    // The bottom chapter-controls bar, toggled by a center tap. The top bar stays static.
    val chapterControlsVisible: Boolean = false,
    val sheetVisible: Boolean = false,
    val customThemes: List<CustomReaderTheme> = emptyList(),
    val customSheetVisible: Boolean = false,
    // The bottom bar's "More" overflow sheet.
    val moreSheetVisible: Boolean = false,
    // Chapter-boundary state, for the next/previous-chapter buttons.
    val chapterProgression: Double = 0.0,
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    // In-reader table of contents.
    val toc: List<ReaderTocItem> = emptyList(),
    val tocLoading: Boolean = true,
    val currentHref: String? = null,
    // The consolidated Browse sheet (Contents/Bookmarks/Highlights/Search); null = closed.
    val browseTab: BrowseTab? = null,
    // Word lookup: the active lookup sheet (null = closed). Triggered from the native "Look up"
    // text-selection menu item.
    val lookup: WordLookupUiState? = null,
    // Footnote popup content (HTML), or null when closed. Shown when a footnote ref is tapped.
    val footnoteHtml: String? = null,
    // All of this book's highlights (newest first), for the list sheet.
    val highlights: List<ReaderHighlight> = emptyList(),
    // An existing highlight the user tapped, shown in an edit/delete popup, or null.
    val editingHighlight: ReaderHighlight? = null,
    // Reading-position bookmarks for this book, and whether the current page is bookmarked.
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val currentPageBookmarked: Boolean = false,
    // In-book search (shown in the Browse > Search tab): query text, results, progress/started flags.
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val searchInProgress: Boolean = false,
    // True once a search has been run, so the UI can show "No results" vs. nothing yet.
    val searchPerformed: Boolean = false,
)

/** State of the word-definition popup. [canGoBack] is true when a deeper word has been looked up
 *  from within the sheet, so the UI can offer a back step through the lookup history. */
data class WordLookupUiState(
    val word: String,
    val loading: Boolean = true,
    val result: DictionaryResult? = null,
    val canGoBack: Boolean = false,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val engine: ReaderEngine,
    private val repository: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val dictionary: DictionaryRepository,
    private val stats: StatsRepository,
    private val highlights: HighlightRepository,
    private val bookmarks: BookmarkRepository,
) : ViewModel() {

    // Wall-clock start of the current foreground reading stretch, or null when paused/closed.
    private var readingStart: Long? = null

    // Latest reading position, captured for bookmark add/toggle (saved & restored via the locator).
    private var lastLocator: ReaderLocator? = null

    // The in-flight search coroutine, cancelled when a new query starts or search closes.
    private var searchJob: Job? = null

    private val bookId: String = requireNotNull(savedStateHandle["bookId"])
    private val locatorOverride: String? = savedStateHandle["locator"]

    // The resource being read; a chapter is marked read only once the reader leaves it for another.
    private var currentHref: String? = null
    private val markedChapters = mutableSetOf<String>()

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
            // Prime the chapter-title lookup from the in-memory TOC cache (present if this book was
            // opened earlier this session) so the top bar shows the correct chapter title on the very
            // first frame instead of updating a moment after the reader opens.
            repository.cachedTableOfContents(BookId(bookId))?.let { cached ->
                val map = LinkedHashMap<String, String>()
                cached.forEach { map.putIfAbsent(it.id, it.title) }
                tocTitles = map
            }
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
            _state.update { it.copy(title = opened.title) }
            // Keep "Opening…" up until the navigator paints its first page (or an 8s fallback),
            // instead of dropping it the instant the session is created.
            launch {
                withTimeoutOrNull(8_000) { opened.ready.first { it } }
                _state.update { it.copy(loading = false) }
            }
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
                // If the locator already emitted before the TOC finished loading (first-ever open),
                // correct the chapter title now instead of waiting for the next page change.
                val resolved = currentHref?.let { map[it] }
                if (!resolved.isNullOrBlank()) lastChapterTitle = resolved
                _state.update {
                    it.copy(
                        toc = items,
                        tocLoading = false,
                        chapterTitle = lastChapterTitle ?: it.chapterTitle,
                    )
                }
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
                        lastLocator = locator
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
                                currentPageBookmarked = isCurrentBookmarked(it.bookmarks),
                            )
                        }
                        if (progression != null) {
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
                // A center tap toggles the bottom chapter-controls bar (TOC / chapter nav / scroll /
                // settings). The top bar stays static.
                opened.centerTaps.collect {
                    _state.update { it.copy(chapterControlsVisible = !it.chapterControlsVisible) }
                }
            }
            launch {
                opened.lookUpRequests.collect { text -> lookUp(text) }
            }
            launch {
                opened.footnotes.collect { html -> _state.update { it.copy(footnoteHtml = html) } }
            }
            // A "Highlight" tap on a selection: create the highlight in the default colour and keep
            // reading. Choosing a colour is optional — tap an existing highlight to recolour it.
            launch {
                opened.highlightRequests.collect { draft ->
                    highlights.add(
                        BookId(bookId),
                        draft.locatorJson,
                        draft.text,
                        DEFAULT_HIGHLIGHT_ARGB,
                    )
                }
            }
            // A tap on an on-page highlight: open its edit/delete popup.
            launch {
                opened.highlightTaps.collect { id ->
                    val target = _state.value.highlights.firstOrNull { it.id == id }
                    if (target != null) _state.update { it.copy(editingHighlight = target) }
                }
            }
            // Observe this book's highlights: keep the list state and the on-page decorations in sync.
            launch {
                highlights.observeForBook(BookId(bookId)).collect { list ->
                    _state.update { it.copy(highlights = list) }
                    opened.applyHighlights(list)
                }
            }
            // Observe this book's bookmarks: keep the list and the current-page flag in sync.
            launch {
                bookmarks.observeForBook(BookId(bookId)).collect { list ->
                    _state.update {
                        it.copy(bookmarks = list, currentPageBookmarked = isCurrentBookmarked(list))
                    }
                }
            }
            launch {
                repository.observeBook(BookId(bookId)).collect { book ->
                    _state.update { it.copy(coverImagePath = book?.coverImagePath) }
                }
            }
        }
    }

    fun onOpenSheet() =
        _state.update { it.copy(sheetVisible = true, chapterControlsVisible = false) }

    fun onCloseSheet() = _state.update { it.copy(sheetVisible = false) }

    fun onSeek(totalProgression: Double) {
        _session.value?.goToProgression(totalProgression)
    }

    fun onNextChapter() {
        _session.value?.nextChapter()
        _state.update { it.copy(chapterControlsVisible = false) }
    }

    fun onPreviousChapter() {
        _session.value?.previousChapter()
        _state.update { it.copy(chapterControlsVisible = false) }
    }

    // --- Browse sheet (Contents / Bookmarks / Highlights / Search) ---

    /** Open the Browse sheet on the Contents tab (the bottom bar's "Browse" button). */
    fun onOpenBrowse() = openBrowse(BrowseTab.Contents)

    fun onSelectBrowseTab(tab: BrowseTab) = _state.update { it.copy(browseTab = tab) }

    private fun openBrowse(tab: BrowseTab) = _state.update {
        it.copy(browseTab = tab, sheetVisible = false, chapterControlsVisible = false)
    }

    /** Close the Browse sheet and clear the in-page search underlines + query/results. */
    fun onCloseBrowse() {
        searchJob?.cancel()
        _session.value?.clearSearch()
        _state.update {
            it.copy(
                browseTab = null,
                searchQuery = "",
                searchResults = emptyList(),
                searchInProgress = false,
                searchPerformed = false,
            )
        }
    }

    /** Jump to a TOC entry and close the Browse sheet. */
    fun onJumpToLocator(locatorJson: String) {
        _session.value?.goToLocator(locatorJson)
        _state.update { it.copy(browseTab = null) }
    }

    // --- More sheet (bottom-bar overflow) ---

    fun onOpenMore() = _state.update {
        it.copy(moreSheetVisible = true, chapterControlsVisible = false, sheetVisible = false)
    }
    fun onCloseMore() = _state.update { it.copy(moreSheetVisible = false) }

    fun onChapterStart() {
        _session.value?.scrollToChapterStart()
        _state.update { it.copy(moreSheetVisible = false) }
    }

    fun onChapterEnd() {
        _session.value?.scrollToChapterEnd()
        _state.update { it.copy(moreSheetVisible = false) }
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

    // Lookup history (a back-stack of words) so tapping a word inside a definition drills in and the
    // user can step back. Results are cached per word so back/forward is instant.
    private val lookupStack = ArrayDeque<String>()
    private val lookupCache = mutableMapOf<String, DictionaryResult>()

    // Text-to-speech for the dictionary "pronounce" button. The API provides only IPA text (no audio
    // clips), so the word is spoken with the device voice. Lazily initialised; a word requested
    // before init completes is spoken once the engine is ready.
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingPronounce: String? = null

    /** Entry point from a text selection: opens a fresh lookup (resets any history). */
    private fun lookUp(rawText: String) {
        val word = sanitizeWord(rawText) ?: return
        lookupStack.clear()
        pushLookup(word)
    }

    /** Look up a word tapped from within the dictionary sheet (definitions, synonyms, antonyms). */
    fun onLookUpWord(raw: String) {
        val word = sanitizeWord(raw) ?: return
        if (lookupStack.lastOrNull() == word) return
        pushLookup(word)
    }

    /** Step back to the previously looked-up word, or close the sheet at the bottom of the stack. */
    fun onLookupBack() {
        if (lookupStack.size <= 1) {
            onCloseLookup()
            return
        }
        lookupStack.removeLast()
        showLookup(lookupStack.last())
    }

    private fun pushLookup(word: String) {
        lookupStack.addLast(word)
        showLookup(word)
    }

    /** Render the current top-of-stack word, fetching it if not already cached. */
    private fun showLookup(word: String) {
        val cached = lookupCache[word]
        _state.update {
            it.copy(
                lookup = WordLookupUiState(
                    word = word,
                    loading = cached == null,
                    result = cached,
                    canGoBack = lookupStack.size > 1,
                ),
            )
        }
        if (cached != null) return
        viewModelScope.launch {
            val result = dictionary.lookup(word)
            lookupCache[word] = result
            _state.update { st ->
                val current = st.lookup ?: return@update st
                // Ignore a stale response if the user has since looked up a different word.
                if (current.word != word) return@update st
                st.copy(lookup = current.copy(loading = false, result = result))
            }
        }
    }

    /** Speak [word] aloud with the device text-to-speech voice. */
    fun onPronounce(word: String) {
        val term = word.trim()
        if (term.isEmpty()) return
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(term, TextToSpeech.QUEUE_FLUSH, null, "yomu-pronounce")
            return
        }
        // First use: init is async, so remember the word and speak it once the engine is ready.
        pendingPronounce = term
        if (engine == null) {
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.ENGLISH
                    pendingPronounce?.let {
                        tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "yomu-pronounce")
                    }
                }
                pendingPronounce = null
            }
        }
    }

    fun onCloseLookup() {
        lookupStack.clear()
        _state.update { it.copy(lookup = null) }
    }

    fun onCloseFootnote() = _state.update { it.copy(footnoteHtml = null) }

    // --- Highlights ---

    fun onOpenHighlights() = openBrowse(BrowseTab.Highlights)

    fun onCloseEditHighlight() = _state.update { it.copy(editingHighlight = null) }

    fun onDeleteHighlight() {
        val target = _state.value.editingHighlight ?: return
        _state.update { it.copy(editingHighlight = null) }
        viewModelScope.launch { highlights.delete(target.id) }
    }

    /** Delete a specific highlight (used by the per-row delete in the list). */
    fun onDeleteHighlightById(id: String) {
        viewModelScope.launch { highlights.delete(id) }
    }

    /** Jump to a highlight's stored position from the list, and close the Browse sheet. */
    fun onJumpToHighlight(locatorJson: String) {
        _session.value?.goToLocator(locatorJson)
        _state.update { it.copy(browseTab = null) }
    }

    /** Recolour the highlight currently open in the edit popup. Updates the page decoration too. */
    fun onSetHighlightColor(colorArgb: Int) {
        val target = _state.value.editingHighlight ?: return
        _state.update { it.copy(editingHighlight = target.copy(colorArgb = colorArgb)) }
        viewModelScope.launch { highlights.updateColor(target.id, colorArgb) }
    }

    // --- Bookmarks ---

    /** Whether the current reading position already has a bookmark (href + ~1% progression window). */
    private fun isCurrentBookmarked(list: List<ReaderBookmark>): Boolean {
        val loc = lastLocator ?: return false
        val p = loc.totalProgression ?: 0.0
        return list.any { it.href == loc.href && kotlin.math.abs(it.progression - p) < 0.01 }
    }

    /** Add or remove a bookmark at the current page (the always-visible top-bar toggle). */
    fun onToggleBookmark() {
        val loc = lastLocator ?: return
        val p = loc.totalProgression ?: 0.0
        viewModelScope.launch {
            if (bookmarks.existsAt(BookId(bookId), loc.href, p)) {
                bookmarks.deleteAt(BookId(bookId), loc.href, p)
            } else {
                bookmarks.add(
                    BookId(bookId),
                    loc.locatorJson,
                    loc.href,
                    loc.chapterTitle ?: lastChapterTitle,
                    p,
                )
            }
        }
    }

    fun onOpenBookmarks() = openBrowse(BrowseTab.Bookmarks)

    /** Jump to a bookmark's stored position from the list, and close the Browse sheet. */
    fun onJumpToBookmark(locatorJson: String) {
        _session.value?.goToLocator(locatorJson)
        _state.update { it.copy(browseTab = null) }
    }

    fun onDeleteBookmarkById(id: String) {
        viewModelScope.launch { bookmarks.delete(id) }
    }

    // --- In-book search ---

    fun onOpenSearch() = openBrowse(BrowseTab.Search)

    fun onSearchQueryChange(query: String) = _state.update { it.copy(searchQuery = query) }

    /** Run the current query against the open publication and underline the hits on the page. */
    fun onSubmitSearch() {
        val query = _state.value.searchQuery.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(searchInProgress = true, searchPerformed = true, searchResults = emptyList())
            }
            val results = _session.value?.search(query).orEmpty()
            _state.update { it.copy(searchInProgress = false, searchResults = results) }
            _session.value?.applySearchDecorations(results)
        }
    }

    /** Jump to a search hit; keep the underlines so the hits stay marked while reading. */
    fun onJumpToSearchResult(locatorJson: String) {
        _session.value?.goToLocator(locatorJson)
        _state.update { it.copy(browseTab = null) }
    }

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
        runCatching { tts?.shutdown() }
        tts = null
        _session.value?.close()
    }

    private companion object {
        const val MAX_SESSION_SECONDS = 24L * 60 * 60

        // Single default highlight colour (a warm yellow) — highlights are no longer multi-colour.
        const val DEFAULT_HIGHLIGHT_ARGB = 0xFFE7C75B.toInt()
    }
}
