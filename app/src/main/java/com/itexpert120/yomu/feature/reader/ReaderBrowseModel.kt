package com.itexpert120.yomu.feature.reader

import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.ChapterReadState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * The Browse sheet's model, kept free of Compose so it can be unit-tested.
 *
 * Contents: the table of contents as an outline of sections. A section is a volume (a top-level
 * entry with entries under it) or a run of loose top-level chapters. Volumes collapse; the one being
 * read starts open. Books with fewer than two volumes are a plain list.
 *
 * Bookmarks and highlights: grouped under the chapter they sit in, in book order.
 */

/** A volume (with [header]) or a run of loose chapters (no header). [entries] index into the TOC. */
internal data class ContentsSection(val header: Int?, val entries: List<Int>)

/** The TOC as sections, plus where the reader is. */
internal data class ContentsOutline(
    val toc: List<ReaderTocItem>,
    val sections: List<ContentsSection>,
    /** TOC index of the chapter being read, or -1. */
    val current: Int,
) {
    val grouped: Boolean get() = sections.any { it.header != null }

    /** Jumpable chapters in the book (what the filter searches and the counts count). */
    val chapterCount: Int get() = sections.sumOf { it.entries.size }

    /** The section holding the chapter being read, or -1. */
    val currentSection: Int get() = sections.indexOfFirst { current in it.entries || it.header == current }
}

internal fun contentsOutline(toc: List<ReaderTocItem>, currentHref: String?): ContentsOutline {
    val current = currentTocIndex(toc, currentHref)
    if (toc.isEmpty()) return ContentsOutline(toc, emptyList(), current)
    val top = toc.minOf { it.depth }
    val sections = mutableListOf<ContentsSection>()
    var header: Int? = null
    var entries = mutableListOf<Int>()
    fun close() {
        if (header != null || entries.isNotEmpty()) sections += ContentsSection(header, entries)
        header = null
        entries = mutableListOf()
    }
    toc.forEachIndexed { index, item ->
        val hasChildren = toc.getOrNull(index + 1)?.let { it.depth > item.depth } == true
        when {
            item.depth == top && hasChildren -> {
                close()
                header = index
                // A volume's own page (its title page) stays reachable as its first entry.
                if (item.locatorJson != null) entries += index
            }
            item.depth == top -> {
                if (header != null) close()
                entries += index
            }
            item.locatorJson != null -> entries += index
        }
    }
    close()
    // One volume holding the whole book (often the book's own title) is no grouping at all.
    val volumes = sections.count { it.header != null }
    val result = if (volumes < 2) {
        listOf(ContentsSection(null, sections.flatMap { it.entries }))
    } else {
        sections
    }
    return ContentsOutline(toc, result, current)
}

/** The TOC entry being read: an exact section match first, else the first entry of its resource. */
internal fun currentTocIndex(toc: List<ReaderTocItem>, currentHref: String?): Int {
    if (currentHref == null) return -1
    val exact = toc.indexOfFirst { it.id == currentHref && it.locatorJson != null }
    if (exact >= 0) return exact
    val resource = currentHref.substringBefore('#')
    return toc.indexOfFirst { it.resourceHref == resource && it.locatorJson != null }
}

/** One line of the Contents list. */
internal sealed interface ContentsRow {
    val key: String

    data class Volume(
        val section: Int,
        val title: String,
        val chapters: Int,
        val read: Int,
        val expanded: Boolean,
        val holdsCurrent: Boolean,
    ) : ContentsRow {
        override val key: String get() = "v$section"
    }

    data class Chapter(
        val tocIndex: Int,
        val title: String,
        val locatorJson: String?,
        /** Nesting below the section's level, for the indent. */
        val indent: Int,
        val read: Boolean,
        /** Progress through it, 0..1 (1 once read). */
        val progress: Float,
        val current: Boolean,
        /** A volume's own title page, shown as its first entry. */
        val volumeStart: Boolean = false,
        /** The volume it belongs to, shown while filtering. */
        val volume: String? = null,
    ) : ContentsRow {
        override val key: String get() = "c$tocIndex"
    }
}

/**
 * The rows to show: the outline with collapsed volumes folded, or with a non-blank [query] every
 * chapter whose title contains it, flat, each naming its volume.
 */
