package com.itexpert120.yomu.app.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderEntranceTest {
    @Test
    fun `the pile resumes with a sheet rising from the library`() {
        assertEquals(ReaderEntrance.Library, readerEntrance(toReader = true, fromLibrary = true, fromBook = false, bookIsPhoneSheet = true))
        assertEquals(ReaderEntrance.Library, readerEntrance(toReader = true, fromLibrary = true, fromBook = false, bookIsPhoneSheet = false))
    }

    @Test
    fun `a book's phone sheet carries on into the reader, a pane keeps the push`() {
        assertEquals(ReaderEntrance.BookSheet, readerEntrance(toReader = true, fromLibrary = false, fromBook = true, bookIsPhoneSheet = true))
        assertNull(readerEntrance(toReader = true, fromLibrary = false, fromBook = true, bookIsPhoneSheet = false))
    }

    @Test
    fun `only opening the reader plays the motion`() {
        assertNull(readerEntrance(toReader = false, fromLibrary = true, fromBook = false, bookIsPhoneSheet = true))
        assertNull(readerEntrance(toReader = false, fromLibrary = false, fromBook = true, bookIsPhoneSheet = true))
        assertNull(readerEntrance(toReader = true, fromLibrary = false, fromBook = false, bookIsPhoneSheet = true))
    }

    @Test
    fun `the page shows as soon as it is laid out`() = runTest {
        val ready = MutableStateFlow(false)
        val shown = async { awaitFirstPage(ready, graceMillis = 650) }
        advanceTimeBy(200)
        ready.value = true
        runCurrent()
        assertTrue(shown.isCompleted)
        assertTrue(shown.await())
        assertEquals(200, currentTime)
    }

    @Test
    fun `a slow open shows the reader's opening state after the grace period`() = runTest {
        val ready = MutableStateFlow(false)
        val shown = async { awaitFirstPage(ready, graceMillis = 650) }
        advanceTimeBy(649)
        assertFalse(shown.isCompleted)
        advanceTimeBy(2)
        assertTrue(shown.isCompleted)
        assertFalse(shown.await())
    }
}
