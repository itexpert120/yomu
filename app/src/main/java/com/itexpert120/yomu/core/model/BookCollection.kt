package com.itexpert120.yomu.core.model

/** A user-made collection. A book can belong to any number of them. */
data class BookCollection(
    val id: String,
    val name: String,
    val position: Int,
)

/**
 * A status the reader sets by hand (stored by [name]). It wins over book progress in
 * [ShelfStatus.of]; null means "follow progress". A [pinned] status (On hold, Dropped) stays until
 * changed by hand; the others (To read, Reading, Finished) only place the book until reading moves
 * it on: the next change in progress returns it to Automatic.
 */
enum class ManualStatus(val shelfStatus: ShelfStatus, val pinned: Boolean) {
    ToRead(ShelfStatus.ToRead, pinned = false),
    Reading(ShelfStatus.Reading, pinned = false),
    Finished(ShelfStatus.Finished, pinned = false),
    OnHold(ShelfStatus.OnHold, pinned = true),
    Dropped(ShelfStatus.Dropped, pinned = true),
    ;

    companion object {
        fun fromStoredName(name: String?): ManualStatus? = entries.firstOrNull { it.name == name }
    }
}
