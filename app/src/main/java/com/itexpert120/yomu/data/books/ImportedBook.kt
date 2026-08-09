package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.core.reader.ReaderPublicationCache

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
    val tableOfContents: List<ReaderTocItem>?,
    val publicationCache: ReaderPublicationCache? = null,
)
