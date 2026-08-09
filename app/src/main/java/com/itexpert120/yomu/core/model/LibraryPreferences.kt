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
}

enum class LibraryViewMode(val label: String) {
    ComfortableGrid("Comfortable grid"),
    CompactGrid("Compact grid"),
    CoverOnlyGrid("Cover only"),
    List("List"),
}

data class LibraryPreferences(
    val sortMode: SortMode = SortMode.Recent,
    val groupMode: GroupMode = GroupMode.None,
    val viewMode: LibraryViewMode = LibraryViewMode.ComfortableGrid,
    // 0 = Automatic: columns adapt to the active window. A positive value forces that exact count.
    val portraitGridColumns: Int = AUTO_COLUMNS,
    val landscapeGridColumns: Int = AUTO_COLUMNS,
    val coverCrop: Boolean = true,
) {
    companion object {
        const val AUTO_COLUMNS = 0
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 7
    }
}
