package com.itexpert120.yomu.feature.reader

import android.content.Context
import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderImage
import com.itexpert120.yomu.core.reader.ReaderLocator
import com.itexpert120.yomu.core.reader.ReaderNavigator
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderOpenTrace
import com.itexpert120.yomu.core.reader.ReaderPageTarget
import com.itexpert120.yomu.core.reader.ReaderRenderState
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderSelection
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.core.reader.at
import com.itexpert120.yomu.core.reader.bookmarkAnchor
import com.itexpert120.yomu.data.bookmarks.BookmarkRepository
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.PublicationIndex
import com.itexpert120.yomu.data.books.ReadingProgressSnapshot
import com.itexpert120.yomu.data.dictionary.DictionaryRepository
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.dictionary.LookupTerm
import com.itexpert120.yomu.data.encyclopedia.WikipediaRepository
import com.itexpert120.yomu.data.encyclopedia.WikipediaResult
import com.itexpert120.yomu.data.highlights.HighlightRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import com.itexpert120.yomu.data.stats.ReadingWriteQueue
import com.itexpert120.yomu.data.stats.StatsRepository
import com.itexpert120.yomu.data.sync.ProgressOffer
import com.itexpert120.yomu.data.sync.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

data class WordLookupUiState(
    val word: String,
    val loading: Boolean = true,
    val result: DictionaryResult? = null,
    val canGoBack: Boolean = false,
    // The dictionary language looked up in (ISO 639), from the selection's language.
    val language: String = "en",
    // The selection as the reader made it ("Portly", "river bank"): Wikipedia's title, and what
    // Translate and Search in book take.
    val query: String = word,
    // A short phrase: the dictionary has no entries for those, so the sheet shows only Wikipedia.
    val phrase: Boolean = false,
    val tab: LookupTab = if (phrase) LookupTab.Wikipedia else LookupTab.Dictionary,
    // Null until the Wikipedia tab is first opened: nothing is fetched before that.
    val wikipedia: WikipediaUiState? = null,
    val canTranslate: Boolean = false,
)

enum class LookupTab(val label: String) {
    Dictionary("Dictionary"),
    Wikipedia("Wikipedia"),
}

data class WikipediaUiState(val loading: Boolean = true, val result: WikipediaResult? = null)

/** A highlight or bookmark just deleted, kept while its Undo notice is up. */
sealed interface AnnotationUndo {
    val key: Long
    val message: String

    data class Highlight(override val key: Long, val highlight: ReaderHighlight) : AnnotationUndo {
        override val message: String get() = "Highlight deleted"
    }

    data class Bookmark(override val key: Long, val bookmark: ReaderBookmark) : AnnotationUndo {
        override val message: String get() = "Bookmark deleted"
    }
}

data class ReadingExperienceState(
    val navigator: ReaderNavigator? = null,
    val loading: Boolean = true,
    val renderState: ReaderRenderState = ReaderRenderState.Opening,
    val failed: Boolean = false,
    val title: String = "",
    val chapterTitle: String? = null,
    val coverImagePath: String? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val settingsPending: Boolean = false,
    val settingsError: String? = null,
    val annotationError: String? = null,
    val annotationsPending: Boolean = false,
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
    // A newer position synced from another device, offered rather than applied.
    val syncOffer: SyncOfferUi? = null,
    // The printed page on screen ("112", "112–113") and the book's page list, for Go to page.
    val pageLabel: String? = null,
    val pageList: List<ReaderPageTarget> = emptyList(),
    // Estimated minutes of reading left in the chapter and in the book; null while unknown.
    val minutesLeftInChapter: Int? = null,
    val minutesLeftInBook: Int? = null,
    // "Back to p. 112": the way back after a jump, while it's on offer.
    val jumpBack: String? = null,
    // A picture opened from the page, and what became of saving it.
    val viewedImage: ReaderImage? = null,
    val imageMessage: String? = null,
    val undo: AnnotationUndo? = null,
) {
    val currentPageBookmarked: Boolean
        get() = locator?.let { bookmarks.at(it.bookmarkAnchor()).isNotEmpty() } ?: false
}

/** "Continue from <device> at <percent>%?" */
data class SyncOfferUi(val device: String?, val percent: Int, val locatorJson: String)

internal sealed interface ReadingExperienceEvent {
    data object ToggleChrome : ReadingExperienceEvent

    /** "Search in book" from the selection menu: the reader opens its search sheet on [text]. */
    data class SearchInBook(val text: String) : ReadingExperienceEvent
}

