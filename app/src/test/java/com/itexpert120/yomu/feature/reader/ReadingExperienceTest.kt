package com.itexpert120.yomu.feature.reader

import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ReaderSettings
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderHighlightDraft
import com.itexpert120.yomu.core.reader.ReaderLocator
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import com.itexpert120.yomu.core.reader.ReaderRenderState
import com.itexpert120.yomu.core.reader.ReaderSearchResult
import com.itexpert120.yomu.core.reader.ReaderSession
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.bookmarks.BookmarkRepository
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.ImportedBook
import com.itexpert120.yomu.data.books.ReadingProgressSnapshot
import com.itexpert120.yomu.data.books.ReadingTarget
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.highlights.HighlightRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingExperienceTest {
    @Test
    fun openPublishesNavigatorAndPersistsPublicationCacheAfterReady() = runBlocking {
        val session = FakeReaderSession()
        val cache = ReaderPublicationCache(resourceWeights = mapOf("chapter.xhtml" to 1))
        val engine = FakeReaderEngine(ReaderOpenResult(session, cache))
        val books = FakeBookRepository()
        val experience = createExperience(engine, books, scope = this)

        try {
            experience.start()
            awaitCondition { experience.state.value.navigator === session }

            assertEquals("book.epub", engine.requests.single().filePath)
            assertEquals("Stored title", experience.state.value.title)
            assertEquals(session.tableOfContents, experience.state.value.tableOfContents)

            session.renderState.value = ReaderRenderState.Ready("chapter.xhtml")
            awaitCondition { experience.state.value.renderState is ReaderRenderState.Ready }
            awaitCondition { books.cachedPublication == cache }

            assertFalse(experience.state.value.loading)
            assertFalse(experience.state.value.failed)
        } finally {
            experience.close()
        }
    }

    @Test
    fun failedOpenCanRetry() = runBlocking {
        val session = FakeReaderSession()
        val engine = FakeReaderEngine(null, ReaderOpenResult(session, ReaderPublicationCache()))
        val experience = createExperience(engine, scope = this)

        try {
            experience.start()
            awaitCondition { experience.state.value.failed }

            assertNull(experience.state.value.navigator)
            experience.dispatch(ReadingExperienceAction.RetryOpen)
            awaitCondition { experience.state.value.navigator === session }

            assertEquals(2, engine.requests.size)
            assertFalse(experience.state.value.failed)
        } finally {
            experience.close()
        }
    }

    @Test
    fun readinessTimeoutClosesStaleSessionAndRetryOwnsSubsequentState() = runBlocking {
        val stale = FakeReaderSession()
        val active = FakeReaderSession()
        val engine = FakeReaderEngine(
            ReaderOpenResult(stale, ReaderPublicationCache()),
            ReaderOpenResult(active, ReaderPublicationCache()),
        )
        val experience = createExperience(
            engine = engine,
            scope = this,
            readyTimeoutMillis = 20L,
        )

        try {
            experience.start()
            awaitCondition { experience.state.value.navigator === stale }
            awaitCondition { experience.state.value.failed }
            assertTrue(stale.closed)

            experience.dispatch(ReadingExperienceAction.RetryOpen)
            awaitCondition { experience.state.value.navigator === active }
            active.renderState.value = ReaderRenderState.Ready("active.xhtml")
            awaitCondition { experience.state.value.renderState == ReaderRenderState.Ready("active.xhtml") }

            stale.renderState.value = ReaderRenderState.Ready("stale.xhtml")
            yield()

            assertSame(active, experience.state.value.navigator)
            assertEquals(ReaderRenderState.Ready("active.xhtml"), experience.state.value.renderState)
            assertFalse(experience.state.value.failed)
        } finally {
            experience.close()
        }
    }

    @Test
    fun settingsAndNavigationCrossOnlyTheExperienceInterface() = runBlocking {
        val persisted = mutableListOf<ReaderSettings>()
        var resets = 0
        val session = FakeReaderSession()
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            scope = this,
            persistSettings = { persisted += it },
            resetSettings = { resets++ },
        )

        try {
            experience.start()
            awaitCondition { experience.state.value.navigator === session }
            val changed = ReaderSettings(fontScale = 1.25f)

            experience.dispatch(ReadingExperienceAction.ApplySettings(changed))
            awaitCondition { persisted == listOf(changed) }
            experience.dispatch(
                ReadingExperienceAction.Navigate(ReaderNavigation.Locator("{target}")),
            )
            experience.dispatch(ReadingExperienceAction.Navigate(ReaderNavigation.NextChapter))
            experience.dispatch(ReadingExperienceAction.Navigate(ReaderNavigation.PreviousChapter))
            experience.dispatch(ReadingExperienceAction.ResetSettings)
            awaitCondition { resets == 1 }

            assertEquals(changed, experience.state.value.settings)
            assertEquals(changed, session.appliedSettings.last())
            assertEquals(listOf("{target}"), session.visitedLocators)
            assertEquals(1, session.nextChapterCalls)
            assertEquals(1, session.previousChapterCalls)
        } finally {
            experience.close()
        }
    }

    @Test
    fun searchOwnsResultsDecorationsErrorsAndClear() = runBlocking {
        val session = FakeReaderSession()
        val result = ReaderSearchResult(
            locatorJson = "{result}",
            before = "before",
            match = "word",
            after = "after",
            chapterTitle = "Chapter one",
        )
        session.searchResults = listOf(result)
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            scope = this,
        )

        try {
            experience.start()
            awaitCondition { experience.state.value.navigator === session }
            experience.dispatch(ReadingExperienceAction.ChangeSearchQuery(" word "))
            experience.dispatch(ReadingExperienceAction.SubmitSearch)
            awaitCondition { experience.state.value.searchResults == listOf(result) }

            assertEquals(listOf("word"), session.searchQueries)
            assertEquals(listOf(result), session.searchDecorations)

            experience.dispatch(ReadingExperienceAction.ClearSearch)
            assertEquals("", experience.state.value.searchQuery)
            assertFalse(experience.state.value.searchPerformed)
            assertEquals(1, session.clearSearchCalls)

            session.searchFailure = true
            experience.dispatch(ReadingExperienceAction.ChangeSearchQuery("broken"))
            experience.dispatch(ReadingExperienceAction.SubmitSearch)
            awaitCondition { experience.state.value.searchError != null }

            assertSame(session, experience.state.value.navigator)
            assertFalse(experience.state.value.searchInProgress)
        } finally {
            experience.close()
        }
    }

    @Test
    fun editingRunningSearchEndsBusyStateAndRejectsOldResults() = runBlocking {
        val session = FakeReaderSession()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        session.searchWait = release
        session.searchResults = listOf(ReaderSearchResult("old", "", "A", "", null))
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            scope = this,
        )
        try {
            experience.start()
            awaitCondition { experience.state.value.navigator === session }
            experience.dispatch(ReadingExperienceAction.ChangeSearchQuery("A"))
            experience.dispatch(ReadingExperienceAction.SubmitSearch)
            awaitCondition { experience.state.value.searchInProgress }
            experience.dispatch(ReadingExperienceAction.ChangeSearchQuery("B"))
            assertFalse(experience.state.value.searchInProgress)
            release.complete(Unit)
            kotlinx.coroutines.yield()
            assertEquals("B", experience.state.value.searchQuery)
            assertTrue(experience.state.value.searchResults.isEmpty())
            assertTrue(session.searchDecorations.isEmpty())
        } finally {
            experience.close()
        }
    }

    @Test
    fun lookupHistoryAndPronunciationStayBookScoped() = runBlocking {
        val lookedUp = mutableListOf<String>()
        val session = FakeReaderSession()
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            scope = this,
            lookupWord = { word ->
                lookedUp += word
                DictionaryResult.NotFound
            },
        )

        try {
            experience.start()
            awaitCondition { session.lookUpEvents.subscriptionCount.value > 0 }
            session.lookUpEvents.emit("Hello!")
            awaitCondition { experience.state.value.lookup?.loading == false }

            assertEquals("hello", experience.state.value.lookup?.word)
            experience.dispatch(ReadingExperienceAction.LookUpWord("World"))
            awaitCondition {
                experience.state.value.lookup?.word == "world" &&
                    experience.state.value.lookup?.loading == false
            }
            assertTrue(experience.state.value.lookup?.canGoBack == true)

            experience.dispatch(ReadingExperienceAction.LookupBack)
            assertEquals("hello", experience.state.value.lookup?.word)
            experience.dispatch(ReadingExperienceAction.Pronounce(" hello "))

            assertEquals(listOf("hello", "world"), lookedUp)
            assertEquals(listOf("hello"), session.spoken)
        } finally {
            experience.close()
        }
    }

    @Test
    fun annotationsAndBookmarksRemainLiveWhenSecondaryWriteFails() = runBlocking {
        val highlights = FakeHighlightRepository()
        val bookmarks = FakeBookmarkRepository()
        val session = FakeReaderSession()
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            highlights = highlights,
            bookmarks = bookmarks,
            scope = this,
        )
        val existing = ReaderHighlight(
            id = "existing",
            locatorJson = "{highlight}",
            text = "marked",
            colorArgb = 1,
            createdAt = 1L,
        )
        highlights.items.value = listOf(existing)

        try {
            experience.start()
            awaitCondition { session.highlightRequestEvents.subscriptionCount.value > 0 }
            awaitCondition { session.appliedHighlights == listOf(existing) }

            session.highlightTapEvents.emit("existing")
            awaitCondition { experience.state.value.editingHighlight?.id == "existing" }
            experience.dispatch(ReadingExperienceAction.SetEditingHighlightColor(7))
            awaitCondition { highlights.updatedColors == listOf("existing" to 7) }

            highlights.failNextAdd = true
            session.highlightRequestEvents.emit(ReaderHighlightDraft("{failed}", "first"))
            session.highlightRequestEvents.emit(ReaderHighlightDraft("{saved}", "second"))
            awaitCondition { highlights.addedTexts == listOf("second") }

            session.currentLocator.value = locator()
            awaitCondition { experience.state.value.locator != null }
            experience.dispatch(ReadingExperienceAction.ToggleBookmark)
            awaitCondition { bookmarks.toggleCount == 1 }
            awaitCondition { experience.state.value.currentPageBookmarked }

            experience.dispatch(ReadingExperienceAction.DeleteBookmark("bookmark-1"))
            experience.dispatch(ReadingExperienceAction.DeleteEditingHighlight)
            awaitCondition { bookmarks.deletedIds == listOf("bookmark-1") }
            awaitCondition { "existing" in highlights.deletedIds }

            assertSame(session, experience.state.value.navigator)
        } finally {
            experience.close()
        }
    }

    @Test
    fun centerTapIsSurfacedWithoutExposingTheSession() = runBlocking {
        val session = FakeReaderSession()
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            scope = this,
        )
        val events = mutableListOf<ReadingExperienceEvent>()
        val eventJob = launch { experience.events.collect(events::add) }

        try {
            experience.start()
            awaitCondition { session.centerTapEvents.subscriptionCount.value > 0 }
            session.centerTapEvents.emit(Unit)
            awaitCondition { events.isNotEmpty() }

            assertEquals(listOf(ReadingExperienceEvent.ToggleChrome), events)
        } finally {
            eventJob.cancel()
            experience.close()
        }
    }

    @Test
    fun onlyForegroundReadyTimeIsRecorded() = runBlocking {
        var now = 1_000L
        val recorded = mutableListOf<RecordedSession>()
        val session = FakeReaderSession()
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            scope = this,
            nowMillis = { now },
            recordReadingSession = { bookId, startedAt, seconds ->
                recorded += RecordedSession(bookId, startedAt, seconds)
            },
        )

        try {
            experience.start()
            awaitCondition { experience.state.value.navigator === session }
            experience.dispatch(ReadingExperienceAction.Resume)
            now = 2_000L
            session.renderState.value = ReaderRenderState.Ready("chapter.xhtml")
            awaitCondition { experience.state.value.renderState is ReaderRenderState.Ready }

            now = 4_000L
            session.renderState.value = ReaderRenderState.Transitioning(forward = true)
            session.renderState.value = ReaderRenderState.Ready("chapter-two.xhtml")
            awaitCondition {
                experience.state.value.renderState == ReaderRenderState.Ready("chapter-two.xhtml")
            }
            now = 7_500L
            experience.dispatch(ReadingExperienceAction.Pause)
            awaitCondition { recorded.isNotEmpty() }

            assertEquals(RecordedSession(BOOK_ID, 2_000L, 5L), recorded.single())
        } finally {
            experience.close()
        }
    }

    @Test
    fun latestReadyPositionIsFlushedWhenExperienceCloses() = runBlocking {
        val books = FakeBookRepository()
        val session = FakeReaderSession()
        val experience = createExperience(
            engine = FakeReaderEngine(ReaderOpenResult(session, ReaderPublicationCache())),
            books = books,
            scope = this,
            progressSaveIntervalMillis = Long.MAX_VALUE,
        )

        experience.start()
        awaitCondition { experience.state.value.navigator === session }
        session.currentLocator.value = locator()
        awaitCondition { experience.state.value.locator != null }
        assertTrue(books.savedProgress.isEmpty())

        session.renderState.value = ReaderRenderState.Ready("chapter.xhtml")
        awaitCondition { experience.state.value.renderState is ReaderRenderState.Ready }
        experience.close()
        awaitCondition { books.savedProgress.isNotEmpty() }

        assertEquals(
            ReadingProgressSnapshot(
                locatorJson = "{locator}",
                bookProgress = 0.4,
                currentHref = "chapter.xhtml",
                chapterId = "chapter.xhtml",
                chapterProgress = 0.5,
                completed = false,
            ),
            books.savedProgress.single(),
        )
    }

    private fun createExperience(
        engine: ReaderEngine,
        books: BookRepository = FakeBookRepository(),
        highlights: HighlightRepository = FakeHighlightRepository(),
        bookmarks: BookmarkRepository = FakeBookmarkRepository(),
        scope: CoroutineScope,
        settings: Flow<ReaderSettings> = MutableStateFlow(ReaderSettings()),
        persistSettings: suspend (ReaderSettings) -> Unit = {},
        resetSettings: suspend () -> Unit = {},
        lookupWord: suspend (String) -> DictionaryResult = { DictionaryResult.NotFound },
        nowMillis: () -> Long = { 0L },
        progressSaveIntervalMillis: Long = 0L,
        readyTimeoutMillis: Long = 60_000L,
        recordReadingSession: suspend (BookId, Long, Long) -> Unit = { _, _, _ -> },
    ) = ReadingExperience(
        bookId = BOOK_ID,
        initialLocatorJson = null,
        settings = settings,
        persistSettings = persistSettings,
        resetSettings = resetSettings,
        lookupWord = lookupWord,
        engine = engine,
        books = books,
        highlights = highlights,
        bookmarks = bookmarks,
        ownerScope = scope,
        finalWriteScope = scope,
        recordReadingSession = recordReadingSession,
        nowMillis = nowMillis,
        progressSaveIntervalMillis = progressSaveIntervalMillis,
        readyTimeoutMillis = readyTimeoutMillis,
    )

    private fun locator() = ReaderLocator(
        locatorJson = "{locator}",
        totalProgression = 0.45,
        chapterTitle = "Chapter one",
        href = "chapter.xhtml",
        chapterProgression = 0.5,
        bookProgress = 0.4,
        chapterId = "chapter.xhtml",
        completed = false,
    )

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000L) {
            while (!condition()) yield()
        }
    }

    private data class RecordedSession(
        val bookId: BookId,
        val startedAt: Long,
        val seconds: Long,
    )

    private class FakeReaderEngine(vararg results: ReaderOpenResult?) : ReaderEngine {
        private val results = results.toMutableList()
        val requests = mutableListOf<ReaderOpenRequest>()

        override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? {
            requests += request
            return results.removeFirst()
        }

        override suspend fun tableOfContents(filePath: String): List<ReaderTocItem> = emptyList()
    }

    private class FakeReaderSession : ReaderSession {
        override val title = "Stored title"
        override val tableOfContents = listOf(
            ReaderTocItem(
                id = "chapter.xhtml",
                title = "Chapter one",
                locatorJson = "{chapter}",
                depth = 0,
            ),
        )
        override val currentLocator = MutableStateFlow<ReaderLocator?>(null)
        override val renderState = MutableStateFlow<ReaderRenderState>(ReaderRenderState.Opening)
        val centerTapEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        override val centerTaps: SharedFlow<Unit> = centerTapEvents
        val lookUpEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override val lookUpRequests: SharedFlow<String> = lookUpEvents
        val footnoteEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override val footnotes: SharedFlow<String> = footnoteEvents
        val highlightRequestEvents = MutableSharedFlow<ReaderHighlightDraft>(extraBufferCapacity = 8)
        override val highlightRequests: SharedFlow<ReaderHighlightDraft> = highlightRequestEvents
        val highlightTapEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override val highlightTaps: SharedFlow<String> = highlightTapEvents
        override val fragmentFactory = FragmentFactory()
        override val fragmentClassName = "FakeReaderFragment"
        val appliedSettings = mutableListOf<ReaderSettings>()
        val visitedLocators = mutableListOf<String>()
        val searchQueries = mutableListOf<String>()
        val spoken = mutableListOf<String>()
        var appliedHighlights = emptyList<ReaderHighlight>()
        var searchResults = emptyList<ReaderSearchResult>()
        var searchDecorations = emptyList<ReaderSearchResult>()
        var searchFailure = false
        var searchWait: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var nextChapterCalls = 0
        var previousChapterCalls = 0
        var clearSearchCalls = 0
        var closed = false

        override fun onFragmentHosted(fragmentManager: FragmentManager, tag: String) = Unit

        override fun applySettings(settings: ReaderSettings) {
            appliedSettings += settings
        }

        override fun refreshImmersiveLayout() = Unit

        override fun onForegroundResumed() = Unit

        override fun goForward() = Unit

        override fun goBackward() = Unit

        override fun nextChapter() {
            nextChapterCalls++
        }

        override fun previousChapter() {
            previousChapterCalls++
        }

        override fun goToProgression(totalProgression: Double) = Unit

        override fun goToLocator(locatorJson: String) {
            visitedLocators += locatorJson
        }

        override fun applyHighlights(highlights: List<ReaderHighlight>) {
            appliedHighlights = highlights
        }

        override suspend fun search(query: String): List<ReaderSearchResult> {
            searchQueries += query
            searchWait?.await()
            if (searchFailure) error("search failed")
            return searchResults
        }

        override fun applySearchDecorations(results: List<ReaderSearchResult>) {
            searchDecorations = results
        }

        override fun clearSearch() {
            clearSearchCalls++
        }

        override fun speak(text: String) {
            spoken += text
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeHighlightRepository : HighlightRepository {
        val items = MutableStateFlow<List<ReaderHighlight>>(emptyList())
        val addedTexts = mutableListOf<String>()
        val updatedColors = mutableListOf<Pair<String, Int>>()
        val deletedIds = mutableListOf<String>()
        var failNextAdd = false

        override fun observeForBook(bookId: BookId): Flow<List<ReaderHighlight>> = items

        override suspend fun add(
            bookId: BookId,
            locatorJson: String,
            text: String,
            colorArgb: Int,
        ): ReaderHighlight {
            if (failNextAdd) {
                failNextAdd = false
                error("write failed")
            }
            addedTexts += text
            return ReaderHighlight(
                id = "highlight-${addedTexts.size}",
                locatorJson = locatorJson,
                text = text,
                colorArgb = colorArgb,
                createdAt = addedTexts.size.toLong(),
            ).also { items.value = listOf(it) + items.value }
        }

        override suspend fun updateColor(id: String, colorArgb: Int) {
            updatedColors += id to colorArgb
            items.value = items.value.map {
                if (it.id == id) it.copy(colorArgb = colorArgb) else it
            }
        }

        override suspend fun delete(id: String) {
            deletedIds += id
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private class FakeBookmarkRepository : BookmarkRepository {
        private val items = MutableStateFlow<List<ReaderBookmark>>(emptyList())
        val deletedIds = mutableListOf<String>()
        var toggleCount = 0

        override fun observeForBook(bookId: BookId): Flow<List<ReaderBookmark>> = items

        override suspend fun toggle(
            bookId: BookId,
            locatorJson: String,
            href: String?,
            chapterTitle: String?,
            progression: Double?,
        ): Boolean {
            toggleCount++
            items.value = listOf(
                ReaderBookmark(
                    id = "bookmark-1",
                    locatorJson = locatorJson,
                    href = href,
                    chapterTitle = chapterTitle,
                    progression = progression,
                    createdAt = 1L,
                ),
            )
            return true
        }

        override suspend fun delete(id: String) {
            deletedIds += id
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private class FakeBookRepository : BookRepository {
        private val book = MutableStateFlow<Book?>(null)
        val savedProgress = mutableListOf<ReadingProgressSnapshot>()
        var cachedPublication: ReaderPublicationCache? = null

        override fun observeBooks(): Flow<List<Book>> = MutableStateFlow(emptyList())

        override fun observeBook(id: BookId): Flow<Book?> = book

        override suspend fun markRead(id: BookId) = Unit

        override suspend fun markUnread(id: BookId) = Unit

        override suspend fun remove(ids: List<BookId>) = Unit

        override suspend fun updateMetadata(
            id: BookId,
            title: String,
            subtitle: String?,
            author: String,
            description: String?,
            coverImagePath: String?,
        ) = Unit

        override suspend fun isDuplicate(sha256: String): Boolean = false

        override suspend fun findIdByHash(sha256: String): BookId? = null

        override suspend fun insert(book: ImportedBook): Boolean = true

        override suspend fun readingTarget(id: BookId) = ReadingTarget(
            storagePath = "book.epub",
            locatorJson = "{saved}",
            title = "Stored title",
        )

        override suspend fun saveProgress(id: BookId, snapshot: ReadingProgressSnapshot) {
            assertEquals(BOOK_ID, id)
            savedProgress += snapshot
        }

        override suspend fun recentBooks(limit: Int): List<Book> = emptyList()

        override suspend fun tableOfContents(id: BookId): List<ReaderTocItem> = emptyList()

        override suspend fun cacheTableOfContents(id: BookId, items: List<ReaderTocItem>) = Unit

        override suspend fun cachePublicationMetadata(
            id: BookId,
            cache: ReaderPublicationCache,
        ) {
            cachedPublication = cache
        }

        override fun cachedTableOfContents(id: BookId): List<ReaderTocItem>? = null

        override fun observeReadChapters(id: BookId): Flow<Set<String>> = MutableStateFlow(emptySet())

        override fun observeChapterProgress(id: BookId): Flow<Map<String, Float>> = MutableStateFlow(emptyMap())

        override suspend fun setChaptersRead(
            id: BookId,
            chapterIds: List<String>,
            read: Boolean,
        ) = Unit
    }

    private companion object {
        val BOOK_ID = BookId("book-id")
    }
}
