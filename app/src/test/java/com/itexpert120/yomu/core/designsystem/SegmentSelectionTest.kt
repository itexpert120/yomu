package com.itexpert120.yomu.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentSelectionTest {
    @Test
    fun `a tap moves the chip at once and is handed over once`() {
        val s = SegmentSelection(0)
        assertTrue(s.tap(2))
        assertEquals(2, s.shown)
        assertEquals(2, s.takePending())
        assertNull(s.takePending())
    }

    @Test
    fun `tapping the shown option does nothing`() {
        val s = SegmentSelection(1)
        assertFalse(s.tap(1))
        assertNull(s.takePending())
    }

    @Test
    fun `quick taps coalesce into the last one`() {
        val s = SegmentSelection(0)
        s.tap(1)
        s.tap(3)
        s.tap(2)
        assertEquals(2, s.shown)
        assertEquals(2, s.takePending())
    }

    @Test
    fun `an older value reported late never pulls the chip back`() {
        val s = SegmentSelection(0)
        s.tap(1)
        s.takePending()
        s.tap(2)
        // The owner reports the first choice while the second is still on its way.
        s.external(1)
        assertEquals(2, s.shown)
        s.takePending()
        s.external(1)
        assertEquals(2, s.shown)
        s.external(2)
        assertEquals(2, s.shown)
        assertFalse(s.isAwaiting)
    }

    @Test
    fun `a change from the owner moves the chip when nothing is on its way`() {
        val s = SegmentSelection(0)
        s.external(3)
        assertEquals(3, s.shown)
    }

    @Test
    fun `a refused choice falls back to what the owner says`() {
        val s = SegmentSelection(0)
        s.tap(2)
        s.takePending()
        s.external(0)
        assertEquals(2, s.shown)
        s.giveUp()
        assertEquals(0, s.shown)
    }
}

class SheetReleaseTest {
    private val fling = 1000f

    @Test
    fun `a short drag springs back`() {
        assertFalse(sheetReleaseDismisses(offsetPx = 200f, extentPx = 600f, velocityPx = 0f, flingPx = fling))
    }

    @Test
    fun `a drag past the threshold dismisses`() {
        assertTrue(sheetReleaseDismisses(offsetPx = 220f, extentPx = 600f, velocityPx = 0f, flingPx = fling))
    }

    @Test
    fun `a fling toward the edge dismisses even from a short drag`() {
        assertTrue(sheetReleaseDismisses(offsetPx = 20f, extentPx = 600f, velocityPx = 1500f, flingPx = fling))
    }

    @Test
    fun `a fling back toward rest keeps the sheet`() {
        assertFalse(sheetReleaseDismisses(offsetPx = 300f, extentPx = 600f, velocityPx = -1500f, flingPx = fling))
    }

    @Test
    fun `a sheet at rest never dismisses`() {
        assertFalse(sheetReleaseDismisses(offsetPx = 0f, extentPx = 600f, velocityPx = 5000f, flingPx = fling))
    }
}
