package com.itexpert120.yomu.app.navigation

import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.feature.bookdetails.bookPaneWidth
import org.junit.Assert.assertEquals
import org.junit.Test

/** The library's visible edge beside a book pane, on a 1280dp-wide window. */
class LibraryEdgeTest {
    private fun edgeDp(density: Float, openness: Float): Float {
        val windowDp = 1280.dp
        val fullPx = (windowDp.value * density).toInt()
        val roomPx = (bookPaneWidth(windowDp).value + 16f) * density
        return libraryVisibleEdge(fullPx, roomPx, openness) / density
    }

    @Test
    fun `closed the library runs the full width`() {
        assertEquals(1280f, edgeDp(2f, 0f), 0.5f)
        assertEquals(1280f, edgeDp(3f, 0f), 0.5f)
    }

    @Test
    fun `open it ends 16dp before the 400dp pane, at any density`() {
        assertEquals(864f, edgeDp(2f, 1f), 0.5f)
        assertEquals(864f, edgeDp(3f, 1f), 0.5f)
    }

    @Test
    fun `half open it is half way`() {
        assertEquals(1072f, edgeDp(2f, 0.5f), 0.5f)
        assertEquals(1072f, edgeDp(3f, 0.5f), 0.5f)
    }
}
