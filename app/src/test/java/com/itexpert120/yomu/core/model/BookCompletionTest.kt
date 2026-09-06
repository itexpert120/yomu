package com.itexpert120.yomu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCompletionTest {
    @Test fun cappedProgressRemainsUnfinishedForBothPresentationAndStatistics() {
        val book = Book(BookId("b"), "Book", "Author", progress = 0.999f)
        assertEquals(ReadingState.Reading, book.readingState)
        assertFalse(isBookCompleted(book.progress))
        assertEquals(ReadingState.Finished, book.copy(progress = 1f).readingState)
        assertTrue(isBookCompleted(1f))
    }
}
