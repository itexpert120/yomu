package com.itexpert120.yomu.core.model

/** A user-made collection (shelf). A book can belong to any number of them. */
data class BookCollection(
    val id: String,
    val name: String,
    val position: Int,
)

/**
 * A reading status the reader sets by hand. It takes precedence over the automatic
 * [ReadingState] for the library's status tabs; null means "follow progress".
 */
enum class ManualStatus(val label: String) {
    OnHold("On hold"),
    Dropped("Dropped"),
    ;

    companion object {
        fun fromStoredName(name: String?): ManualStatus? = entries.firstOrNull { it.name == name }
    }
}
