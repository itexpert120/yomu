package com.itexpert120.yomu.data.reader

import com.itexpert120.yomu.core.model.ReaderLayout
import com.itexpert120.yomu.core.reader.ReaderLocator

/**
 * Where the reader is in a book, in the book's own terms: the logical TOC section, chapter-weighted
 * book progress, pages left in paged mode, and the section completed by reading through its end.
 *
 * Fed the rendering engine's position events as plain data; knows nothing about the engine.
 * [T] is the engine's navigation target for a section, handed back by [sectionTarget].
 */
internal class ReadingPosition<T>(
    sections: List<Section<T>>,
    private val readingOrder: List<String>,
    resourceWeights: List<Int>,
    layout: ReaderLayout,
    initialHref: String? = null,
    initialProgression: Double? = null,
) {
    /** A logical TOC section starting at [startProgression] of reading-order resource [resourceIndex]. */
    data class Section<out T>(
        val id: String,
        val title: String,
        val resourceIndex: Int,
        val startProgression: Double,
        val target: T,
    )

    private val sections: List<Section<T>> =
        sections.sortedWith(compareBy<Section<T>> { it.resourceIndex }.thenBy { it.startProgression })

    // Fragment links into one resource all resolve to the same start (their anchors' positions
    // aren't known without the DOM), so only the last of them would ever be "current". Let every
    // section in such a run share the current one's progress instead of leaving the rest at 0%.
    private val sectionsSharingStart: Map<String, List<String>> = this.sections
        .groupBy { it.resourceIndex to it.startProgression }
        .values
        .filter { it.size > 1 }
        .flatMap { group -> group.map { section -> section.id to group.map { it.id }.minus(section.id) } }
        .toMap()

    // Href -> index, built once: position updates look this up several times per page turn, and
    // books can have 1500+ resources. First match wins.
    private val resourceIndexByHref: Map<String, Int> = HashMap<String, Int>().apply {
        readingOrder.forEachIndexed { index, href -> putIfAbsent(href, index) }
    }

    private val boundaries: List<SectionBoundary> =
        this.sections.map { SectionBoundary(it.resourceIndex, it.startProgression) }

    private val weights: List<Int> =
        resourceWeights.takeIf { it.size == readingOrder.size } ?: List(readingOrder.size) { 1 }

    private data class Page(val href: String, val index: Int, val total: Int)

    private data class ScrollMeasurement(val href: String, val progression: Double)

    private var currentHref: String? = initialHref
    private var currentProgression: Double? = initialProgression
    private var page: Page? = null
    private var scroll: ScrollMeasurement? = null
    private var pendingCompletedSectionId: String? = null

    /** Paged mode measures progress in visual pages; scroll mode in measured scroll offset. */
    var layout: ReaderLayout = layout
        set(value) {
            field = value
            if (value != ReaderLayout.Scroll) scroll = null
        }

    fun resourceIndexOf(href: String?): Int = href?.let { resourceIndexByHref[it] } ?: -1

    /** The visual page shown in paged mode. */
    fun onPage(href: String, pageIndex: Int, totalPages: Int) {
        page = Page(href, pageIndex, totalPages)
    }

    /** The measured scroll offset of [href] in scroll mode (0..1). */
    fun onScrollMeasured(href: String, progression: Double) {
        scroll = ScrollMeasurement(href, progression.coerceIn(0.0, 1.0))
    }

    /** The engine's position moved to [progression] of resource [href]. */
    fun onLocator(
        href: String,
        progression: Double?,
        totalProgression: Double?,
        title: String?,
    ): PositionSnapshot {
        val previousHref = currentHref
        currentHref = href
        currentProgression = progression
        if (scroll?.href != href) scroll = null
        val metrics = metrics(href, progression, page)
        val section = sections.getOrNull(metrics.sectionIndex)
        // Reading through a section's end into the next resource completes it; a continuation
        // resource of the same section, or a jump, does not.
        val completedSectionId = pendingCompletedSectionId?.takeIf {
            it != section?.id && previousHref != null && previousHref != href
        }
        if (completedSectionId != null) pendingCompletedSectionId = null
        val pagesLeft = page
            ?.takeIf { layout == ReaderLayout.Paged && it.href == href && it.total > 0 }
            ?.let { (it.total - it.index.coerceIn(0, it.total - 1) - 1).coerceAtLeast(0) }
        return PositionSnapshot(
            href = href,
            totalProgression = totalProgression,
            chapterTitle = section?.title ?: title,
            chapterProgression = metrics.sectionProgress,
            hasPreviousChapter = metrics.sectionIndex > 0,
            hasNextChapter = metrics.sectionIndex in 0 until sections.lastIndex,
            bookProgress = metrics.bookProgress,
            chapterPagesLeft = pagesLeft,
            chapterId = section?.id,
            sharedChapterIds = section?.id?.let { sectionsSharingStart[it] }.orEmpty(),
            completedChapterId = completedSectionId,
            completedSharedChapterIds = completedSectionId?.let { sectionsSharingStart[it] }.orEmpty(),
            completed = metrics.completed,
        )
    }

    /**
     * The next position will come from reading continuously into resource [toHref] (scroll-mode
     * overscroll). If that lands in a different section, the current one is completed on arrival.
     */
    fun expectSequentialCrossing(toHref: String) {
        val current = currentHref?.let { sections.getOrNull(metrics(it, currentProgression, page).sectionIndex) }
        val target = sections.getOrNull(metrics(toHref, null, null).sectionIndex)
        pendingCompletedSectionId = current?.id?.takeIf { target?.id != it }
    }

    /** The expected crossing didn't happen (navigation failed). */
    fun cancelCrossing() {
        pendingCompletedSectionId = null
    }

    /** The section [delta] sections away from the current one, or null past either end. */
    fun sectionTarget(delta: Int): T? {
        val href = currentHref ?: return null
        val index = metrics(href, currentProgression, page).sectionIndex
        return sections.getOrNull(index + delta)?.target
    }

    /** Whether [progression] of resource [href] is at or after the current position. */
    fun isForward(href: String, progression: Double?): Boolean = metrics(href, progression, null).bookProgress >= currentBookProgress()

    private fun currentBookProgress(): Double = currentHref?.let { metrics(it, currentProgression, page).bookProgress } ?: 0.0

    private fun metrics(href: String, locatorProgression: Double?, page: Page?): LogicalProgress {
        val resourceIndex = resourceIndexOf(href)
        if (resourceIndex < 0) return LogicalProgress(0.0, -1, null, false)
        val pageProgress = page
            ?.takeIf { it.href == href && it.total > 0 }
            ?.let { (it.index + 1).toDouble() / it.total }
        val resourceProgress = if (layout == ReaderLayout.Paged) {
            pageProgress ?: locatorProgression ?: 0.0
        } else {
            scroll?.takeIf { it.href == href }?.progression ?: locatorProgression ?: 0.0
        }
        return calculateLogicalProgress(
            resourceIndex = resourceIndex,
            resourceProgress = resourceProgress,
            locatorProgress = locatorProgression ?: 0.0,
            resourceWeights = weights,
            sections = boundaries,
        )
    }
}

