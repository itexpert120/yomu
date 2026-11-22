package com.itexpert120.yomu.core.model

/**
 * The one status a book shows everywhere (library tabs, Book Details, Organize, Statistics).
 * To read / Reading / Finished follow book progress; On hold and Dropped come from a
 * [ManualStatus], which always wins.
 */
enum class ShelfStatus(val label: String) {
    ToRead("To read"),
    Reading("Reading"),
    Finished("Finished"),
    OnHold("On hold"),
    Dropped("Dropped"),
    ;

    companion object {
        /** The only place the precedence lives: a manual status wins over book progress. */
        fun of(progress: Float, manualStatus: ManualStatus?): ShelfStatus = manualStatus?.shelfStatus ?: when {
            // Progress is capped below 1 until the book's real end is reached.
            progress >= 1f -> Finished
            progress > 0f -> Reading
            else -> ToRead
        }
    }
}

val Book.shelfStatus: ShelfStatus get() = ShelfStatus.of(progress, manualStatus)
