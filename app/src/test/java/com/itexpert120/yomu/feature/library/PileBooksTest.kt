package com.itexpert120.yomu.feature.library

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PileBooksTest {
    private fun book(id: String, progress: Float, opened: Long, manual: ManualStatus? = null) = Book(id = BookId(id), title = id, author = "", progress = progress, lastOpenedAt = opened, manualStatus = manual)

    @Test
    fun onlyBooksInProgressMakeThePile() {
        val books = listOf(
            book("openedAtTheStart", progress = 0f, opened = 50),
            book("reading", progress = 0.4f, opened = 10),
            book("finished", progress = 1f, opened = 40),
            book("onHold", progress = 0.3f, opened = 30, manual = ManualStatus.OnHold),
            book("setToReading", progress = 0f, opened = 0, manual = ManualStatus.Reading),
            book("recent", progress = 0.1f, opened = 20),
        )
        assertEquals(listOf("recent", "reading", "setToReading"), books.pileBooks().map { it.id.value })
    }

    @Test
    fun noBookInProgressLeavesThePileEmpty() {
        val books = listOf(book("new", progress = 0f, opened = 5), book("done", progress = 1f, opened = 6))
        assertEquals(emptyList<Book>(), books.pileBooks())
    }
}
