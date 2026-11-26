package com.itexpert120.yomu.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteadyCellsTest {
    @Test
    fun `at full width the cells fill the row like Adaptive`() {
        val cells = steadyCellSizes(available = 1000, fullExtra = 0, minSizePx = 140, spacing = 20)
        assertEquals(6, cells.size)
        assertEquals(1000 - 20 * 5, cells.sum())
    }

    @Test
    fun `a narrower layout fits fewer cells of the same size`() {
        val full = steadyCellSizes(available = 1000, fullExtra = 0, minSizePx = 140, spacing = 20)
        val narrow = steadyCellSizes(available = 600, fullExtra = 400, minSizePx = 140, spacing = 20)
        assertTrue(narrow.size < full.size)
        assertTrue(narrow.all { it == full.min() })
        assertTrue(narrow.sum() + 20 * (narrow.size - 1) <= 600)
    }
}