internal fun contentsRows(
    outline: ContentsOutline,
    expanded: Set<Int>,
    reads: ChapterReadState,
    query: String = "",
): List<ContentsRow> {
    val toc = outline.toc
    val needle = query.trim()
    val rows = ArrayList<ContentsRow>(outline.chapterCount + outline.sections.size)
    fun chapter(index: Int, section: ContentsSection, volume: String? = null): ContentsRow.Chapter {
        val item = toc[index]
        val base = section.header?.let { toc[it].depth + 1 } ?: toc.minOf { it.depth }
        return ContentsRow.Chapter(
            tocIndex = index,
            title = item.title,
            locatorJson = item.locatorJson,
            indent = (item.depth - base).coerceIn(0, ReaderTocMaxIndentLevels),
            read = reads.isRead(item.id),
            progress = reads.percent(item.id),
            current = index == outline.current,
            volumeStart = index == section.header,
            volume = volume,
        )
    }
    if (needle.isNotEmpty()) {
        outline.sections.forEach { section ->
            val volume = section.header?.let { toc[it].title }
            section.entries.forEach { index ->
                if (toc[index].title.contains(needle, ignoreCase = true)) rows += chapter(index, section, volume)
            }
        }
        return rows
    }
    outline.sections.forEachIndexed { sectionIndex, section ->
        val header = section.header
        if (header != null) {
            val open = sectionIndex in expanded
            rows += ContentsRow.Volume(
                section = sectionIndex,
                title = toc[header].title,
                chapters = section.entries.count { it != header },
                read = section.entries.count { it != header && reads.isRead(toc[it].id) },
                expanded = open,
                holdsCurrent = sectionIndex == outline.currentSection,
            )
            if (!open) return@forEachIndexed
        }
        section.entries.forEach { rows += chapter(it, section) }
    }
    return rows
}

/** Above this many chapters the Contents tab offers a filter field. */
internal const val ContentsFilterThreshold = 30

/** Bookmarks or highlights under one chapter. [title] null = not in any listed chapter. */
internal data class AnnotationGroup<T>(val title: String?, val items: List<T>)

/** Bookmarks under their chapters, chapters in book order, bookmarks by position. */
internal fun groupBookmarks(bookmarks: List<ReaderBookmark>, toc: List<ReaderTocItem>): List<AnnotationGroup<ReaderBookmark>> = groupByChapter(
    items = bookmarks,
    toc = toc,
    href = { it.href },
    fallbackTitle = { it.chapterTitle?.takeIf(String::isNotBlank) },
    position = { it.progression ?: Double.MAX_VALUE },
    created = { it.createdAt },
)

/** Highlights under their chapters, chapters in book order, passages by position. */
internal fun groupHighlights(highlights: List<ReaderHighlight>, toc: List<ReaderTocItem>): List<AnnotationGroup<ReaderHighlight>> {
    val locators = highlights.associate { it.id to LocatorFacts.parse(it.locatorJson) }
    return groupByChapter(
        items = highlights,
        toc = toc,
        href = { locators[it.id]?.href },
        fallbackTitle = { locators[it.id]?.title },
        position = { locators[it.id]?.progression ?: Double.MAX_VALUE },
        created = { it.createdAt },
    )
}

private fun <T> groupByChapter(
    items: List<T>,
    toc: List<ReaderTocItem>,
    href: (T) -> String?,
    fallbackTitle: (T) -> String?,
    position: (T) -> Double,
    created: (T) -> Long,
): List<AnnotationGroup<T>> {
    if (items.isEmpty()) return emptyList()
    data class Placed<T>(val item: T, val tocIndex: Int, val title: String?)
    val placed = items.map { item ->
        val index = chapterIndexOf(toc, href(item))
        Placed(item, index, toc.getOrNull(index)?.title ?: fallbackTitle(item))
    }
    return placed
        .groupBy { if (it.tocIndex >= 0) "#${it.tocIndex}" else it.title ?: "" }
        .values
        .sortedWith(compareBy({ group -> group.first().tocIndex.let { if (it < 0) Int.MAX_VALUE else it } }, { position(it.first().item) }))
        .map { group ->
            AnnotationGroup(
                title = group.first().title,
                items = group.map { it.item }.sortedWith(compareBy(position, created)),
            )
        }
}

/** The TOC entry an annotation's chapter anchor (a section id or a resource href) belongs to. */
private fun chapterIndexOf(toc: List<ReaderTocItem>, anchor: String?): Int {
    if (anchor.isNullOrBlank()) return -1
    val exact = toc.indexOfFirst { it.id == anchor }
    if (exact >= 0) return exact
    val resource = anchor.substringBefore('#')
    return toc.indexOfFirst { it.resourceHref == resource || it.id.substringBefore('#') == resource }
}

/**
 * What a saved position's locator says about itself. Both engines write the shared locator shape:
 * `href`, `title`, `locations.progression` and `text.{before,highlight,after}`.
 */
internal data class LocatorFacts(
    val href: String?,
    val title: String?,
    val progression: Double?,
    /** The passage at the position, when the locator carries one. */
    val text: String?,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(locatorJson: String): LocatorFacts? = runCatching {
            val root = json.parseToJsonElement(locatorJson).jsonObject
            val locations = root["locations"] as? JsonObject
            val text = root["text"] as? JsonObject
            fun JsonObject.string(key: String) = (this[key])?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            LocatorFacts(
                href = root.string("href"),
                title = root.string("title"),
                progression = locations?.get("progression")?.jsonPrimitive?.doubleOrNull,
                text = text?.let { t -> (t.string("highlight") ?: "") + (t.string("after") ?: "") }?.trim()?.takeIf { it.isNotEmpty() },
            )
        }.getOrNull()
    }
}
