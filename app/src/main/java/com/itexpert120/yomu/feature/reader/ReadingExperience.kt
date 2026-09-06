package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.BookmarkIdentity
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderLocator
import com.itexpert120.yomu.core.reader.ReaderNavigator
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderOpenTrace
import com.itexpert120.yomu.core.reader.ReaderRenderState
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.bookmarks.BookmarkRepository
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.ReadingProgressSnapshot
import com.itexpert120.yomu.data.dictionary.DictionaryRepository
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.highlights.HighlightRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import com.itexpert120.yomu.data.stats.StatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class WordLookupUiState(
    val word: String,
    val loading: Boolean = true,
    val result: DictionaryResult? = null,
    val canGoBack: Boolean = false,
)

data class ReadingExperienceState(
    val navigator: ReaderNavigator? = null,
    val loading: Boolean = true,
    val renderState: ReaderRenderState = ReaderRenderState.Opening,
    val failed: Boolean = false,
    val title: String = "",
    val chapterTitle: String? = null,
    val coverImagePath: String? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val tableOfContents: List<ReaderTocItem> = emptyList(),
    val tocLoading: Boolean = true,
    val locator: ReaderLocator? = null,
    val progressPercent: Int? = null,
    val totalProgression: Double = 0.0,
    val chapterPagesLeft: Int? = null,
    val chapterProgression: Double = 0.0,
    val hasPreviousChapter: Boolean = false,
    val hasNextChapter: Boolean = false,
    val currentHref: String? = null,
    val lookup: WordLookupUiState? = null,
    val footnoteHtml: String? = null,
    val highlights: List<ReaderHighlight> = emptyList(),
    val editingHighlight: ReaderHighlight? = null,
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val searchInProgress: Boolean = false,
    val searchError: String? = null,
    val searchPerformed: Boolean = false,
) {
    val currentPageBookmarked: Boolean
        get() {
            val current = locator ?: return false
            return bookmarks.any { bookmark ->
                BookmarkIdentity.samePosition(
                    bookmark.href,
                    bookmark.locatorJson,
                    bookmark.progression,
                    current.chapterId ?: current.href,
                    current.locatorJson,
                    current.totalProgression,
                )
            }
        }
}

internal sealed interface ReadingExperienceEvent {
    data object ToggleChrome : ReadingExperienceEvent
}

internal sealed interface ReadingExperienceAction {
    data object RetryOpen : ReadingExperienceAction
    data object Resume : ReadingExperienceAction
    data object Pause : ReadingExperienceAction
    data object ResetSettings : ReadingExperienceAction
    data object SubmitSearch : ReadingExperienceAction
    data object ClearSearch : ReadingExperienceAction
    data object RetryLookup : ReadingExperienceAction
    data object LookupBack : ReadingExperienceAction
    data object CloseLookup : ReadingExperienceAction
    data object CloseFootnote : ReadingExperienceAction
    data object ToggleBookmark : ReadingExperienceAction
    data object DeleteEditingHighlight : ReadingExperienceAction
    data object CloseEditingHighlight : ReadingExperienceAction

    data class ApplySettings(val settings: ReaderSettings) : ReadingExperienceAction
    data class Navigate(val target: ReaderNavigation) : ReadingExperienceAction
    data class ChangeSearchQuery(val query: String) : ReadingExperienceAction
    data class LookUpWord(val text: String, val resetHistory: Boolean = false) : ReadingExperienceAction
    data class Pronounce(val text: String) : ReadingExperienceAction
    data class DeleteBookmark(val id: String) : ReadingExperienceAction
    data class DeleteHighlight(val id: String) : ReadingExperienceAction
    data class SetEditingHighlightColor(val colorArgb: Int) : ReadingExperienceAction
}

internal sealed interface ReaderNavigation {
    data object NextChapter : ReaderNavigation
    data object PreviousChapter : ReaderNavigation
    data class Locator(val locatorJson: String) : ReaderNavigation
}

