package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.HighlightEntity
import com.itexpert120.yomu.core.database.anchor
import com.itexpert120.yomu.core.database.toBookmarkEntity
import com.itexpert120.yomu.core.reader.BookmarkAnchor
import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.data.highlights.normalizedNote

/**
 * What importing a file into a book would do. [highlights]/[notes]/[bookmarks] count the file;
 * [newHighlights], [updatedHighlights] and [newBookmarks] are the changes applying it would make
 * (the rest are already there). [bookMatches]: the file was exported from this very book (same
 * EPUB content hash); when false the UI should warn before applying.
 */
data class AnnotationImportPreview(
    val fileBookTitle: String?,
    val fileBookAuthor: String?,
    val bookMatches: Boolean,
    val highlights: Int,
    val notes: Int,
    val bookmarks: Int,
    val newHighlights: Int,
    val updatedHighlights: Int,
    val newBookmarks: Int,
) {
    val changesNothing: Boolean get() = newHighlights == 0 && updatedHighlights == 0 && newBookmarks == 0
}

/** Reading an import for a book: a preview plus the parsed file to apply, or why it can't be. */
sealed interface AnnotationImportCheck {
    data class Ready(val preview: AnnotationImportPreview, val file: AnnotationFile) : AnnotationImportCheck

    data object NotAnnotationFile : AnnotationImportCheck

    data class NewerVersion(val version: Int) : AnnotationImportCheck

    data object BookMissing : AnnotationImportCheck
}

data class AnnotationImportResult(val addedHighlights: Int, val updatedHighlights: Int, val addedBookmarks: Int)

/** The rows an import writes: new highlights, existing ones whose note/style/colour it updates, new bookmarks. */
internal data class ImportPlan(
    val insertHighlights: List<HighlightEntity>,
    val updateHighlights: List<HighlightEntity>,
    val insertBookmarks: List<BookmarkEntity>,
)

/**
 * Merges an [AnnotationFile] into one book without duplicating anything.
 *
 * A highlight is already there when one has the same text at the same locator (same CFI, or the
 * same locator object). For those, the more recently edited side wins the note, style and colour
 * (edit time = `modifiedAt`; never edited = 0, so any edit beats none). A bookmark is already there
 * when one is on the same page ([BookmarkAnchor.matches]). New rows get fresh ids: reusing an id
 * from the file could revive a highlight that was deleted (and the deletion synced) elsewhere.
 */
internal object AnnotationMerge {

    fun plan(
        bookId: String,
        file: AnnotationFile,
        existingHighlights: List<HighlightEntity>,
        existingBookmarks: List<BookmarkEntity>,
        newId: () -> String,
        now: Long,
    ): ImportPlan {
        val known = existingHighlights.toMutableList()
        val inserts = linkedMapOf<String, HighlightEntity>()
        val updates = linkedMapOf<String, HighlightEntity>()
        for (incoming in file.highlights) {
            val text = incoming.text.takeIf { it.isNotBlank() } ?: continue
            val locator = incoming.locator.toLocatorJson()
            val note = normalizedNote(incoming.note)
            val style = incoming.style.ifBlank { HighlightStyle.Highlight.key }
            val color = parseArgbHex(incoming.color) ?: DEFAULT_HIGHLIGHT_ARGB
            val createdAt = parseInstant(incoming.createdAt) ?: now
            val modifiedAt = parseInstant(incoming.modifiedAt) ?: 0L
            val index = known.indexOfFirst { it.text.trim() == text.trim() && sameLocator(it.locatorJson, locator) }
            if (index < 0) {
                val row = HighlightEntity(newId(), bookId, locator, text, color, createdAt, note, style, modifiedAt)
                known += row
                inserts[row.id] = row
                continue
            }
            val local = known[index]
            if (modifiedAt <= local.modifiedAt) continue
            if (local.note == note && local.style == style && local.colorArgb == color) continue
            val merged = local.copy(note = note, style = style, colorArgb = color, modifiedAt = modifiedAt)
            known[index] = merged
            if (merged.id in inserts) inserts[merged.id] = merged else updates[merged.id] = merged
        }

        val anchors = existingBookmarks.map { it.anchor }.toMutableList()
        val bookmarks = mutableListOf<BookmarkEntity>()
        for (incoming in file.bookmarks) {
            val anchor = BookmarkAnchor(incoming.chapterId, incoming.locator.toLocatorJson(), incoming.progression)
            if (anchors.any { it.matches(anchor) }) continue
            anchors += anchor
            bookmarks += anchor.toBookmarkEntity(newId(), bookId, incoming.chapter, parseInstant(incoming.createdAt) ?: now)
        }
        return ImportPlan(inserts.values.toList(), updates.values.toList(), bookmarks)
    }

    fun preview(file: AnnotationFile, targetSha256: String?, plan: ImportPlan) = AnnotationImportPreview(
        fileBookTitle = file.book.title,
        fileBookAuthor = file.book.author,
        bookMatches = file.book.sha256 != null && file.book.sha256.equals(targetSha256, ignoreCase = true),
        highlights = file.highlights.count { it.text.isNotBlank() },
        notes = file.highlights.count { it.text.isNotBlank() && normalizedNote(it.note) != null },
        bookmarks = file.bookmarks.size,
        newHighlights = plan.insertHighlights.size,
        updatedHighlights = plan.updateHighlights.size,
        newBookmarks = plan.insertBookmarks.size,
    )

    /** Same place in the book: identical locators, the same CFI, or equal locator objects. */
    fun sameLocator(a: String, b: String): Boolean {
        if (a == b) return true
        val cfiA = LocatorPoint.parse(a).cfi
        val cfiB = LocatorPoint.parse(b).cfi
        if (cfiA != null || cfiB != null) return cfiA == cfiB
        val jsonA = parseJsonElement(a) ?: return false
        return jsonA == parseJsonElement(b)
    }
}

/** The reader's default yellow, for imported highlights without a usable colour. */
internal const val DEFAULT_HIGHLIGHT_ARGB = 0xFFF6E27A.toInt()
