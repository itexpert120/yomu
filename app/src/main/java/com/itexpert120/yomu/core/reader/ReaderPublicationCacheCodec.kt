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
        val weights = resourceWeightsJson
            ?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull() }
            ?.filterValues { it > 0 }
            .orEmpty()
        return ReaderPublicationCache(toc = toc, resourceWeights = weights)
    }
}
