package com.itexpert120.yomu.app.navigation

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YomuScreenTransitionsTest {
    @Test
    fun `top-level moves are forward toward a later tab and back toward an earlier one`() {
        assertTrue(isForwardTopLevelMove(fromIndex = 0, targetIndex = 1))
        assertTrue(isForwardTopLevelMove(fromIndex = 0, targetIndex = 2))
        assertFalse(isForwardTopLevelMove(fromIndex = 2, targetIndex = 1))
        assertFalse(isForwardTopLevelMove(fromIndex = 1, targetIndex = 0))
        assertFalse(isForwardTopLevelMove(fromIndex = 1, targetIndex = 1))
    }

    @Test
    fun `moves involving a non top-level screen are forward`() {
        assertTrue(isForwardTopLevelMove(fromIndex = null, targetIndex = 0))
        assertTrue(isForwardTopLevelMove(fromIndex = 2, targetIndex = null))
        assertTrue(isForwardTopLevelMove(fromIndex = null, targetIndex = null))
    }

    @Test
    fun `physical direction follows the logical one in LTR and mirrors it in RTL`() {
        assertTrue(physicalDirection(forward = true, layoutDirection = LayoutDirection.Ltr))
        assertFalse(physicalDirection(forward = false, layoutDirection = LayoutDirection.Ltr))
        assertFalse(physicalDirection(forward = true, layoutDirection = LayoutDirection.Rtl))
        assertTrue(physicalDirection(forward = false, layoutDirection = LayoutDirection.Rtl))
    }
}
