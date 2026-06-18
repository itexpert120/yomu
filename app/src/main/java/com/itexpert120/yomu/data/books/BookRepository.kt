package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
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
    suspend fun insert(book: ImportedBook)

    suspend fun readingTarget(id: BookId): ReadingTarget?
    suspend fun saveProgress(id: BookId, locatorJson: String, totalProgression: Double)
}
