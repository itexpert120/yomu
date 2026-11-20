package com.itexpert120.yomu.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per imported book. For v1 the single EPUB file's storage info is embedded here rather
 * than a separate BookFile table; split it out if alternate copies are ever supported.
 */
@Entity(
    tableName = "books",
    indices = [Index(value = ["sha256"], unique = true)],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String?,
    val author: String,
    val description: String?,
    val language: String?,
    val publisher: String?,
    val series: String?,
    val coverImagePath: String?,
    val storagePath: String,
    val originalUri: String?,
    val originalDisplayName: String?,
    val sha256: String,
    val fileSizeBytes: Long,
    val progress: Float,
    val totalProgression: Double?,
    val locatorJson: String?,
    val currentChapterId: String?,
    val addedAt: Long,
    val lastOpenedAt: Long,
    // Epoch millis for the reading timeline; 0 = not yet started / not yet finished.
    val startedAt: Long,
    val finishedAt: Long,
    // Layout-independent position (first visible element + its text) for resuming on other screens.
    val syncLocatorJson: String? = null,
    // When the user last edited title/author/etc.; 0 = untouched import metadata (never synced).
    val metadataEditedAt: Long = 0,
    // A ManualStatus name (On hold / Dropped), or null to follow reading progress.
    val manualStatus: String? = null,
    // When the book's collections or manual status last changed here; 0 = never (dates sync records).
    val shelfEditedAt: Long = 0,
)
