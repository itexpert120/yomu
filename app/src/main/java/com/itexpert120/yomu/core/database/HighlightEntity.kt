package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted text highlight. [id] is an app-generated UUID (mirrors the engine [Decoration] id).
 * [locatorJson] is the engine-native locator the highlight is anchored to; [colorArgb] is a packed
 * ARGB int. Indexed by [bookId] so a book's highlights load cheaply.
 */
@Entity(
    tableName = "highlights",
    indices = [Index(value = ["bookId"])],
)
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locatorJson: String,
    val text: String,
    val colorArgb: Int,
    val createdAt: Long,
)
