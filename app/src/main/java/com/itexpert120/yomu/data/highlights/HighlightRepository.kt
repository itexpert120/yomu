package com.itexpert120.yomu.data.highlights

import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderHighlight
import kotlinx.coroutines.flow.Flow

/** Persists and observes a book's text highlights. Highlights cross as Yomu-owned models. */
interface HighlightRepository {
    fun observeForBook(bookId: BookId): Flow<List<ReaderHighlight>>
    suspend fun add(bookId: BookId, locatorJson: String, text: String, colorArgb: Int): ReaderHighlight
    suspend fun updateColor(id: String, colorArgb: Int)
    suspend fun delete(id: String)
}