class ReadingExperienceFactory @Inject constructor(
    private val engine: ReaderEngine,
    private val books: BookRepository,
    private val settings: ReaderSettingsRepository,
    private val dictionary: DictionaryRepository,
    private val highlights: HighlightRepository,
    private val bookmarks: BookmarkRepository,
    private val stats: StatsRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    internal fun create(
        bookId: BookId,
        initialLocatorJson: String?,
        ownerScope: CoroutineScope,
    ): ReadingExperience = ReadingExperience(
        bookId = bookId,
        initialLocatorJson = initialLocatorJson,
        settings = settings.effective(bookId),
        persistSettings = { settings.setForBook(bookId, it) },
        resetSettings = { settings.clearForBook(bookId) },
        lookupWord = { dictionary.lookup(it) },
        engine = engine,
        books = books,
        highlights = highlights,
        bookmarks = bookmarks,
        ownerScope = ownerScope,
        finalWriteScope = applicationScope,
        recordReadingSession = stats::recordSession,
    )
}

/** Owns the lifecycle and persistence invariants of one book-scoped reading experience. */
internal class ReadingExperience(
    private val bookId: BookId,
    private val initialLocatorJson: String?,
    private val settings: Flow<ReaderSettings>,
    private val persistSettings: suspend (ReaderSettings) -> Unit,
    private val resetSettings: suspend () -> Unit,
    private val lookupWord: suspend (String) -> DictionaryResult,
    private val engine: ReaderEngine,
    private val books: BookRepository,
    private val highlights: HighlightRepository,
    private val bookmarks: BookmarkRepository,
    ownerScope: CoroutineScope,
    private val finalWriteScope: CoroutineScope,
    private val recordReadingSession: suspend (BookId, Long, Long) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val progressSaveIntervalMillis: Long = PROGRESS_SAVE_INTERVAL_MS,
    private val readyTimeoutMillis: Long = READY_TIMEOUT_MS,
) {
    private val lifetimeJob = SupervisorJob(ownerScope.coroutineContext[Job])
    private val scope = CoroutineScope(ownerScope.coroutineContext + lifetimeJob)

    private val _state = MutableStateFlow(ReadingExperienceState())
    val state: StateFlow<ReadingExperienceState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReadingExperienceEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ReadingExperienceEvent> = _events.asSharedFlow()

    private var started = false
    private var closed = false
    private var foreground = false
    private var readingStartedAt: Long? = null
    private var openGeneration = 0L
    private var searchGeneration = 0L
    private var openJob: Job? = null
    private var sessionJob: Job? = null
    private var searchJob: Job? = null
    private var settingsWriteJob: Job? = null
    private var progressSaveJob: Job? = null
    private var pendingProgress: ReadingProgressSnapshot? = null
    private var lastLocator: ReaderLocator? = null
    private var currentChapterId: String? = null
    private var tocTitles: Map<String, String> = emptyMap()
    private var lastChapterTitle: String? = null
    private var session: ReaderSession? = null
    private val lookupStack = ArrayDeque<String>()
    private val lookupCache = mutableMapOf<String, DictionaryResult>()

    fun start() {
        if (started || closed) return
        started = true

        books.cachedTableOfContents(bookId)?.let(::primeTocTitles)
        scope.launch {
            books.observeBook(bookId).collect { book ->
                _state.update { it.copy(coverImagePath = book?.coverImagePath) }
            }
        }
        scope.launch {
            settings.collect { applySettings(it, persist = false) }
        }
        scope.launch {
            highlights.observeForBook(bookId).collect { list ->
                _state.update { it.copy(highlights = list) }
                session?.applyHighlights(list)
            }
        }
        scope.launch {
            bookmarks.observeForBook(bookId).collect { list ->
                _state.update { it.copy(bookmarks = list) }
            }
        }
        retry()
    }

    fun dispatch(action: ReadingExperienceAction) {
        if (closed) return
        when (action) {
            ReadingExperienceAction.RetryOpen -> retry()
            ReadingExperienceAction.Resume -> resume()
            ReadingExperienceAction.Pause -> pause()
            ReadingExperienceAction.ResetSettings -> scope.launch { runCatching { resetSettings() } }
            ReadingExperienceAction.SubmitSearch -> submitSearch()
            ReadingExperienceAction.ClearSearch -> clearSearch()
            ReadingExperienceAction.RetryLookup -> retryLookup()
            ReadingExperienceAction.LookupBack -> lookupBack()
            ReadingExperienceAction.CloseLookup -> closeLookup()
            ReadingExperienceAction.CloseFootnote -> _state.update { it.copy(footnoteHtml = null) }
            ReadingExperienceAction.ToggleBookmark -> toggleBookmark()
            ReadingExperienceAction.DeleteEditingHighlight -> deleteEditingHighlight()
            ReadingExperienceAction.CloseEditingHighlight -> _state.update { it.copy(editingHighlight = null) }
            is ReadingExperienceAction.ApplySettings -> applySettings(action.settings, persist = true)
            is ReadingExperienceAction.Navigate -> navigate(action.target)
            is ReadingExperienceAction.ChangeSearchQuery -> _state.update { it.copy(searchQuery = action.query) }
            is ReadingExperienceAction.LookUpWord -> lookUp(action.text, action.resetHistory)
            is ReadingExperienceAction.Pronounce -> speak(action.text)
            is ReadingExperienceAction.DeleteBookmark -> deleteBookmark(action.id)
            is ReadingExperienceAction.DeleteHighlight -> deleteHighlight(action.id)
            is ReadingExperienceAction.SetEditingHighlightColor -> setEditingHighlightColor(action.colorArgb)
        }
    }

    fun close() {
        if (closed) return
        foreground = false
        bankReadingSession()
        flushPendingProgress()
        closed = true
        openGeneration++
        searchGeneration++
        lifetimeJob.cancel()
        session?.close()
        session = null
        _state.update { it.copy(navigator = null) }
    }

    private fun retry() {
        if (closed || session != null || openJob?.isActive == true) return
        val generation = ++openGeneration
        _state.update {
            it.copy(
                loading = true,
                failed = false,
                renderState = ReaderRenderState.Opening,
            )
        }
        val traceCookie = ReaderOpenTrace.beginAsync("reader.open")
        openJob = scope.launch {
            var traceEnded = false
            fun endTrace() {
                if (!traceEnded) {
                    traceEnded = true
                    ReaderOpenTrace.endAsync("reader.open", traceCookie)
                }
            }

            try {
                val (target, initialSettings) = coroutineScope {
                    val targetDeferred = async { books.readingTarget(bookId) }
                    val settingsDeferred = async { settings.first() }
                    targetDeferred.await() to settingsDeferred.await()
                }
                if (!isCurrent(generation)) return@launch endTrace()

                ReaderOpenTrace.mark("reader.target-settings-resolved")
                applySettings(initialSettings, persist = false)
                val openedResult = target?.let {
                    ReaderOpenTrace.mark("reader.publication-open-start")
                    engine.open(
                        ReaderOpenRequest(
                            filePath = it.storagePath,
                            initialLocatorJson = initialLocatorJson ?: it.locatorJson,
                            initialSettings = initialSettings,
                            publicationCache = it.publicationCache,
                        ),
                    )
                }
                if (!isCurrent(generation)) {
                    openedResult?.session?.close()
                    return@launch endTrace()
                }
                if (openedResult == null) {
                    failOpen(generation, ::endTrace)
                    return@launch
                }
                attachSession(generation, openedResult, ::endTrace)
            } catch (cancelled: CancellationException) {
                endTrace()
                throw cancelled
            } catch (_: Throwable) {
                failOpen(generation, ::endTrace)
            }
        }
    }

    private fun attachSession(
        generation: Long,
        openedResult: ReaderOpenResult,
        endTrace: () -> Unit,
    ) {
        val opened = openedResult.session
        ReaderOpenTrace.mark("reader.session-created")
        session = opened
        opened.applyHighlights(_state.value.highlights)
        if (foreground) opened.onForegroundResumed()

        val items = opened.tableOfContents
        primeTocTitles(items)
        _state.update {
            it.copy(
                navigator = opened,
                title = opened.title,
                loading = true,
                failed = false,
                renderState = ReaderRenderState.Opening,
                tableOfContents = items,
                tocLoading = false,
                chapterTitle = lastChapterTitle ?: it.chapterTitle,
            )
        }

        sessionJob?.cancel()
        sessionJob = scope.launch {
            supervisorScope {
                var reachedReady = false
                var cachePersisted = false

                launch {
                    opened.renderState.collect { renderState ->
                        if (renderState is ReaderRenderState.Failed) {
                            endTrace()
                            failActiveSession(opened)
                            return@collect
                        }
                        if (renderState is ReaderRenderState.Ready) {
                            ReaderOpenTrace.mark("reader.ready")
                            endTrace()
                            reachedReady = true
                            if (!cachePersisted) {
                                cachePersisted = true
                                launch {
                                    runCatching {
                                        books.cachePublicationMetadata(
                                            bookId,
                                            openedResult.publicationCache,
                                        )
                                    }
                                }
                            }
                            lastLocator?.bookProgress?.let { progression ->
                                scheduleProgressSave(lastLocator!!.toProgressSnapshot(progression))
                            }
                            if (foreground && readingStartedAt == null) {
                                readingStartedAt = nowMillis()
                            }
                        }
                        _state.update {
                            it.copy(
                                renderState = renderState,
                                loading = renderState is ReaderRenderState.Opening,
                            )
                        }
                    }
                }
                launch {
                    val ready = withTimeoutOrNull(readyTimeoutMillis) {
                        opened.renderState.first { it is ReaderRenderState.Ready }
                    }
                    if (ready == null && !reachedReady && isCurrent(generation, opened)) {
                        endTrace()
                        failActiveSession(opened)
                    }
                }
                launch {
                    opened.currentLocator.collect { locator ->
                        if (locator != null) onLocator(opened, locator)
                    }
                }
                launch {
                    opened.centerTaps.collect { _events.tryEmit(ReadingExperienceEvent.ToggleChrome) }
                }
                launch {
                    opened.lookUpRequests.collect { lookUp(it, resetHistory = true) }
                }
                launch {
                    opened.footnotes.collect { html ->
                        _state.update { it.copy(footnoteHtml = html) }
                    }
                }
                launch {
                    opened.highlightRequests.collect { draft ->
                        runCatching {
                            highlights.add(
                                bookId,
                                draft.locatorJson,
                                draft.text,
                                DEFAULT_HIGHLIGHT_ARGB,
                            )
                        }
                    }
                }
                launch {
                    opened.highlightTaps.collect { id ->
                        val target = _state.value.highlights.firstOrNull { it.id == id }
                        if (target != null) {
                            _state.update { it.copy(editingHighlight = target) }
                        }
                    }
                }
            }
        }
    }

    private fun applySettings(settings: ReaderSettings, persist: Boolean) {
        if (closed || settings == _state.value.settings && persist) return
        _state.update { it.copy(settings = settings) }
        session?.applySettings(settings)
        if (persist) {
            settingsWriteJob?.cancel()
            settingsWriteJob = scope.launch { runCatching { persistSettings(settings) } }
        }
    }

    private fun navigate(target: ReaderNavigation) {
        val active = session ?: return
        when (target) {
            ReaderNavigation.NextChapter -> active.nextChapter()
            ReaderNavigation.PreviousChapter -> active.previousChapter()
            is ReaderNavigation.Locator -> active.goToLocator(target.locatorJson)
        }
    }

    private fun submitSearch() {
        val query = _state.value.searchQuery.trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        val generation = ++searchGeneration
        val active = session
        searchJob = scope.launch {
            _state.update {
                it.copy(
                    searchInProgress = true,
                    searchError = null,
                    searchPerformed = true,
                    searchResults = emptyList(),
                )
            }
            try {
                val results = active?.search(query).orEmpty()
                ensureActive()
                if (
                    generation != searchGeneration ||
                    _state.value.searchQuery.trim() != query ||
                    session !== active
                ) {
                    return@launch
                }
                _state.update { it.copy(searchInProgress = false, searchResults = results) }
                active?.applySearchDecorations(results)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (generation == searchGeneration) {
                    _state.update {
                        it.copy(
                            searchInProgress = false,
                            searchError = "Search couldn't be completed.",
                        )
                    }
                }
            }
        }
    }

    private fun clearSearch() {
        searchGeneration++
        searchJob?.cancel()
        searchJob = null
        session?.clearSearch()
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searchInProgress = false,
                searchError = null,
                searchPerformed = false,
            )
        }
    }

    private fun lookUp(rawText: String, resetHistory: Boolean) {
        val word = sanitizeWord(rawText) ?: return
        if (resetHistory) lookupStack.clear()
        if (!resetHistory && lookupStack.lastOrNull() == word) return
        lookupStack.addLast(word)
        showLookup(word)
    }

    private fun retryLookup() {
        val current = _state.value.lookup ?: return
        if (current.result !is DictionaryResult.Error || current.loading) return
        lookupCache.remove(current.word)
        showLookup(current.word)
    }

    private fun lookupBack() {
        if (lookupStack.size <= 1) {
            closeLookup()
            return
        }
        lookupStack.removeLast()
        showLookup(lookupStack.last())
    }

    private fun closeLookup() {
        lookupStack.clear()
        _state.update { it.copy(lookup = null) }
    }

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
        scope.launch {
            val result = runCatching { lookupWord(word) }.getOrDefault(DictionaryResult.Error)
            lookupCache[word] = result
            _state.update { currentState ->
                val current = currentState.lookup ?: return@update currentState
                if (current.word != word) return@update currentState
                currentState.copy(lookup = current.copy(loading = false, result = result))
            }
        }
    }

    private fun speak(rawText: String) {
        rawText.trim().takeIf { it.isNotEmpty() }?.let { session?.speak(it) }
    }

    private fun toggleBookmark() {
        val locator = _state.value.locator ?: return
        scope.launch {
            runCatching {
                bookmarks.toggle(
                    bookId,
                    locator.locatorJson,
                    locator.chapterId ?: locator.href,
                    locator.chapterTitle ?: _state.value.chapterTitle,
                    locator.totalProgression,
                )
            }
        }
    }

    private fun deleteBookmark(id: String) {
        scope.launch { runCatching { bookmarks.delete(id) } }
    }

    private fun deleteEditingHighlight() {
        val target = _state.value.editingHighlight ?: return
        _state.update { it.copy(editingHighlight = null) }
        deleteHighlight(target.id)
    }

    private fun deleteHighlight(id: String) {
        scope.launch { runCatching { highlights.delete(id) } }
    }

    private fun setEditingHighlightColor(colorArgb: Int) {
        val target = _state.value.editingHighlight ?: return
        _state.update { it.copy(editingHighlight = target.copy(colorArgb = colorArgb)) }
        scope.launch { runCatching { highlights.updateColor(target.id, colorArgb) } }
    }

    private fun resume() {
        foreground = true
        val active = session
        if (active != null) {
            active.onForegroundResumed()
            if (_state.value.renderState is ReaderRenderState.Ready && readingStartedAt == null) {
                readingStartedAt = nowMillis()
            }
        }
    }

    private fun pause() {
        foreground = false
        bankReadingSession()
        flushPendingProgress()
    }

    private fun onLocator(opened: ReaderSession, locator: ReaderLocator) {
        if (session !== opened) return
        lastLocator = locator
        val progression = locator.bookProgress
        val resolved = locator.chapterTitle ?: locator.chapterId?.let { tocTitles[it] }
        if (!resolved.isNullOrBlank()) lastChapterTitle = resolved
        _state.update {
            it.copy(
                locator = locator,
                chapterTitle = lastChapterTitle,
                progressPercent = progression?.let { progress -> (progress * 100).toInt() }
                    ?: it.progressPercent,
                totalProgression = progression ?: it.totalProgression,
                chapterPagesLeft = locator.chapterPagesLeft,
                chapterProgression = locator.chapterProgression ?: it.chapterProgression,
                hasPreviousChapter = locator.hasPreviousChapter,
                hasNextChapter = locator.hasNextChapter,
                currentHref = locator.chapterId ?: locator.href,
            )
        }

        val chapterChanged = currentChapterId != null && locator.chapterId != currentChapterId
        if (progression != null && opened.renderState.value is ReaderRenderState.Ready) {
            scheduleProgressSave(locator.toProgressSnapshot(progression))
            if (chapterChanged) scope.launch { persistPendingProgress() }
        }
        currentChapterId = locator.chapterId
    }

    private fun failOpen(generation: Long, endTrace: () -> Unit) {
        if (!isCurrent(generation)) return
        endTrace()
        _state.update { it.copy(loading = false, failed = true, navigator = null) }
    }

    private fun failActiveSession(opened: ReaderSession) {
        if (session !== opened) return
        bankReadingSession()
        flushPendingProgress()
        searchGeneration++
        searchJob?.cancel()
        opened.close()
        session = null
        _state.update {
            it.copy(
                navigator = null,
                loading = false,
                failed = true,
                searchInProgress = false,
            )
        }
        sessionJob?.cancel()
    }

    private fun primeTocTitles(items: List<ReaderTocItem>) {
        val titles = LinkedHashMap<String, String>()
        items.forEach { titles.putIfAbsent(it.id, it.title) }
        tocTitles = titles
        val resolved = currentChapterId?.let(titles::get)
        if (!resolved.isNullOrBlank()) lastChapterTitle = resolved
    }

    private fun scheduleProgressSave(snapshot: ReadingProgressSnapshot) {
        pendingProgress = snapshot
        if (progressSaveJob?.isActive == true) return
        progressSaveJob = scope.launch {
            do {
                delay(progressSaveIntervalMillis)
                persistPendingProgress()
            } while (pendingProgress != null)
        }
    }

    private suspend fun persistPendingProgress() {
        val pending = pendingProgress ?: return
        runCatching {
            books.saveProgress(bookId, pending)
        }.onSuccess {
            if (pendingProgress == pending) pendingProgress = null
        }
    }

    private fun flushPendingProgress() {
        progressSaveJob?.cancel()
        progressSaveJob = null
        val pending = pendingProgress ?: return
        pendingProgress = null
        finalWriteScope.launch {
            runCatching { books.saveProgress(bookId, pending) }
        }
    }

    private fun bankReadingSession() {
        val start = readingStartedAt ?: return
        readingStartedAt = null
        val seconds = (nowMillis() - start) / 1_000L
        if (seconds in 1..MAX_SESSION_SECONDS) {
            finalWriteScope.launch {
                runCatching { recordReadingSession(bookId, start, seconds) }
            }
        }
    }

    private fun isCurrent(generation: Long, opened: ReaderSession? = null): Boolean = !closed && generation == openGeneration && (opened == null || session === opened)

    private fun ReaderLocator.toProgressSnapshot(progression: Double): ReadingProgressSnapshot = ReadingProgressSnapshot(
        locatorJson = locatorJson,
        bookProgress = progression,
        currentHref = href,
        chapterId = chapterId,
        chapterProgress = chapterProgression,
        completedChapterId = completedChapterId,
        completed = completed,
    )

    private fun sanitizeWord(raw: String): String? = raw.trim()
        .split(Regex("\\s+"))
        .firstOrNull()
        ?.lowercase()
        ?.filter { it.isLetter() || it == '-' || it == '\'' }
        ?.takeIf { it.isNotBlank() }

    private companion object {
        const val DEFAULT_HIGHLIGHT_ARGB = 0xFFE7C75B.toInt()
        const val MAX_SESSION_SECONDS = 24L * 60 * 60
        const val PROGRESS_SAVE_INTERVAL_MS = 4_000L
        const val READY_TIMEOUT_MS = 8_000L
    }
}
