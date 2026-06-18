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
    // 0 = Auto: columns adapt to screen width (good on phone and tablet). A positive value
    // forces that exact column count regardless of width.
    val gridColumns: Int = AUTO_COLUMNS,
    val coverCrop: Boolean = true,
) {
    companion object {
        const val AUTO_COLUMNS = 0
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 7
    }
}
