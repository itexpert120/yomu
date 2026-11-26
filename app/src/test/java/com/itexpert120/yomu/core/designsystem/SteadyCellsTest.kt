package com.itexpert120.yomu.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteadyCellsTest {
    @Test
    fun `at full width the cells are Adaptive's, in whole pixels`() {
        val cells = steadyCellSizes(available = 1000, fullExtra = 0, minSizePx = 140, spacing = 20)
        assertEquals(6, cells.size)
        assertEquals(List(6) { (1000 - 20 * 5) / 6 }, cells)
    }

    @Test
    fun `a narrower layout fits fewer cells of exactly the full-width size`() {
        val full = steadyCellSizes(available = 1000, fullExtra = 0, minSizePx = 140, spacing = 20)
        val narrow = steadyCellSizes(available = 600, fullExtra = 352, minSizePx = 140, spacing = 20, fullCellPx = full.first())
        assertTrue(narrow.size < full.size)
        assertTrue(narrow.all { it == full.first() })
        assertTrue(narrow.sum() + 20 * (narrow.size - 1) <= 600)
    }

    @Test
    fun `back at full width the layout is the same as before`() {
        val before = steadyCellSizes(available = 1000, fullExtra = 0, minSizePx = 140, spacing = 20)
        steadyCellSizes(available = 600, fullExtra = 352, minSizePx = 140, spacing = 20, fullCellPx = before.first())
        val after = steadyCellSizes(available = 1000, fullExtra = 0, minSizePx = 140, spacing = 20, fullCellPx = before.first())
        assertEquals(before, after)
    }
}
