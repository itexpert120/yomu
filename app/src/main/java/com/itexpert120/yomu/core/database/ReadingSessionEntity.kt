package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One recorded reading session: which book, when it started (epoch millis), and how long it lasted.
 * This is the reading-history log; per-day totals ([ReadingDayEntity]) are an incremental rollup of
 * these for cheap streak/total queries.
 */
@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val startedAt: Long,
    val seconds: Long,
)
