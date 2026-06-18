package com.itexpert120.yomu.core.model

/** Persisted library view preferences (docs/data-model.md "Library View Preferences"). */
enum class SortMode(val label: String) {
    Recent("Recent"),
    Title("Title"),
    Author("Author"),
    Unread("Unread"),
}

enum class GroupMode(val label: String) {
    None("None"),
    Author("Author"),
    Series("Series"),
}

enum class LibraryViewMode(val label: String) {
    Grid("Grid"),
    List("List"),
}

data class LibraryPreferences(
    val sortMode: SortMode = SortMode.Recent,
    val groupMode: GroupMode = GroupMode.None,
    val viewMode: LibraryViewMode = LibraryViewMode.Grid,
    val gridColumns: Int = 3,
    val coverCrop: Boolean = true,
) {
    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 4
    }
}
