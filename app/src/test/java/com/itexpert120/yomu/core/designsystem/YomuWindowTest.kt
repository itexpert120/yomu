package com.itexpert120.yomu.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YomuWindowTest {
    @Test
    fun twoPaneBreakpointUsesAvailableWidth() {
        assertFalse(599.dp.supportsYomuTwoPane())
        assertTrue(720.dp.supportsYomuTwoPane())
        assertTrue(1280.dp.supportsYomuTwoPane())
    }

    @Test
    fun leadingPaneUsesHalfUntilThe450DpCap() {
        assertEquals(360.dp, yomuLeadingPaneWidth(720.dp))
        assertEquals(450.dp, yomuLeadingPaneWidth(900.dp))
        assertEquals(450.dp, yomuLeadingPaneWidth(1280.dp))
    }

    @Test
    fun navigationWidthClassActivatesAt600Dp() {
        assertEquals(YomuWidthClass.Compact, YomuWidthClass.fromWidth(599.dp))
        assertEquals(YomuWidthClass.Medium, YomuWidthClass.fromWidth(600.dp))
    }
}
