package com.itexpert120.yomu.data.encyclopedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaParserTest {

    @Test
    fun parsesAStandardSummary() {
        val body = """
            {"type":"standard","title":"Selvage","titles":{"canonical":"Selvage","normalized":"Selvage"},
             "description":"Self-finished edge of fabric","extract":"A selvage is a self-finished edge of fabric.",
             "thumbnail":{"source":"https://upload.wikimedia.org/selvage.jpg","width":320,"height":240},
             "content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Selvage"},
                             "mobile":{"page":"https://en.m.wikipedia.org/wiki/Selvage"}},
             "lang":"en","extra":{"ignored":true}}
        """.trimIndent()

        val found = WikipediaParser.parse(body, "en") as WikipediaResult.Found

        with(found.summary) {
            assertEquals("Selvage", title)
            assertEquals("Self-finished edge of fabric", description)
            assertEquals("A selvage is a self-finished edge of fabric.", extract)
            assertEquals("https://upload.wikimedia.org/selvage.jpg", thumbnailUrl)
            assertEquals("https://en.m.wikipedia.org/wiki/Selvage", pageUrl)
            assertEquals("en", language)
            assertEquals(false, disambiguation)
        }
    }

    @Test
    fun aMissingThumbnailAndDescriptionAreLeftOut() {
        val body = """{"type":"standard","title":"Mole","extract":"Mole may be…","lang":"fr"}"""

        val summary = (WikipediaParser.parse(body, "fr") as WikipediaResult.Found).summary

        assertNull(summary.thumbnailUrl)
        assertNull(summary.description)
        assertEquals("https://fr.wikipedia.org/wiki/Mole", summary.pageUrl)
    }

    @Test
    fun disambiguationPagesAreMarked() {
        val body = """{"type":"disambiguation","title":"Toad","extract":"Toad may refer to:"}"""

        val summary = (WikipediaParser.parse(body, "en") as WikipediaResult.Found).summary

        assertTrue(summary.disambiguation)
    }

    @Test
    fun anEmptyExtractIsNotFoundAndGarbageIsAnError() {
        assertEquals(WikipediaResult.NotFound, WikipediaParser.parse("""{"type":"no-extract","title":"X","extract":""}""", "en"))
        assertEquals(WikipediaResult.Error, WikipediaParser.parse("<html>nope</html>", "en"))
    }

    @Test
    fun insecureLinksAreNotUsed() {
        val body = """{"title":"A","extract":"B","thumbnail":{"source":"http://x/y.png"},"content_urls":{"mobile":{"page":"javascript:alert(1)"}}}"""

        val summary = (WikipediaParser.parse(body, "en") as WikipediaResult.Found).summary

        assertNull(summary.thumbnailUrl)
        assertEquals("https://en.wikipedia.org/wiki/A", summary.pageUrl)
    }

    @Test
    fun requestsUseThePrimaryLanguageAndAnEncodedTitle() {
        assertEquals("fr", WikipediaRequest.wikiLanguage("fr-CA"))
        assertEquals("en", WikipediaRequest.wikiLanguage(null))
        assertEquals("en", WikipediaRequest.wikiLanguage("../evil"))
        assertEquals("River bank", WikipediaRequest.title("  “River   bank”, "))
        assertNull(WikipediaRequest.title(" … "))
        assertEquals(
            "https://de.wikipedia.org/api/rest_v1/page/summary/Kr%C3%B6te_%26_Frosch",
            WikipediaRequest.summaryUrl("Kröte & Frosch", "de"),
        )
    }
}
