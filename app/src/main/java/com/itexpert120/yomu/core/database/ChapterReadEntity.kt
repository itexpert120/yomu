package com.itexpert120.yomu.core.database

import androidx.room.Entity

/**
 * One row per chapter the user has marked as read. Presence = read; absence = unread. The composite
 * primary key (bookId first) also serves lookups by book without a separate index.
 */
@Entity(tableName = "chapter_reads", primaryKeys = ["bookId", "chapterId"])
data class ChapterReadEntity(
    val bookId: String,
    val chapterId: String,
)
