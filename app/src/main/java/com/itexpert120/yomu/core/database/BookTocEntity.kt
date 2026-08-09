package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached reader metadata for a book. [json] remains the historical TOC JSON array; resource weights
 * were added later so old rows can migrate without rewriting their TOC payload.
 */
@Entity(tableName = "book_toc")
data class BookTocEntity(
    @PrimaryKey val bookId: String,
    val json: String,
    val resourceWeightsJson: String? = null,
)
