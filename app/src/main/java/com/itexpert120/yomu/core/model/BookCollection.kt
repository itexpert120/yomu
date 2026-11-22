package com.itexpert120.yomu.core.model

/** A user-made collection. A book can belong to any number of them. */
data class BookCollection(
    val id: String,
    val name: String,
    val position: Int,
)

/**
 * A status the reader sets by hand (stored by [name]). It wins over book progress in
 * [ShelfStatus.of]; null means "follow progress".
 */
enum class ManualStatus(val shelfStatus: ShelfStatus) {
    OnHold(ShelfStatus.OnHold),
    Dropped(ShelfStatus.Dropped),
    ;

    companion object {
        fun fromStoredName(name: String?): ManualStatus? = entries.firstOrNull { it.name == name }
    }
}
