package com.itexpert120.yomu.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterReadStateTest {
    @Test
    fun aReadRowOrProgressAtTheEndMeansRead() {
        val state = ChapterReadState.from(
            readRows = listOf("marked"),
            progressRows = mapOf("reached" to 1f, "almost" to 0.999f, "half" to 0.5f),
        )
        assertTrue(state.isRead("marked"))
        assertTrue(state.isRead("reached"))
        assertTrue(state.isRead("almost"))
        assertFalse(state.isRead("half"))
        assertFalse(state.isRead("untouched"))
        assertEquals(3, state.readCount)
    }

    @Test
    fun aSyncMergedReadRowWithPartialProgressIsReadEverywhere() {
        // First-contact merge keeps "read" from one device and the other's furthest progress.
        val state = ChapterReadState.from(listOf("ch"), mapOf("ch" to 0.4f))
        assertTrue(state.isRead("ch"))
        assertEquals(1f, state.percent("ch"))
        assertEquals(1, state.readCount)
        assertTrue(ChapterReadState.isRead(hasReadRow = true, progress = 0.4f))
    }

    @Test
    fun percentIsTheFurthestProgressUntilRead() {
        val state = ChapterReadState.from(emptyList(), mapOf("half" to 0.5f, "over" to 1.2f, "under" to -0.1f))
        assertEquals(0.5f, state.percent("half"))
        assertEquals(1f, state.percent("over"))
        assertEquals(0f, state.percent("under"))
        assertEquals(0f, state.percent("untouched"))
        assertFalse(ChapterReadState.isRead(hasReadRow = false, progress = null))
    }

    @Test
    fun markingUnreadClearsBothSignals() {
        // Mark unread deletes the read row and zeroes progress; nothing then counts as read.
        val state = ChapterReadState.from(emptyList(), mapOf("ch" to 0f))
        assertFalse(state.isRead("ch"))
        assertEquals(0, state.readCount)
    }
}
