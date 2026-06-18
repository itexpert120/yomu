package com.itexpert120.yomu.data.books

/** Payload produced by the import pipeline and inserted into the library. */
data class ImportedBook(
    val id: String,
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
    val addedAt: Long,
)
