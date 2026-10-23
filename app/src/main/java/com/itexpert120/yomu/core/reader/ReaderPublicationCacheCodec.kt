package com.itexpert120.yomu.core.reader

import kotlinx.serialization.json.Json

/** Stable Room representation for the reader publication cache. */
object ReaderPublicationCacheCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeToc(items: List<ReaderTocItem>): String = json.encodeToString(items)

    fun encodeWeights(weights: Map<String, Int>): String = json.encodeToString(weights)

    /** Returns null for a missing/corrupt TOC; malformed weights intentionally become a cache miss. */
    fun decode(tocJson: String?, resourceWeightsJson: String?): ReaderPublicationCache? {
        if (tocJson == null) return null
        val toc = runCatching { json.decodeFromString<List<ReaderTocItem>>(tocJson) }
            .getOrNull() ?: return null
        // TOCs cached before non-ASCII anchors were repaired stored those rows as unjumpable with an
        // encoded `#` (`%23`) in the href; treat them as a miss so the TOC is re-extracted.
        if (toc.any { it.locatorJson == null && "%23" in it.resourceHref }) return null
        val weights = resourceWeightsJson
            ?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull() }
            ?.filterValues { it > 0 }
            .orEmpty()
        return ReaderPublicationCache(toc = toc, resourceWeights = weights)
    }
}
