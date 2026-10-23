package com.itexpert120.yomu

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itexpert120.yomu.data.reader.readium.flattenedTableOfContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReadiumTocMapperTest {
    @Test
    fun largeNavigationTreeResolvesThroughTheManifestIndex() {
        val readingOrder = (0 until 2_000).map { index ->
            Link(
                href = Href("Text/chapter$index.xhtml")!!,
                mediaType = MediaType.XHTML,
            )
        }
        val toc = readingOrder.mapIndexed { index, link ->
            Link(
                href = Href("${link.href}#section-$index")!!,
                title = "Section $index",
            )
        }
        val publication = Publication(
            Manifest(
                metadata = Metadata(),
                readingOrder = readingOrder,
                tableOfContents = toc,
            ),
        )

        val mapped = publication.flattenedTableOfContents()

        assertEquals(2_000, mapped.size)
        assertEquals(1_999, mapped.last().readingOrderIndex)
        assertEquals("Text/chapter1999.xhtml#section-1999", mapped.last().item.id)
        assertNotNull(mapped.last().item.locatorJson)
    }

    @Test
    fun sameResourceAnchorsRemainDistinctLogicalSections() {
        val resource = Link(
            href = Href("Text/chapter.xhtml")!!,
            mediaType = MediaType.XHTML,
        )
        val publication = Publication(
            Manifest(
                metadata = Metadata(),
                readingOrder = listOf(resource),
                tableOfContents = listOf(
                    Link(Href("Text/chapter.xhtml#one")!!, title = "One"),
                    Link(Href("Text/chapter.xhtml#two")!!, title = "Two"),
                ),
            ),
        )

        val mapped = publication.flattenedTableOfContents()

        assertEquals(
            listOf("Text/chapter.xhtml#one", "Text/chapter.xhtml#two"),
            mapped.map { it.item.id },
        )
        assertEquals(listOf(0, 0), mapped.map { it.readingOrderIndex })
    }

    @Test
    fun nonAsciiAnchorMangledByEpubParserStaysJumpable() {
        val resource = Link(
            href = Href("Text/ch864.xhtml")!!,
            mediaType = MediaType.XHTML,
        )
        // Url.fromEpubHref falls back to this for non-ASCII hrefs, encoding the `#` into the path.
        val mangled = Url.fromDecodedPath("Text/ch864.xhtml#chapter-851-dwayne-dantèss")!!
        val publication = Publication(
            Manifest(
                metadata = Metadata(),
                readingOrder = listOf(resource),
                tableOfContents = listOf(Link(Href(mangled), title = "Chapter 851")),
            ),
        )

        val entry = publication.flattenedTableOfContents().single()

        assertNotNull(entry.item.locatorJson)
        assertEquals(0, entry.readingOrderIndex)
        assertEquals("Text/ch864.xhtml", entry.locator!!.href.toString())
        assertEquals(listOf("chapter-851-dwayne-dantèss"), entry.locator!!.locations.fragments)
    }
}
