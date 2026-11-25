package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderTocItem

/** One highlight or bookmark of a book, as exported. */
sealed interface AnnotationItem {
    val id: String
    val createdAt: Long

    data class Highlight(val highlight: ReaderHighlight) : AnnotationItem {
        override val id get() = highlight.id
        override val createdAt get() = highlight.createdAt
    }

    data class Bookmark(val bookmark: ReaderBookmark) : AnnotationItem {
        override val id get() = bookmark.id
        override val createdAt get() = bookmark.createdAt
    }
}

/** A run of annotations under one chapter; [title] null = no known chapter (no heading). */
data class AnnotationChapter(val title: String?, val items: List<AnnotationItem>)

/**
 * Everything exported for one book, in reading order. [sha256] is the EPUB's content hash, the
 * identity sync matches books by, so an exported file can be matched back to its book.
 */
data class AnnotationDocument(
    val bookTitle: String,
    val author: String?,
    val sha256: String?,
    val exportedAt: Long,
    val chapters: List<AnnotationChapter>,
) {
    private val items get() = chapters.asSequence().flatMap { it.items }

    val highlights: List<ReaderHighlight> get() = items.filterIsInstance<AnnotationItem.Highlight>().map { it.highlight }.toList()
    val bookmarks: List<ReaderBookmark> get() = items.filterIsInstance<AnnotationItem.Bookmark>().map { it.bookmark }.toList()

    val highlightCount: Int get() = highlights.size
    val bookmarkCount: Int get() = bookmarks.size

    /** Highlights with a note: the notes of the book. */
    val noteCount: Int get() = highlights.count { it.note != null }

    val isEmpty: Boolean get() = chapters.all { it.items.isEmpty() }

    companion object {
        /** [readingOrder]: the book's resource hrefs in spine order (empty = derive from the TOC). */
        fun of(
            bookTitle: String,
            author: String?,
            sha256: String?,
            exportedAt: Long,
            highlights: List<ReaderHighlight>,
            bookmarks: List<ReaderBookmark>,
            toc: List<ReaderTocItem>,
            readingOrder: List<String>,
        ) = AnnotationDocument(bookTitle, author, sha256, exportedAt, AnnotationOutline.chapters(highlights, bookmarks, toc, readingOrder))
    }
}

/**
 * Orders a book's annotations by where they sit (spine item, then progression through it) and
 * files each under the TOC entry it falls in: the last entry at or before it. TOC entries that share
 * a resource without a known position (fragment targets) all sit at its start, so the first of them
 * wins. Annotations whose resource is unknown go last, by whole-book progression.
 */
internal object AnnotationOutline {

    private class Placed(val item: AnnotationItem, val spine: Int?, val progression: Double, val total: Double?, val fallbackTitle: String?)

    fun chapters(
        highlights: List<ReaderHighlight>,
        bookmarks: List<ReaderBookmark>,
        toc: List<ReaderTocItem>,
        readingOrder: List<String>,
    ): List<AnnotationChapter> {
        val spineIndex = spineIndex(readingOrder, toc)
        fun place(item: AnnotationItem, locatorJson: String, bookProgression: Double?, chapterTitle: String?): Placed {
            val point = LocatorPoint.parse(locatorJson)
            val spine = normalizedHref(point.href)?.let(spineIndex::get)
            return Placed(item, spine, point.progression ?: 0.0, bookProgression ?: point.totalProgression, chapterTitle ?: point.title)
        }
        val placed = highlights.map { place(AnnotationItem.Highlight(it), it.locatorJson, null, null) } +
            bookmarks.map { place(AnnotationItem.Bookmark(it), it.locatorJson, it.progression, it.chapterTitle) }
        val sorted = placed.sortedWith(
            compareBy<Placed> { if (it.spine == null) 1 else 0 }
                .thenBy { it.spine ?: 0 }
                .thenBy { if (it.spine != null) it.progression else it.total ?: Double.MAX_VALUE }
                .thenBy { it.item.createdAt }
                .thenBy { it.item.id },
        )
        val tocById = toc.withIndex().associateBy({ it.value.id }, { it.index })
        val tocPlaces = toc.mapIndexedNotNull { index, entry ->
            val spine = normalizedHref(entry.resourceHref)?.let(spineIndex::get) ?: return@mapIndexedNotNull null
            Triple(index, spine, LocatorPoint.parse(entry.locatorJson).progression ?: 0.0)
        }.sortedWith(compareBy<Triple<Int, Int, Double>> { it.second }.thenBy { it.third }.thenBy { it.first })

        fun chapterOf(p: Placed): Pair<Any?, String?> {
            // New bookmarks name their logical chapter (a TOC id) outright.
            val named = (p.item as? AnnotationItem.Bookmark)?.bookmark?.href?.let(tocById::get)
            val index = named ?: p.spine?.let { spine ->
                val atOrBefore = tocPlaces.lastOrNull { (_, s, prog) -> s < spine || (s == spine && prog <= p.progression) }
                // The first entry at that same position, not the last.
                atOrBefore?.let { last -> tocPlaces.first { it.second == last.second && it.third == last.third }.first }
            }
            return if (index != null) index to toc[index].title else p.fallbackTitle to p.fallbackTitle
        }

        val chapters = mutableListOf<AnnotationChapter>()
        var currentKey: Any? = Unit
        var current = mutableListOf<AnnotationItem>()
        for (p in sorted) {
            val (key, title) = chapterOf(p)
            if (key != currentKey || chapters.isEmpty()) {
                current = mutableListOf()
                chapters += AnnotationChapter(title, current)
                currentKey = key
            }
            current += p.item
        }
        return chapters.map { it.copy(items = it.items.toList()) }
    }

    private fun spineIndex(readingOrder: List<String>, toc: List<ReaderTocItem>): Map<String, Int> {
        val hrefs = readingOrder.ifEmpty { toc.map { it.resourceHref } }
        val index = HashMap<String, Int>()
        hrefs.mapNotNull(::normalizedHref).distinct().forEachIndexed { i, href -> index[href] = i }
        return index
    }
}
