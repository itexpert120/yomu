package com.itexpert120.yomu

import com.itexpert120.yomu.core.database.YomuDatabase
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseVersionTest {
    @Test
    fun schemaVersionNeverRegressesBelowFourteen() {
        assertTrue(YomuDatabase.VERSION >= 14)
    }
}
