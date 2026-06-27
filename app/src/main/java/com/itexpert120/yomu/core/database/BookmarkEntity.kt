package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted reading-position bookmark. [id] is an app-generated UUID. [locatorJson] is the
 * engine-native position the bookmark is anchored to; [progression] is whole-book progression
 * (0..1), used for ordering and current-page detection. Indexed by [bookId] so a book's bookmarks
 * load cheaply.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId"])],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locatorJson: String,
    val href: String?,
    val chapterTitle: String?,
    val progression: Double,
    val createdAt: Long,
)
