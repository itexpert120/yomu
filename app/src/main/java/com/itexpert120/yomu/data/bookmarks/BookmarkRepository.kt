package com.itexpert120.yomu.data.bookmarks

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderBookmark
import kotlinx.coroutines.flow.Flow

/** Persists and observes a book's reading-position bookmarks. Bookmarks cross as Yomu-owned models. */
interface BookmarkRepository {
    fun observeForBook(bookId: BookId): Flow<List<ReaderBookmark>>
    suspend fun toggle(
        bookId: BookId,
        locatorJson: String,
        href: String?,
        chapterTitle: String?,
        progression: Double?,
    ): Boolean
    suspend fun delete(id: String)
}
