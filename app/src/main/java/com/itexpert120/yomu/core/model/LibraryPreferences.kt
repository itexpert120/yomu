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
    ComfortableGrid("Comfortable"),
    CompactGrid("Compact"),
    CoverOnlyGrid("Covers"),
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
    // The library tab that was open last (device-local; not synced).
    val lastTab: String = "all",
    // The user's tab order (tab keys); tabs not listed keep their default place after these.
    // Device-local: older builds would drop it from the synced library settings and echo forever.
    val tabOrder: List<String> = emptyList(),
    // Built-in status tabs the user turned off (All can't be hidden). Device-local, like tabOrder.
    val hiddenTabs: Set<String> = emptySet(),
) {
    companion object {
        const val AUTO_COLUMNS = 0
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 7
    }
}
