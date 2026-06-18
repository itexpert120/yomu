package com.itexpert120.yomu.core.model

/** Strongly typed book identifier (docs/data-model.md "Identity Types"). */
@JvmInline
value class BookId(val value: String)

enum class ReadingState { Unread, Reading, Finished }

/**
 * Domain model for a book in the library. Kept free of Compose/Android types so it can later
 * back a Room entity unchanged. Cover colors are ARGB [Long]s (the prototype renders a gradient
 * placeholder); a real cover image path replaces these once import lands.
 */
data class Book(
    val id: BookId,
    val title: String,
    val author: String,
    val series: String? = null,
    val progress: Float = 0f,
    val remainingLabel: String = "Unread",
    val coverPalette: List<Long> = emptyList(),
    val lastOpenedAt: Long = 0L,
) {
    val readingState: ReadingState
        get() = when {
            progress >= 1f -> ReadingState.Finished
            progress > 0f -> ReadingState.Reading
            else -> ReadingState.Unread
        }
}
