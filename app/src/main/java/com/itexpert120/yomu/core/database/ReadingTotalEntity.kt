package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Lifetime rollup, deliberately independent of book deletion and recent-history retention. */
@Entity(tableName = "reading_totals")
data class ReadingTotalEntity(
    @PrimaryKey val bookId: String,
    val seconds: Long,
    val sessionCount: Int,
    val longestSeconds: Long,
)
