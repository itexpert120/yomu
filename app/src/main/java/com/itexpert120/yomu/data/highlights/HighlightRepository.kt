package com.itexpert120.yomu.data.highlights

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderHighlight
import kotlinx.coroutines.flow.Flow

/** Persists and observes a book's text highlights. Highlights cross as Yomu-owned models. */
interface HighlightRepository {
    fun observeForBook(bookId: BookId): Flow<List<ReaderHighlight>>
    suspend fun add(bookId: BookId, locatorJson: String, text: String, colorArgb: Int): ReaderHighlight
    suspend fun updateColor(id: String, colorArgb: Int)

    /** Sets or clears (null or blank) a highlight's note. */
    suspend fun setNote(id: String, note: String?): Unit = throw UnsupportedOperationException("setNote")

    suspend fun setStyle(id: String, style: HighlightStyle): Unit = throw UnsupportedOperationException("setStyle")

    suspend fun delete(id: String)

    /** Puts a deleted [highlight] back as it was (same id), e.g. for Undo. */
    suspend fun restore(bookId: BookId, highlight: ReaderHighlight): Unit = throw UnsupportedOperationException("restore")
}
