package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One persisted text highlight. [id] is an app-generated UUID (mirrors the engine [Decoration] id).
 * [locatorJson] is the engine-native locator the highlight is anchored to; [colorArgb] is a packed
 * ARGB int. [style] is a `HighlightStyle.key`, kept verbatim so a style written by a newer build
 * survives here and syncs back unchanged. [modifiedAt] is the last colour/style/note edit (0 = none).
 * Indexed by [bookId] so a book's highlights load cheaply.
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
    val note: String? = null,
    val style: String = DEFAULT_HIGHLIGHT_STYLE,
    val modifiedAt: Long = 0,
)

/** `HighlightStyle.Highlight.key`; the column default for rows from before styles existed. */
const val DEFAULT_HIGHLIGHT_STYLE = "highlight"