internal sealed interface ReadingExperienceAction {
    data object RetryOpen : ReadingExperienceAction
    data object Resume : ReadingExperienceAction
    data object Pause : ReadingExperienceAction
    data object ResetSettings : ReadingExperienceAction
    data object RetrySettings : ReadingExperienceAction
    data object RetryAnnotations : ReadingExperienceAction
    data object SubmitSearch : ReadingExperienceAction
    data object ClearSearch : ReadingExperienceAction
    data object RetryLookup : ReadingExperienceAction
    data object LookupBack : ReadingExperienceAction
    data object CloseLookup : ReadingExperienceAction
    data object CloseFootnote : ReadingExperienceAction
    data object ToggleBookmark : ReadingExperienceAction
    data object DeleteEditingHighlight : ReadingExperienceAction
    data object CloseEditingHighlight : ReadingExperienceAction
    data object AcceptSyncOffer : ReadingExperienceAction
    data object DismissSyncOffer : ReadingExperienceAction
    data object JumpBack : ReadingExperienceAction
    data object CloseImage : ReadingExperienceAction
    data object SaveImage : ReadingExperienceAction
    data object ShareImage : ReadingExperienceAction

    data class ApplySettings(val settings: ReaderSettings) : ReadingExperienceAction
    data class Navigate(val target: ReaderNavigation) : ReadingExperienceAction
    data class ChangeSearchQuery(val query: String) : ReadingExperienceAction
    data class LookUpWord(val text: String, val resetHistory: Boolean = false) : ReadingExperienceAction
    data class Pronounce(val text: String) : ReadingExperienceAction
    data class DeleteBookmark(val id: String) : ReadingExperienceAction
    data class DeleteHighlight(val id: String) : ReadingExperienceAction
    data class SetEditingHighlightColor(val colorArgb: Int) : ReadingExperienceAction
    data class SetEditingHighlightStyle(val style: HighlightStyle) : ReadingExperienceAction
    data class SetHighlightNote(val id: String, val note: String) : ReadingExperienceAction
    data object CopyEditingHighlight : ReadingExperienceAction
    data object ShareEditingHighlight : ReadingExperienceAction
    data class UndoDelete(val key: Long) : ReadingExperienceAction
    data class DismissUndo(val key: Long) : ReadingExperienceAction
    data class SelectLookupTab(val tab: LookupTab) : ReadingExperienceAction
    data object LookUpInEnglish : ReadingExperienceAction
    data object RetryWikipedia : ReadingExperienceAction
    data class Translate(val text: String) : ReadingExperienceAction

    /** Closes the look-up sheet and searches the book for [text]. */
    data class SearchFor(val text: String) : ReadingExperienceAction
}

internal sealed interface ReaderNavigation {
    data object NextChapter : ReaderNavigation
    data object PreviousChapter : ReaderNavigation

    /** A jump to a position (contents, search, a bookmark or highlight, a page number). */
    data class Locator(val locatorJson: String) : ReaderNavigation

    /** [progression] (0..1) of the way through the chapter being read. */
    data class ChapterProgression(val progression: Double) : ReaderNavigation
}

class ReadingExperienceFactory @Inject constructor(
    private val engine: ReaderEngine,
    private val books: BookRepository,
    private val publications: PublicationIndex,
    private val settings: ReaderSettingsRepository,
    private val dictionary: DictionaryRepository,
    private val wikipedia: WikipediaRepository,
    private val highlights: HighlightRepository,
    private val bookmarks: BookmarkRepository,
    private val stats: StatsRepository,
    private val readingWrites: ReadingWriteQueue,
    private val sync: SyncRepository,
    private val selectionOutlet: AndroidSelectionOutlet,
    private val imageOutlet: AndroidImageOutlet,
    @ApplicationContext private val context: Context,
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
        lookupWord = { word, language -> dictionary.lookup(word, language) },
        selectionOutlet = selectionOutlet,
        imageOutlet = imageOutlet,
        readingSeconds = stats.bookReadingSeconds(bookId),
        readAloud = AndroidReadAloud(context),
        engine = engine,
        books = books,
        publications = publications,
        highlights = highlights,
        bookmarks = bookmarks,
        ownerScope = ownerScope,
        finalWriteScope = applicationScope,
        recordReadingSession = stats::recordSession,
        readingWrites = readingWrites,
        syncOffer = sync.progressOffer(bookId),
        clearSyncOffer = { sync.clearOffer(bookId) },
        lookupWikipedia = wikipedia::summary,
    )
}

