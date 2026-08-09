package com.itexpert120.yomu.data.reader.readium

import com.itexpert120.yomu.core.reader.ReaderTocItem
import org.json.JSONObject
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

internal data class ReadiumTocEntry(
    val item: ReaderTocItem,
    val locator: Locator?,
    val readingOrderIndex: Int,
    val startProgression: Double,
)

/** Maps Readium's navigation tree once for both Yomu UI rows and logical reader sections. */
@OptIn(DelicateReadiumApi::class)
internal fun Publication.flattenedTableOfContents(): List<ReadiumTocEntry> = buildList {
    val exactResources = LinkedHashMap<String, Link>()
    val resourcesWithoutQuery = LinkedHashMap<String, Link>()
    fun indexLinks(links: List<Link>) {
        links.forEach { link ->
            val normalized = link.url().normalize().removeFragment()
            exactResources.putIfAbsent(normalized.toString(), link)
            resourcesWithoutQuery.putIfAbsent(normalized.removeQuery().toString(), link)
            indexLinks(link.alternates)
            indexLinks(link.children)
        }
    }
    // Match Readium Manifest.linkWithHref() precedence without rescanning and normalizing the full
    // manifest for every TOC row. That implementation is quadratic on books with huge spine/TOCs.
    indexLinks(readingOrder)
    indexLinks(resources)
    indexLinks(links)

    val orderIndices = buildMap {
        readingOrder.forEachIndexed { index, link ->
            putIfAbsent(link.url().normalize().removeFragment().removeQuery().toString(), index)
        }
    }

    fun addLinks(links: List<Link>, depth: Int) {
        links.forEach { link ->
            val title = link.title?.trim()?.takeIf { it.isNotEmpty() }
            if (title != null) {
                val target = link.url()
                val resourceUrl = target.removeFragment()
                val normalized = resourceUrl.normalize()
                val resourceLink = exactResources[normalized.toString()]
                    ?: resourcesWithoutQuery[normalized.removeQuery().toString()]
                val locator = resourceLink?.mediaType?.let { mediaType ->
                    Locator(
                        href = resourceUrl,
                        mediaType = mediaType,
                        title = resourceLink.title ?: link.title,
                        locations = Locator.Locations(
                            fragments = listOfNotNull(target.fragment),
                            progression = if (target.fragment == null) 0.0 else null,
                        ),
                    )
                }
                val resourceHref = locator?.href?.toString() ?: link.url().toString()
                val orderIndex = locator?.let {
                    orderIndices[it.href.normalize().removeFragment().removeQuery().toString()]
                } ?: -1
                add(
                    ReadiumTocEntry(
                        item = ReaderTocItem(
                            id = locator?.stableTocId() ?: link.url().toString(),
                            title = title,
                            locatorJson = locator?.toJSON()?.toString(),
                            depth = depth,
                            resourceHref = resourceHref,
                        ),
                        locator = locator,
                        readingOrderIndex = orderIndex,
                        startProgression = locator?.locations?.progression ?: 0.0,
                    ),
                )
            }
            if (link.children.isNotEmpty()) addLinks(link.children, depth + 1)
        }
    }

    addLinks(tableOfContents, depth = 0)
}

/** Rehydrates the import-time TOC against the current publication without walking the TOC tree. */
@OptIn(DelicateReadiumApi::class)
internal fun Publication.cachedTableOfContents(items: List<ReaderTocItem>): List<ReadiumTocEntry>? {
    val orderIndices = buildMap {
        readingOrder.forEachIndexed { index, link ->
            putIfAbsent(link.url().normalize().removeFragment().removeQuery().toString(), index)
        }
    }
    val entries = items.map { item ->
        val locator = item.locatorJson?.let {
            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
        }
        if (item.locatorJson != null && locator == null) return null
        val orderIndex = locator?.let {
            orderIndices[it.href.normalize().removeFragment().removeQuery().toString()]
                ?: return null
        } ?: -1
        ReadiumTocEntry(
            item = item,
            locator = locator,
            readingOrderIndex = orderIndex,
            startProgression = locator?.locations?.progression ?: 0.0,
        )
    }
    return entries
}

private fun Locator.stableTocId(): String = buildString {
    append(href.toString())
    locations.fragments.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
        append('#')
        append(it)
    }
}
