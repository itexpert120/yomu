package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import java.util.concurrent.ConcurrentHashMap

/** [PublicationStore] test adapter: books are paths, the stored caches a map. */
internal class InMemoryPublicationStore(books: Map<BookId, String> = emptyMap()) : PublicationStore {
    private val paths = ConcurrentHashMap(books)
    val rows = ConcurrentHashMap<BookId, ReaderPublicationCache>()

    /** Deletes the book's rows, as the library's deletion transaction does. */
    fun delete(book: BookId) {
        paths.remove(book)
        rows.remove(book)
    }

    override suspend fun read(book: BookId): ReaderPublicationCache? = rows[book]

    override suspend fun write(book: BookId, cache: ReaderPublicationCache): Boolean = synchronized(this) {
        if (!paths.containsKey(book)) return false
        rows[book] = cache
        true
    }

    override suspend fun storagePath(book: BookId): String? = paths[book]
}
