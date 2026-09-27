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
    fun leadingPaneFollowsMaterialPaneWidths() {
        // Medium: an even split of the space left after two 24dp margins and one 24dp spacer.
        assertEquals(324.dp, yomuLeadingPaneWidth(720.dp))
        // Expanded: fixed 360dp pane. Large: fixed 412dp pane.
        assertEquals(360.dp, yomuLeadingPaneWidth(900.dp))
        assertEquals(412.dp, yomuLeadingPaneWidth(1280.dp))
    }

    @Test
    fun widthClassesMatchMaterialBreakpoints() {
        assertEquals(YomuWidthClass.Compact, YomuWidthClass.fromWidth(599.dp))
        assertEquals(YomuWidthClass.Medium, YomuWidthClass.fromWidth(600.dp))
        assertEquals(YomuWidthClass.Expanded, YomuWidthClass.fromWidth(840.dp))
        assertEquals(YomuWidthClass.Large, YomuWidthClass.fromWidth(1200.dp))
        assertEquals(16.dp, YomuWidthClass.Compact.margin)
        assertEquals(24.dp, YomuWidthClass.Medium.margin)
    }
}
