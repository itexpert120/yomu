package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recorded reading session: which book, when it started (epoch millis), and how long it lasted.
 * This is the reading-history log; per-day totals ([ReadingDayEntity]) are an incremental rollup of
 * these for cheap streak/total queries. Sessions sync between devices; [bookId] may be a
 * `sync:<sha256>` placeholder for a book this device doesn't have yet.
 */
@Entity(tableName = "reading_sessions", indices = [Index(value = ["uuid"], unique = true)])
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val startedAt: Long,
    val seconds: Long,
    // Global id (also the write's idempotency key) so the session syncs exactly once everywhere.
    val uuid: String? = null,
    // The book's content hash, kept so the session still identifies its book after local removal.
    val bookSha256: String? = null,
)
