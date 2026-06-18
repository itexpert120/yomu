package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import kotlinx.coroutines.flow.Flow

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
}
