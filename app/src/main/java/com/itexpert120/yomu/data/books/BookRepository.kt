package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderTocItem
import kotlinx.coroutines.flow.Flow

/** What the reader needs to open a book: its file, last position, and title. */
data class ReadingTarget(
    val storagePath: String,
    val locatorJson: String?,
    val title: String,
)

/**
 * Library data source backed by Room ([RoomBookRepository]). The import pipeline inserts via
 * [insert]/[isDuplicate]; the UI reads via the observe* flows and mutates via the rest.
 */
interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBook(id: BookId): Flow<Book?>
    suspend fun markRead(id: BookId)
    suspend fun markUnread(id: BookId)
    suspend fun remove(ids: List<BookId>)
    suspend fun updateMetadata(
        id: BookId,
        title: String,
        subtitle: String?,
        author: String,
        description: String?,
        coverImagePath: String?,
    )

    suspend fun isDuplicate(sha256: String): Boolean

    /** The id of the already-imported book with this content hash, or null if none. */
    suspend fun findIdByHash(sha256: String): BookId?

    suspend fun insert(book: ImportedBook)

    suspend fun readingTarget(id: BookId): ReadingTarget?
    suspend fun saveProgress(id: BookId, locatorJson: String, totalProgression: Double)

    /**
     * The book to surface in the "Continue reading" home-screen widget: the most-recently-opened
     * in-progress book, or the most-recent book of any state as a fallback, or null when the library
     * is empty.
     */
    suspend fun continueReadingBook(): Book?

    /**
     * The book's table of contents, served from a persistent cache. On a cache miss it is extracted
     * from the EPUB and stored, so subsequent opens are instant. Also keeps a process-lifetime
     * in-memory copy ([cachedTableOfContents]) so repeat opens don't even re-read/parse from disk.
     */
    suspend fun tableOfContents(id: BookId): List<ReaderTocItem>

    /**
     * The in-memory TOC for [id] if it's already been loaded this session, else null. Synchronous so
     * callers can render instantly without a loading flash; fall back to [tableOfContents] on null.
     */
    fun cachedTableOfContents(id: BookId): List<ReaderTocItem>?

    /** Set of chapter ids the user has marked read for [id]. */
    fun observeReadChapters(id: BookId): Flow<Set<String>>

    /** Marks [chapterIds] read or unread for [id]. */
    suspend fun setChaptersRead(id: BookId, chapterIds: List<String>, read: Boolean)
}
