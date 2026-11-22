package com.itexpert120.yomu.data.reader

import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.PublicationIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic reader preloading: warms the rendering engine and opens a book the user is about to
 * read (the Resume book, a book whose details are showing) before they tap, so the reader opens
 * without parsing. Callers fire it after their screen has settled; the engine keeps one book.
 */
@Singleton
class ReaderPreloader @Inject constructor(
    private val books: BookRepository,
    private val publications: PublicationIndex,
    private val engine: ReaderEngine,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Main thread. */
    fun warmUp() = engine.warmUp()

    fun preload(id: BookId) {
        scope.launch {
            val target = runCatching { books.readingTarget(id) }.getOrNull() ?: return@launch
            val cache = runCatching { publications.publicationCache(id) }.getOrNull()
            engine.prepare(target.storagePath, cache)
        }
    }
}
