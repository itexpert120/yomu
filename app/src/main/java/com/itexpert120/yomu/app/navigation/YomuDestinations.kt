package com.itexpert120.yomu.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
@Serializable
object Library

@Serializable
data class BookDetails(val bookId: String)

@Serializable
data class EditBook(val bookId: String)

@Serializable
object Settings

@Serializable
object Stats

@Serializable
object ReaderDefaults

@Serializable
object About

/**
 * Opens the reader for [bookId]. [locator] optionally overrides the restored position with a
 * specific location (e.g. a table-of-contents jump); null resumes the last saved position.
 */
@Serializable
data class Reader(val bookId: String, val locator: String? = null)