/** One computed position; [toReaderLocator] adds the engine's serialized locator. */
internal data class PositionSnapshot(
    val href: String,
    val totalProgression: Double?,
    val chapterTitle: String?,
    val chapterProgression: Double?,
    val hasPreviousChapter: Boolean,
    val hasNextChapter: Boolean,
    val bookProgress: Double,
    val chapterPagesLeft: Int?,
    val chapterId: String?,
    val sharedChapterIds: List<String>,
    val completedChapterId: String?,
    val completedSharedChapterIds: List<String>,
    val completed: Boolean,
) {
    fun toReaderLocator(locatorJson: String) = ReaderLocator(
        locatorJson = locatorJson,
        totalProgression = totalProgression,
        chapterTitle = chapterTitle,
        href = href,
        chapterProgression = chapterProgression,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
        bookProgress = bookProgress,
        chapterPagesLeft = chapterPagesLeft,
        chapterId = chapterId,
        sharedChapterIds = sharedChapterIds,
        completedChapterId = completedChapterId,
        completedSharedChapterIds = completedSharedChapterIds,
        completed = completed,
    )
}

private data class SectionBoundary(
    val resourceIndex: Int,
    val startProgression: Double,
)

private data class LogicalProgress(
    val bookProgress: Double,
    val sectionIndex: Int,
    val sectionProgress: Double?,
    val completed: Boolean,
)

/** Position-weighted progress through the book and its logical sections. */
private fun calculateLogicalProgress(
    resourceIndex: Int,
    resourceProgress: Double,
    locatorProgress: Double,
    resourceWeights: List<Int>,
    sections: List<SectionBoundary>,
): LogicalProgress {
    if (resourceIndex !in resourceWeights.indices || resourceWeights.isEmpty()) {
        return LogicalProgress(0.0, -1, null, false)
    }
    val weights = resourceWeights.map { it.coerceAtLeast(1) }
    val prefix = IntArray(weights.size + 1)
    weights.forEachIndexed { i, weight -> prefix[i + 1] = prefix[i] + weight }
    val totalWeight = prefix.last().coerceAtLeast(1).toDouble()
    fun coordinate(index: Int, progression: Double): Double = prefix[index] + progression.coerceIn(0.0, 1.0) * weights[index]

    val coordinate = coordinate(resourceIndex, resourceProgress)
    val starts = sections.map { coordinate(it.resourceIndex, it.startProgression) }
    // The inclusive final visual page can complete this section, but it still belongs to the current
    // resource until navigation actually crosses the boundary.
    val sectionIndex = sections.indexOfLast { section ->
        section.resourceIndex < resourceIndex ||
            (
                section.resourceIndex == resourceIndex &&
                    section.startProgression <= locatorProgress + 1e-6
                )
    }
    val sectionProgress = sections.getOrNull(sectionIndex)?.let {
        val start = starts[sectionIndex]
        val end = starts.getOrNull(sectionIndex + 1) ?: totalWeight
        if (end <= start) {
            0.0
        } else {
            val raw = ((coordinate - start) / (end - start)).coerceIn(0.0, 1.0)
            val nextSection = sections.getOrNull(sectionIndex + 1)
            val finalResourceIndex = (nextSection?.resourceIndex?.minus(1) ?: weights.lastIndex)
            val reachedActualEnd =
                resourceIndex == finalResourceIndex && resourceProgress >= 0.999
            // Position weights can round a section to 100% while the locator is still in an earlier
            // resource. Reserve 100% for the actual final resource/page of the logical section.
            if (reachedActualEnd) 1.0 else raw.coerceAtMost(0.998)
        }
    }
    return LogicalProgress(
        bookProgress = (coordinate / totalWeight).coerceIn(0.0, 1.0),
        sectionIndex = sectionIndex,
        sectionProgress = sectionProgress,
        completed = resourceIndex == weights.lastIndex && resourceProgress >= 0.999,
    )
}
