package com.itexpert120.yomu.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPublicationCacheTest {
    private val toc = listOf(
        ReaderTocItem(
            id = "chapter-1.xhtml",
            title = "Chapter 1",
            locatorJson = null,
            depth = 0,
        ),
    )

    @Test
    fun legacyTocWithoutWeightsRemainsValid() {
        val decoded = ReaderPublicationCacheCodec.decode(
            tocJson = ReaderPublicationCacheCodec.encodeToc(toc),
            resourceWeightsJson = null,
        )

        assertEquals(ReaderPublicationCache(toc = toc), decoded)
    }

    @Test
    fun emptyTocIsDistinctFromMissingCache() {
        val encodedEmpty = ReaderPublicationCacheCodec.encodeToc(emptyList())

        assertNotNull(ReaderPublicationCacheCodec.decode(encodedEmpty, null))
        assertNull(ReaderPublicationCacheCodec.decode(null, null))
    }

    @Test
    fun corruptTocFallsBackToPublicationRebuild() {
        assertNull(ReaderPublicationCacheCodec.decode("not-json", "{}"))
    }

    @Test
    fun corruptWeightsBecomeAWeightCacheMissWithoutDiscardingToc() {
        val decoded = ReaderPublicationCacheCodec.decode(
            tocJson = ReaderPublicationCacheCodec.encodeToc(toc),
            resourceWeightsJson = "not-json",
        )

        assertEquals(toc, decoded?.toc)
        assertEquals(emptyMap<String, Int>(), decoded?.resourceWeights)
    }

    @Test
    fun weightsAreValidatedAndOrderedByOpenedPublication() {
        val cache = ReaderPublicationCache(resourceWeights = mapOf("b" to 3, "a" to 1, "extra" to 9))

        assertEquals(listOf(1, 3), cache.validatedWeights(listOf("a", "b")))
        assertNull(cache.validatedWeights(listOf("a", "missing")))
        assertNull(
            ReaderPublicationCache(resourceWeights = mapOf("a" to 0))
                .validatedWeights(listOf("a")),
        )
    }
}
