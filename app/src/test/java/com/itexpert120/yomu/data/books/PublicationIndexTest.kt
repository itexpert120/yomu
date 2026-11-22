package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import com.itexpert120.yomu.core.reader.ReaderTocItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublicationIndexTest {
    private val book = BookId("book")
    private val toc = listOf(ReaderTocItem("one.xhtml", "One", "{one}", 0), ReaderTocItem("two.xhtml", "Two", "{two}", 0))
    private val store = InMemoryPublicationStore(mapOf(book to "book.epub"))
    private val engine = CountingEngine(toc)

    @Test
    fun repeatObserveStartsReadyWithoutALoadingFlash() = runTest {
        val index = index()
        val first = index.observeToc(book)
        assertEquals(TocLoad.Loading, first.value)
        advanceUntilIdle()
        assertEquals(TocLoad.Ready(toc), first.value)

        assertEquals(TocLoad.Ready(toc), index.observeToc(book).value)
        assertEquals(1, engine.parses)
    }

    @Test
    fun aStoredTocIsServedWithoutParsing() = runTest {
        store.rows[book] = ReaderPublicationCache(toc = toc)
        val load = index().observeToc(book)
        advanceUntilIdle()
        assertEquals(TocLoad.Ready(toc), load.value)
        assertEquals(0, engine.parses)
    }

    @Test
    fun concurrentLoadsShareOneParse() = runTest {
        engine.gate = CompletableDeferred()
        val index = index()
        val first = index.observeToc(book)
        val second = index.observeToc(book)
        runCurrent()
        engine.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(TocLoad.Ready(toc), first.value)
        assertEquals(TocLoad.Ready(toc), second.value)
        assertEquals(1, engine.parses)
        assertEquals(ReaderPublicationCache(toc = toc), store.rows[book])
    }

    @Test
    fun aTocLoadRacingADeleteReturnsEmpty() = runTest {
        engine.gate = CompletableDeferred()
        val index = index()
        val load = index.observeToc(book)
        runCurrent()
        // The book is deleted while its EPUB is being parsed.
        store.delete(book)
        index.forget(listOf(book))
        engine.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(TocLoad.Ready(emptyList<ReaderTocItem>()), load.value)
        assertEquals(TocLoad.Ready(emptyList<ReaderTocItem>()), index.observeToc(book).value)
        assertNull(index.publicationCache(book))
        assertNull(store.rows[book])
    }

    // markRead relies on this: asking what the index has never parses the EPUB.
    @Test
    fun publicationCacheNeverParses() = runTest {
        val index = index()
        assertNull(index.publicationCache(book))
        assertEquals(0, engine.parses)
    }

    @Test
    fun rememberedPublicationPrimesTheNextObserve() = runTest {
        val index = index()
        val cache = ReaderPublicationCache(toc = toc, resourceWeights = mapOf("one.xhtml" to 3))
        index.remember(book, cache)

        assertEquals(TocLoad.Ready(toc), index.observeToc(book).value)
        assertEquals(cache, index.publicationCache(book))
        assertEquals(cache, store.rows[book])
        assertEquals(0, engine.parses)
    }

    @Test
    fun aForgottenBookIsNotRemembered() = runTest {
        val index = index()
        index.forget(listOf(book))
        index.remember(book, ReaderPublicationCache(toc = toc))

        assertNull(store.rows[book])
        assertEquals(TocLoad.Ready(emptyList<ReaderTocItem>()), index.observeToc(book).value)
    }

    @Test
    fun anUnreadableBookIsReadyAndEmpty() = runTest {
        engine.failing = true
        val load = index().observeToc(book)
        advanceUntilIdle()
        assertEquals(TocLoad.Ready(emptyList<ReaderTocItem>()), load.value)
    }

    private fun TestScope.index() = PublicationIndex(store, engine, this, StandardTestDispatcher(testScheduler))

    private class CountingEngine(private val toc: List<ReaderTocItem>) : ReaderEngine {
        var parses = 0
        var gate: CompletableDeferred<Unit>? = null
        var failing = false

        override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? = null

        override suspend fun tableOfContents(filePath: String): List<ReaderTocItem>? {
            parses++
            gate?.await()
            check(!failing) { "unreadable" }
            return toc
        }
    }
}