/** Owns the lifecycle and persistence invariants of one book-scoped reading experience. */
internal class ReadingExperience(
    private val bookId: BookId,
    private val initialLocatorJson: String?,
    private val settings: Flow<ReaderSettings>,
    private val persistSettings: suspend (ReaderSettings) -> Unit,
    private val resetSettings: suspend () -> Unit,
    private val lookupWord: suspend (word: String, language: String) -> DictionaryResult,
    private val selectionOutlet: SelectionOutlet,
    private val readAloud: ReadAloud,
    private val engine: ReaderEngine,
    private val books: BookRepository,
    private val publications: PublicationIndex,
    private val highlights: HighlightRepository,
    private val bookmarks: BookmarkRepository,
    ownerScope: CoroutineScope,
    private val finalWriteScope: CoroutineScope,
    private val recordReadingSession: suspend (BookId, Long, Long, String) -> Unit,
    private val readingWrites: ReadingWriteQueue = ReadingWriteQueue(finalWriteScope),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val progressSaveIntervalMillis: Long = PROGRESS_SAVE_INTERVAL_MS,
    private val readyTimeoutMillis: Long = READY_TIMEOUT_MS,
    private val syncOffer: Flow<ProgressOffer?> = emptyFlow(),
    private val clearSyncOffer: suspend () -> Unit = {},
    private val imageOutlet: ImageOutlet? = null,
    // Time spent reading this book so far (Stats), for the reader's own reading speed.
    private val readingSeconds: Flow<Long> = emptyFlow(),
    private val jumpBackTimeoutMillis: Long = JUMP_BACK_TIMEOUT_MS,
    private val lookupWikipedia: suspend (text: String, language: String?) -> WikipediaResult = { _, _ -> WikipediaResult.NotFound },
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
    private var readingStartedElapsed: Long = 0L
    private var openGeneration = 0L
    private var searchGeneration = 0L
    private var openJob: Job? = null
    private var sessionJob: Job? = null
    private var searchJob: Job? = null
    private val settingsEditor = ReaderSettingsEditor(scope, persistSettings) {
        resetSettings()
        settings.first()
    }
    private var progressSaveJob: Job? = null
    private var pendingProgress: ReadingProgressSnapshot? = null
    private var lastLocator: ReaderLocator? = null
    private var currentChapterId: String? = null
    private var tocTitles: Map<String, String> = emptyMap()
    private var lastChapterTitle: String? = null
    private var session: ReaderSession? = null

    // The engine locator a portable locator was captured for, and that portable locator.
    private var portableLocator: Pair<String, String>? = null
    private var portableJob: Job? = null
    private val lookupStack = ArrayDeque<LookupEntry>()
    private val lookupCache = mutableMapOf<String, DictionaryResult>()

    private val jumps = JumpHistory()
    private var jumpBackJob: Job? = null
    private var bookReadingSeconds: Long? = null
    private var imageJob: Job? = null
    private val wikipediaCache = mutableMapOf<String, WikipediaResult>()
    private var bookAuthor: String? = null
    private var undoKey = 0L
    private data class AnnotationWrite(val message: String, val persist: suspend () -> Unit)
    private val annotationWrites = ArrayDeque<AnnotationWrite>()
    private var annotationJob: Job? = null

    fun start() {
        if (started || closed) return
        started = true

        scope.launch {
            books.observeBook(bookId).collect { book ->
                bookAuthor = book?.author?.takeIf { it.isNotBlank() }
                _state.update { it.copy(coverImagePath = book?.coverImagePath) }
            }
        }
        scope.launch {
            settings.collect(settingsEditor::observe)
        }
        scope.launch {
            settingsEditor.state.collect { edit ->
                _state.update { it.copy(settings = edit.settings, settingsPending = edit.pending, settingsError = edit.error) }
                session?.applySettings(edit.settings)
            }
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
        scope.launch {
            readingSeconds.collect { seconds ->
                bookReadingSeconds = seconds
                lastLocator?.let { locator -> _state.update { it.withTimeLeft(locator) } }
            }
        }
        scope.launch {
            syncOffer.collect { offer ->
                val ui = offer?.let { SyncOfferUi(it.device, (it.progress * 100).roundToInt().coerceIn(0, 100), it.locatorJson) }
                _state.update { it.copy(syncOffer = ui) }
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
            ReadingExperienceAction.ResetSettings -> settingsEditor.reset()
            ReadingExperienceAction.RetrySettings -> settingsEditor.retry()
            ReadingExperienceAction.RetryAnnotations -> retryAnnotations()
            ReadingExperienceAction.SubmitSearch -> submitSearch()
            ReadingExperienceAction.ClearSearch -> clearSearch()
            ReadingExperienceAction.RetryLookup -> retryLookup()
            ReadingExperienceAction.LookupBack -> lookupBack()
            ReadingExperienceAction.CloseLookup -> closeLookup()
            ReadingExperienceAction.CloseFootnote -> _state.update { it.copy(footnoteHtml = null) }
            ReadingExperienceAction.ToggleBookmark -> toggleBookmark()
            ReadingExperienceAction.DeleteEditingHighlight -> deleteEditingHighlight()
            ReadingExperienceAction.CloseEditingHighlight -> _state.update { it.copy(editingHighlight = null) }
            ReadingExperienceAction.AcceptSyncOffer -> {
                _state.value.syncOffer?.let { navigate(ReaderNavigation.Locator(it.locatorJson)) }
                dismissSyncOffer()
            }
            ReadingExperienceAction.DismissSyncOffer -> dismissSyncOffer()
            is ReadingExperienceAction.ApplySettings -> applySettings(action.settings, persist = true)
            is ReadingExperienceAction.Navigate -> navigate(action.target)
            is ReadingExperienceAction.ChangeSearchQuery -> changeSearchQuery(action.query)
            is ReadingExperienceAction.LookUpWord -> lookUpWord(action.text, action.resetHistory)
            is ReadingExperienceAction.Pronounce -> speak(action.text)
            is ReadingExperienceAction.DeleteBookmark -> deleteBookmark(action.id)
            is ReadingExperienceAction.DeleteHighlight -> deleteHighlight(action.id)
            is ReadingExperienceAction.SetEditingHighlightColor -> setEditingHighlightColor(action.colorArgb)
            ReadingExperienceAction.JumpBack -> jumpBack()
            ReadingExperienceAction.CloseImage -> {
                imageJob?.cancel()
                _state.update { it.copy(viewedImage = null, imageMessage = null) }
            }
            ReadingExperienceAction.SaveImage -> saveImage()
            ReadingExperienceAction.ShareImage -> shareImage()
            is ReadingExperienceAction.SetEditingHighlightStyle -> setEditingHighlightStyle(action.style)
            is ReadingExperienceAction.SetHighlightNote -> setHighlightNote(action.id, action.note)
            ReadingExperienceAction.CopyEditingHighlight -> _state.value.editingHighlight?.let { selectionOutlet.copy(it.text) }
            ReadingExperienceAction.ShareEditingHighlight -> _state.value.editingHighlight?.let { selectionOutlet.share(it.text) }
            is ReadingExperienceAction.UndoDelete -> undoDelete(action.key)
            is ReadingExperienceAction.DismissUndo -> _state.update { if (it.undo?.key == action.key) it.copy(undo = null) else it }
            is ReadingExperienceAction.SelectLookupTab -> selectLookupTab(action.tab)
            ReadingExperienceAction.LookUpInEnglish -> lookUpInEnglish()
            ReadingExperienceAction.RetryWikipedia -> retryWikipedia()
            is ReadingExperienceAction.Translate -> action.text.trim().takeIf { it.isNotEmpty() }?.let(selectionOutlet::translate)
            is ReadingExperienceAction.SearchFor -> searchFor(action.text)
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
        readAloud.shutdown()
        session?.close()
        session = null
        _state.update { it.copy(navigator = null) }
    }

    // region Back to where you were

    private fun recordJump(coalesce: Boolean = false) {
        val origin = lastLocator ?: return
        jumps.recordJump(origin, coalesce)
        showJumpBack()
    }

    private fun showJumpBack() {
        val back = jumps.back
        _state.update { it.copy(jumpBack = back?.let { origin -> jumpBackLabel(origin, origin.chapterId?.let(tocTitles::get)) }) }
        jumpBackJob?.cancel()
        if (back == null) return
        jumpBackJob = scope.launch {
            delay(jumpBackTimeoutMillis)
            jumps.clear()
            _state.update { it.copy(jumpBack = null) }
        }
    }

    private fun jumpBack() {
        val active = session ?: return
        val origin = jumps.pop() ?: return
        active.goToLocator(origin.locatorJson)
        showJumpBack()
    }

    // endregion

    // region Pictures

    private fun saveImage() {
        val image = _state.value.viewedImage ?: return
        val outlet = imageOutlet ?: return
        imageJob?.cancel()
        imageJob = scope.launch {
            val saved = outlet.save(image, imageName())
            if (_state.value.viewedImage !== image) return@launch
            _state.update { it.copy(imageMessage = if (saved) "Saved to Pictures / Open Reader" else "Couldn't save the picture") }
            delay(IMAGE_MESSAGE_MS)
            _state.update { if (it.viewedImage === image) it.copy(imageMessage = null) else it }
        }
    }

    private fun shareImage() {
        val image = _state.value.viewedImage ?: return
        val outlet = imageOutlet ?: return
        scope.launch { runCatching { outlet.share(image, imageName()) } }
    }

    /** "Book Title - 20261126-142305", safe as a file name. */
    private fun imageName(): String {
        val title = _state.value.title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").trim().take(60).ifBlank { "Open Reader" }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date(nowMillis()))
        return "$title - $stamp"
    }

    // endregion

    /** [locator]'s reading-time estimates, at the reader's speed in this book. */
    private fun ReadingExperienceState.withTimeLeft(locator: ReaderLocator): ReadingExperienceState {
        // The session under way counts too: its progress is already in the position.
        val current = if (readingStartedAt != null) (elapsedMillis() - readingStartedElapsed) / 1_000L else 0L
        val speed = ReadingTime.kbPerMinute(bookReadingSeconds?.plus(current), locator.bookSizeRead)
        return copy(
            minutesLeftInChapter = ReadingTime.minutesLeft(locator.chapterSizeLeft, speed),
            minutesLeftInBook = ReadingTime.minutesLeft(locator.bookSizeLeft, speed),
        )
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
                val (target, initialSettings, publication) = coroutineScope {
                    val targetDeferred = async { books.readingTarget(bookId) }
                    val settingsDeferred = async { settings.first() }
                    val publicationDeferred = async { publications.publicationCache(bookId) }
                    Triple(targetDeferred.await(), settingsDeferred.await(), publicationDeferred.await())
                }
                if (!isCurrent(generation)) return@launch endTrace()
                publication?.let { primeTocTitles(it.toc) }

                ReaderOpenTrace.mark("reader.target-settings-resolved")
                applySettings(initialSettings, persist = false)
                val openedResult = target?.let {
                    ReaderOpenTrace.mark("reader.publication-open-start")
                    engine.open(
                        ReaderOpenRequest(
                            filePath = it.storagePath,
                            initialLocatorJson = initialLocatorJson ?: it.locatorJson,
                            initialSettings = initialSettings,
                            publicationCache = publication,
                            // Short phrases look up on Wikipedia in the sheet; only longer ones lose "Look up".
                            isLookupPhrase = { text -> (LookupTerm.of(text) as? LookupTerm.Phrase)?.isShort == false },
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
                        if (renderState is ReaderRenderState.Ready) {
                            ReaderOpenTrace.mark("reader.ready")
                            endTrace()
                            reachedReady = true
                            if (!cachePersisted) {
                                cachePersisted = true
                                launch {
                                    runCatching {
                                        publications.remember(bookId, openedResult.publicationCache)
                                    }
                                }
                            }
                            lastLocator?.bookProgress?.let { progression ->
                                scheduleProgressSave(lastLocator!!.toProgressSnapshot(progression))
                            }
                            if (foreground && readingStartedAt == null) {
                                readingStartedAt = nowMillis()
                                readingStartedElapsed = elapsedMillis()
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
                    opened.selections.collect(::onSelection)
                }
                launch {
                    opened.footnotes.collect { html ->
                        _state.update { it.copy(footnoteHtml = html) }
                    }
                }
                launch {
                    opened.linkJumps.collect { origin ->
                        jumps.recordJump(origin)
                        showJumpBack()
                    }
                }
                launch {
                    opened.pageList.collect { pages -> _state.update { it.copy(pageList = pages) } }
                }
                launch {
                    opened.images.collect { image ->
                        imageJob?.cancel()
                        _state.update { it.copy(viewedImage = image, imageMessage = null) }
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

    private fun onSelection(selection: ReaderSelection) {
        val text = selection.text
        when (selection.action) {
            ReaderSelection.Action.Copy -> selectionOutlet.copy(text)
            ReaderSelection.Action.Share -> selectionOutlet.share(text)
            ReaderSelection.Action.SearchWeb -> selectionOutlet.searchWeb(text)
            ReaderSelection.Action.ReadAloud -> speak(text)
            ReaderSelection.Action.SearchInBook -> _events.tryEmit(ReadingExperienceEvent.SearchInBook(text))
            ReaderSelection.Action.CopyWithCitation -> selectionOutlet.copy(formatCitation(text, bookAuthor, _state.value.title, _state.value.pageLabel?.substringBefore('–')))
            // A short phrase is looked up on Wikipedia in the sheet. The menu hides "Look up" for
            // longer ones, but one that hasn't re-prepared yet can still fire it: those go to the web.
            ReaderSelection.Action.LookUp -> when (val term = LookupTerm.of(text)) {
                is LookupTerm.Phrase -> if (term.isShort) {
                    lookUpPhrase(term.text, lookupLanguage(selection.language))
                } else {
                    selectionOutlet.searchWeb("${term.text} meaning")
                }
                is LookupTerm.Word -> lookUp(term, resetHistory = true, language = lookupLanguage(selection.language), query = text)
                null -> Unit
            }
            // A new highlight opens its sheet straight away, for a note or another colour or style.
            ReaderSelection.Action.Highlight -> mutateAnnotation("Highlight wasn't saved.") {
                val existing = highlights.observeForBook(bookId).first().firstOrNull {
                    it.locatorJson == selection.locatorJson && it.text == text
                }
                val highlight = existing ?: highlights.add(bookId, selection.locatorJson, text, DEFAULT_HIGHLIGHT_ARGB)
                _state.update { it.copy(editingHighlight = highlight) }
            }
        }
    }

    private fun applySettings(settings: ReaderSettings, persist: Boolean) {
        if (closed || settings == _state.value.settings && persist) return
        if (persist) settingsEditor.edit(settings) else settingsEditor.observe(settings)
        val edit = settingsEditor.state.value
        _state.update { it.copy(settings = edit.settings, settingsPending = edit.pending, settingsError = edit.error) }
        session?.applySettings(edit.settings)
    }

    private fun navigate(target: ReaderNavigation) {
        val active = session ?: return
        when (target) {
            ReaderNavigation.NextChapter -> active.nextChapter()
            ReaderNavigation.PreviousChapter -> active.previousChapter()
            is ReaderNavigation.Locator -> {
                recordJump()
                active.goToLocator(target.locatorJson)
            }
            is ReaderNavigation.ChapterProgression -> {
                // Scrubbing several times goes back to where the first scrub started.
                recordJump(coalesce = true)
                active.goToChapterProgression(target.progression)
            }
        }
    }

    private fun changeSearchQuery(query: String) {
        if (query == _state.value.searchQuery) return
        if (_state.value.searchPerformed || _state.value.searchInProgress) clearSearch()
        _state.update { it.copy(searchQuery = query) }
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

    // Text from the look-up sheet (a definition's word, a synonym chip): a phrase looks up its first
    // word, in the language of the entry it was tapped in.
    private fun lookUpWord(rawText: String, resetHistory: Boolean) {
        val term = when (val parsed = LookupTerm.of(rawText)) {
            is LookupTerm.Phrase -> parsed.firstWord
            is LookupTerm.Word -> parsed
            null -> null
        } ?: return
        lookUp(term, resetHistory, language = lookupStack.lastOrNull()?.language ?: DEFAULT_LOOKUP_LANGUAGE)
    }

    private fun lookUp(term: LookupTerm.Word, resetHistory: Boolean, language: String, query: String? = null) {
        val entry = LookupEntry(
            word = term.word,
            language = language,
            query = query?.trim { !it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() } ?: term.word,
        )
        if (resetHistory) lookupStack.clear()
        val top = lookupStack.lastOrNull()
        if (!resetHistory && top?.word == entry.word && top.language == entry.language) return
        lookupStack.addLast(entry)
        showLookup(entry)
    }

    private fun lookUpPhrase(text: String, language: String) {
        lookupStack.clear()
        val entry = LookupEntry(word = text, language = language, query = text, phrase = true)
        lookupStack.addLast(entry)
        showLookup(entry)
    }

    private fun lookUpInEnglish() {
        val top = lookupStack.removeLastOrNull() ?: return
        val english = top.copy(language = DEFAULT_LOOKUP_LANGUAGE)
        lookupStack.addLast(english)
        showLookup(english)
    }

    private fun retryLookup() {
        val current = _state.value.lookup ?: return
        if (current.result !is DictionaryResult.Error || current.loading) return
        val entry = lookupStack.lastOrNull() ?: return
        lookupCache.remove(entry.cacheKey)
        showLookup(entry)
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

    private fun showLookup(entry: LookupEntry) {
        val cached = if (entry.phrase) null else lookupCache[entry.cacheKey]
        _state.update {
            it.copy(
                lookup = WordLookupUiState(
                    word = entry.word,
                    loading = !entry.phrase && cached == null,
                    result = cached,
                    canGoBack = lookupStack.size > 1,
                    language = entry.language,
                    query = entry.query,
                    phrase = entry.phrase,
                    canTranslate = selectionOutlet.canTranslate,
                ),
            )
        }
        if (entry.phrase) {
            loadWikipedia()
            return
        }
        if (cached != null) return
        scope.launch {
            val result = runCatching { lookupWord(entry.word, entry.language) }.getOrDefault(DictionaryResult.Error)
            lookupCache[entry.cacheKey] = result
            _state.update { currentState ->
                val current = currentState.lookup ?: return@update currentState
                if (current.word != entry.word || current.language != entry.language) return@update currentState
                currentState.copy(lookup = current.copy(loading = false, result = result))
            }
        }
    }

    private fun selectLookupTab(tab: LookupTab) {
        val current = _state.value.lookup ?: return
        if (current.phrase || current.tab == tab) return
        _state.update { it.copy(lookup = current.copy(tab = tab)) }
        if (tab == LookupTab.Wikipedia && current.wikipedia == null) loadWikipedia()
    }

    private fun retryWikipedia() {
        val current = _state.value.lookup ?: return
        if (current.wikipedia?.result !is WikipediaResult.Error) return
        wikipediaCache.remove(wikipediaKey(current))
        loadWikipedia()
    }

    // Wikipedia is fetched only from here: when its tab is opened, or a phrase is looked up.
    private fun loadWikipedia() {
        val current = _state.value.lookup ?: return
        val key = wikipediaKey(current)
        val cached = wikipediaCache[key]
        _state.update {
            it.copy(lookup = current.copy(tab = LookupTab.Wikipedia, wikipedia = WikipediaUiState(loading = cached == null, result = cached)))
        }
        if (cached != null) return
        scope.launch {
            val result = runCatching { lookupWikipedia(current.query, current.language) }.getOrDefault(WikipediaResult.Error)
            if (result !is WikipediaResult.Error) wikipediaCache[key] = result
            _state.update { currentState ->
                val shown = currentState.lookup ?: return@update currentState
                if (wikipediaKey(shown) != key) return@update currentState
                currentState.copy(lookup = shown.copy(wikipedia = WikipediaUiState(loading = false, result = result)))
            }
        }
    }

    private fun wikipediaKey(lookup: WordLookupUiState) = "${lookup.language}:${lookup.query}"

    private fun searchFor(text: String) {
        val query = text.trim().replace(WHITESPACE, " ")
        if (query.isEmpty()) return
        closeLookup()
        changeSearchQuery(query)
        submitSearch()
    }

    private fun speak(rawText: String) {
        rawText.trim().takeIf { it.isNotEmpty() }?.let(readAloud::speak)
    }

    private fun toggleBookmark() {
        val locator = _state.value.locator ?: return
        val bookmarked = !_state.value.currentPageBookmarked
        val chapterTitle = locator.chapterTitle ?: _state.value.chapterTitle
        mutateAnnotation("Bookmark change wasn't saved.") {
            bookmarks.setBookmarked(bookId, locator.bookmarkAnchor(), chapterTitle, bookmarked)
        }
    }

    private fun deleteBookmark(id: String) {
        val deleted = _state.value.bookmarks.firstOrNull { it.id == id }
        mutateAnnotation("Bookmark wasn't deleted.") { bookmarks.delete(id) }
        if (deleted != null) _state.update { it.copy(undo = AnnotationUndo.Bookmark(++undoKey, deleted)) }
    }

    private fun deleteEditingHighlight() {
        val target = _state.value.editingHighlight ?: return
        deleteHighlight(target.id)
    }

    private fun deleteHighlight(id: String) {
        // The sheet's copy is the freshest (a note just typed); the list's otherwise.
        val deleted = _state.value.editingHighlight?.takeIf { it.id == id }
            ?: _state.value.highlights.firstOrNull { it.id == id }
        if (deleted != null) _state.update { it.copy(undo = AnnotationUndo.Highlight(++undoKey, deleted)) }
        mutateAnnotation("Highlight wasn't deleted.") {
            highlights.delete(id)
            _state.update { if (it.editingHighlight?.id == id) it.copy(editingHighlight = null) else it }
        }
    }

    private fun setEditingHighlightColor(colorArgb: Int) {
        val target = _state.value.editingHighlight ?: return
        mutateAnnotation("Highlight color wasn't saved.") {
            highlights.updateColor(target.id, colorArgb)
            _state.update { if (it.editingHighlight?.id == target.id) it.copy(editingHighlight = target.copy(colorArgb = colorArgb)) else it }
        }
    }

    private fun setEditingHighlightStyle(style: HighlightStyle) {
        val target = _state.value.editingHighlight ?: return
        if (target.style == style) return
        _state.update { it.copy(editingHighlight = target.copy(style = style)) }
        mutateAnnotation("Highlight style wasn't saved.") { highlights.setStyle(target.id, style) }
    }

    private fun setHighlightNote(id: String, note: String) {
        val normalized = note.trim().takeIf { it.isNotEmpty() }
        val known = _state.value.editingHighlight?.takeIf { it.id == id } ?: _state.value.highlights.firstOrNull { it.id == id }
        if (known != null && known.note?.trim() == normalized) return
        _state.update { state ->
            state.editingHighlight?.takeIf { it.id == id }?.let { state.copy(editingHighlight = it.copy(note = normalized)) } ?: state
        }
        mutateAnnotation("Note wasn't saved.") { highlights.setNote(id, normalized) }
    }

    // Puts the row back with its own id, so sync sees the same highlight or bookmark return.
    private fun undoDelete(key: Long) {
        val undo = _state.value.undo?.takeIf { it.key == key } ?: return
        _state.update { it.copy(undo = null) }
        when (undo) {
            is AnnotationUndo.Highlight -> mutateAnnotation("Highlight wasn't restored.") { highlights.restore(bookId, undo.highlight) }
            is AnnotationUndo.Bookmark -> mutateAnnotation("Bookmark wasn't restored.") { bookmarks.restore(bookId, undo.bookmark) }
        }
    }

    private fun mutateAnnotation(message: String, persist: suspend () -> Unit) {
        annotationWrites.addLast(AnnotationWrite(message, persist))
        _state.update { it.copy(annotationsPending = true) }
        retryAnnotations()
    }

    private fun retryAnnotations() {
        if (annotationJob?.isActive == true) return
        _state.update { it.copy(annotationError = null) }
        annotationJob = scope.launch {
            while (annotationWrites.isNotEmpty()) {
                val next = annotationWrites.first()
                try {
                    next.persist()
                    annotationWrites.removeFirst()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _state.update { it.copy(annotationError = "${next.message} Retry to finish the change.") }
                    return@launch
                }
            }
            _state.update { it.copy(annotationsPending = false) }
        }
    }

    private fun resume() {
        foreground = true
        val active = session
        if (active != null) {
            active.onForegroundResumed()
            if (_state.value.renderState is ReaderRenderState.Ready && readingStartedAt == null) {
                readingStartedAt = nowMillis()
                readingStartedElapsed = elapsedMillis()
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
        if (jumps.onLocator(locator, elapsedMillis())) {
            jumpBackJob?.cancel()
            _state.update { it.copy(jumpBack = null) }
        }
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
                pageLabel = locator.pageLabel,
            ).withTimeLeft(locator)
        }

        val chapterChanged = currentChapterId != null && locator.chapterId != currentChapterId
        if (progression != null && opened.renderState.value is ReaderRenderState.Ready) {
            scheduleProgressSave(locator.toProgressSnapshot(progression))
            if (chapterChanged) persistPendingProgress()
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

    private fun dismissSyncOffer() {
        _state.update { it.copy(syncOffer = null) }
        finalWriteScope.launch { clearSyncOffer() }
    }

    /**
     * Captures the portable form of [locatorJson] shortly after the position settles, so progress
     * saves can carry it for other devices without a navigator call on every scroll frame.
     */
    private fun capturePortableLocator(locatorJson: String) {
        portableJob?.cancel()
        portableJob = scope.launch {
            delay(PORTABLE_CAPTURE_DELAY_MS)
            val portable = session?.portableLocator() ?: return@launch
            portableLocator = locatorJson to portable
        }
    }

    private fun scheduleProgressSave(snapshot: ReadingProgressSnapshot) {
        capturePortableLocator(snapshot.locatorJson)
        // Snapshots are coalesced, but only within one chapter: the previous chapter's last position
        // (often its final page) must be written before the next chapter's replaces it, or that
        // chapter is left at whatever an earlier periodic save recorded.
        if (pendingProgress?.chapterId != snapshot.chapterId) persistPendingProgress()
        pendingProgress = snapshot
        if (progressSaveJob?.isActive == true) return
        progressSaveJob = scope.launch {
            do {
                delay(progressSaveIntervalMillis)
                persistPendingProgress()
            } while (pendingProgress != null)
        }
    }

    private fun persistPendingProgress() {
        val portable = portableLocator?.takeIf { it.first == pendingProgress?.locatorJson }?.second
        val pending = pendingProgress?.copy(portableLocatorJson = portable) ?: return
        readingWrites.enqueue { books.saveProgress(bookId, pending) }
        pendingProgress = null
    }

    private fun flushPendingProgress() {
        progressSaveJob?.cancel()
        progressSaveJob = null
        persistPendingProgress()
    }

    private fun bankReadingSession() {
        val start = readingStartedAt ?: return
        readingStartedAt = null
        val seconds = (elapsedMillis() - readingStartedElapsed) / 1_000L
        if (seconds in 1..MAX_SESSION_SECONDS) {
            val operationId = UUID.randomUUID().toString()
            readingWrites.enqueue { recordReadingSession(bookId, start, seconds, operationId) }
        }
    }

    private fun isCurrent(generation: Long, opened: ReaderSession? = null): Boolean = !closed && generation == openGeneration && (opened == null || session === opened)

    private fun ReaderLocator.toProgressSnapshot(progression: Double): ReadingProgressSnapshot = ReadingProgressSnapshot(
        locatorJson = locatorJson,
        bookProgress = progression,
        currentHref = href,
        chapterId = chapterId,
        chapterProgress = chapterProgression,
        sharedChapterIds = sharedChapterIds,
        completedChapterId = completedChapterId,
        completedSharedChapterIds = completedSharedChapterIds,
        completed = completed,
    )

    /** One look-up in the sheet's history: a dictionary [word] in [language], or a short [phrase]. */
    private data class LookupEntry(val word: String, val language: String, val query: String, val phrase: Boolean = false) {
        val cacheKey: String get() = "$language:$word"
    }

    private companion object {
        const val DEFAULT_LOOKUP_LANGUAGE = "en"
        val WHITESPACE = Regex("\\s+")
        const val DEFAULT_HIGHLIGHT_ARGB = 0xFFE7C75B.toInt()
        const val MAX_SESSION_SECONDS = 24L * 60 * 60
        const val PROGRESS_SAVE_INTERVAL_MS = 4_000L
        const val PORTABLE_CAPTURE_DELAY_MS = 600L
        const val READY_TIMEOUT_MS = 8_000L

        // How long "Back to …" stays on offer without being taken or read past.
        const val JUMP_BACK_TIMEOUT_MS = 20_000L
        const val IMAGE_MESSAGE_MS = 2_500L
    }
}

/** The dictionary language for a selection's BCP 47 tag ("fr-CA" → "fr"); English when unknown. */
internal fun lookupLanguage(tag: String?): String {
    val primary = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase().orEmpty()
    return primary.takeIf { it.length in 2..3 && it.all(Char::isLetter) } ?: "en"
}
