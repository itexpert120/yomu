package com.itexpert120.yomu.data.bookmarks

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderBookmark
import kotlinx.coroutines.flow.Flow

/** Persists and observes a book's reading-position bookmarks. Bookmarks cross as Yomu-owned models. */
interface BookmarkRepository {
    fun observeForBook(bookId: BookId): Flow<List<ReaderBookmark>>
    suspend fun add(
        bookId: BookId,
        locatorJson: String,
        href: String?,
        chapterTitle: String?,
        progression: Double,
    ): ReaderBookmark
    suspend fun delete(id: String)

    /** True when this book already has a bookmark on the current page (href + ~1% progression). */
    suspend fun existsAt(bookId: BookId, href: String?, progression: Double): Boolean

    /** Removes the current-page bookmark (the toggle-off path). */
    suspend fun deleteAt(bookId: BookId, href: String?, progression: Double)
}
