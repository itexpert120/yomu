package com.itexpert120.yomu.core.database

import androidx.room.Entity

/** Highest reading percentage reached for one logical TOC section. */
@Entity(tableName = "chapter_progress", primaryKeys = ["bookId", "chapterId"])
data class ChapterProgressEntity(
    val bookId: String,
    val chapterId: String,
    val progress: Float,
    val updatedAt: Long,
    val manuallyRead: Boolean = false,
)
