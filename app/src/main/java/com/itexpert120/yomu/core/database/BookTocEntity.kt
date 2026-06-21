package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached table of contents for a book, stored as a JSON array of TOC entries. The TOC is derived
 * from the (immutable) EPUB, so once extracted it never changes — caching it avoids re-opening the
 * publication and re-flattening the tree every time Book Details or the reader is opened.
 */
@Entity(tableName = "book_toc")
data class BookTocEntity(
    @PrimaryKey val bookId: String,
    val json: String,
)
