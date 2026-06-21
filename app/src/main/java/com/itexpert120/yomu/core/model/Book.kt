package com.itexpert120.yomu.core.model

/** Strongly typed book identifier (docs/data-model.md "Identity Types"). */
@JvmInline
value class BookId(val value: String)

enum class ReadingState { Unread, Reading, Finished }

/**
 * Domain model for a book in the library. Kept free of Compose/Android types so it backs a Room
 * entity directly. [coverImagePath] points at an extracted cover file when available; otherwise
 * the UI falls back to a gradient generated from [coverPalette].
 */
data class Book(
    val id: BookId,
    val title: String,
    val author: String,
    val subtitle: String? = null,
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val series: String? = null,
    val coverImagePath: String? = null,
    val coverPalette: List<Long> = emptyList(),
    val progress: Float = 0f,
    val remainingLabel: String = "Unread",
    // Current reading position: the resource href being read and how far through it (0..1).
    val currentHref: String? = null,
    val currentChapterProgress: Float? = null,
    val addedAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
    // Reading-timeline timestamps (epoch millis); 0 = not started / not finished yet.
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
) {
    val readingState: ReadingState
        get() = when {
            progress >= 1f -> ReadingState.Finished
            progress > 0f -> ReadingState.Reading
            else -> ReadingState.Unread
        }
}
