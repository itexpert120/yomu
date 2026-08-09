package com.itexpert120.yomu.data.settings

import com.itexpert120.yomu.core.model.LibraryPreferences
import com.itexpert120.yomu.core.model.LibraryViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPrefsRepositoryTest {
    @Test
    fun legacyGridModeMigratesToComfortableGrid() {
        assertEquals(LibraryViewMode.ComfortableGrid, "Grid".toLibraryViewMode())
        assertEquals(LibraryViewMode.CompactGrid, "CompactGrid".toLibraryViewMode())
        assertEquals(LibraryViewMode.ComfortableGrid, "unknown".toLibraryViewMode())
    }

    @Test
    fun legacyColumnsSeedBothOrientationsUntilOverridden() {
        assertEquals(5, resolveLibraryColumns(value = null, legacy = 5, fallback = 0))
        assertEquals(3, resolveLibraryColumns(value = 3, legacy = 5, fallback = 0))
        assertEquals(
            LibraryPreferences.AUTO_COLUMNS,
            resolveLibraryColumns(value = null, legacy = null, fallback = LibraryPreferences.AUTO_COLUMNS),
        )
    }

    @Test
    fun invalidColumnValuesAreClamped() {
        assertEquals(LibraryPreferences.AUTO_COLUMNS, (-1).coerceColumns())
        assertEquals(LibraryPreferences.MIN_COLUMNS, 1.coerceColumns())
        assertEquals(LibraryPreferences.MAX_COLUMNS, 99.coerceColumns())
        assertEquals(4, 4.coerceColumns())
    }
}
