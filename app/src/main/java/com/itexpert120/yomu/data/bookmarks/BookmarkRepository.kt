package com.itexpert120.yomu.data.bookmarks

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.BookmarkAnchor
import com.itexpert120.yomu.core.reader.ReaderBookmark
import kotlinx.coroutines.flow.Flow

/** Persists and observes a book's reading-position bookmarks. Bookmarks cross as Yomu-owned models. */
interface BookmarkRepository {
    fun observeForBook(bookId: BookId): Flow<List<ReaderBookmark>>

    /**
     * Makes the page at [at] bookmarked or not. Idempotent, so a retried write cannot duplicate or
     * flip it: bookmarking keeps an existing bookmark there, unbookmarking removes all of them.
     */
    suspend fun setBookmarked(
        bookId: BookId,
        at: BookmarkAnchor,
        chapterTitle: String?,
        bookmarked: Boolean,
    )

    suspend fun delete(id: String)

    /** Puts a deleted [bookmark] back as it was (same id), e.g. for Undo. */
    suspend fun restore(bookId: BookId, bookmark: ReaderBookmark): Unit = throw UnsupportedOperationException("restore")
}
