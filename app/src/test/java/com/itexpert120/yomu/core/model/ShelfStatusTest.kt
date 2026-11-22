package com.itexpert120.yomu.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfStatusTest {
    private fun status(progress: Float, manual: ManualStatus?) = Book(BookId("b"), "Book", "Author", progress = progress, manualStatus = manual).shelfStatus

    @Test
    fun progressDecidesWhenNoManualStatusIsSet() {
        assertEquals(ShelfStatus.ToRead, status(0f, null))
        assertEquals(ShelfStatus.Reading, status(0.4f, null))
        // Progress is capped at 0.999 until the book's real end, so that is still Reading.
        assertEquals(ShelfStatus.Reading, status(0.999f, null))
        assertEquals(ShelfStatus.Finished, status(1f, null))
    }

    @Test
    fun aManualStatusWinsOverEveryProgress() {
        for (progress in listOf(0f, 0.4f, 0.999f, 1f)) {
            assertEquals(ShelfStatus.OnHold, status(progress, ManualStatus.OnHold))
            assertEquals(ShelfStatus.Dropped, status(progress, ManualStatus.Dropped))
        }
    }

    @Test
    fun theNotStartedLabelIsToRead() {
        assertEquals("To read", ShelfStatus.ToRead.label)
        assertEquals(
            listOf("To read", "Reading", "Finished", "On hold", "Dropped"),
            ShelfStatus.entries.map { it.label },
        )
    }
}
