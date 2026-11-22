package com.itexpert120.yomu.core.reader

import kotlin.math.abs

/**
 * Where a bookmark sits: its chapter [anchor] (a logical chapter id; older bookmarks carry only a
 * resource href), the engine [locatorJson], and whole-book [progression] when known. Shared by the
 * durable write and the live "this page is bookmarked" indicator so the two always agree.
 */
data class BookmarkAnchor(
    val anchor: String?,
    val locatorJson: String,
    val progression: Double?,
) {
    /** Same page: same chapter (a resource-only anchor matches any chapter starting in that resource) and within 1% of the book. */
    fun matches(other: BookmarkAnchor): Boolean {
        if (anchor != other.anchor) {
            if (anchor?.substringBefore('#') != other.anchor?.substringBefore('#')) return false
            if (anchor?.contains('#') == true && other.anchor?.contains('#') == true) return false
        }
        // An unknown book position falls back to the exact locator rather than counting as 0%.
        return if (progression != null && other.progression != null) {
            abs(progression - other.progression) < SAME_PAGE_PROGRESSION
        } else {
            locatorJson == other.locatorJson
        }
    }

    private companion object {
        const val SAME_PAGE_PROGRESSION = 0.01
    }
}

/** The anchor a bookmark of this position gets: the logical chapter when known, else the resource. */
fun ReaderLocator.bookmarkAnchor(): BookmarkAnchor = BookmarkAnchor(chapterId ?: href, locatorJson, totalProgression)

val ReaderBookmark.anchor: BookmarkAnchor get() = BookmarkAnchor(href, locatorJson, progression)

/** The bookmarks on the page at [anchor] (normally zero or one). */
fun List<ReaderBookmark>.at(anchor: BookmarkAnchor): List<ReaderBookmark> = filter { it.anchor.matches(anchor) }
