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
object About

/** Reader is declared now but routed to a placeholder until the reader feature lands. */
@Serializable
data class Reader(val bookId: String)
