package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import kotlinx.coroutines.flow.Flow

/**
 * Library data source. Backed by [FakeBookRepository] for the prototype; a Room-backed
 * implementation replaces it in the data/import phase without changing callers.
 */
interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBook(id: BookId): Flow<Book?>
    suspend fun markRead(id: BookId)
    suspend fun remove(id: BookId)
}
