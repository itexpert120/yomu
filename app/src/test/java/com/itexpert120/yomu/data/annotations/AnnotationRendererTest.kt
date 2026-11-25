package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

/** Exports exactly as people (Markdown, HTML) and importers (JSON) will see them. */
class AnnotationRendererTest {
    private val at = 1_700_000_000_000 // 2023-11-14T22:13:20Z

    private val fear = ReaderHighlight(
        id = "h1",
        locatorJson = """{"href":"ch1.xhtml","locations":{"cfi":"epubcfi(/6/4!/4/2,/1:0,/1:24)"}}""",
        text = "Fear is the mind-killer.",
        colorArgb = 0xFFF6E27A.toInt(),
        createdAt = at,
        note = "Litany\nagainst fear",
        style = HighlightStyle.Squiggly,
        modifiedAt = at + 5_000,
    )
    private val mark = ReaderBookmark("b1", """{"href":"ch1.xhtml","locations":{"progression":0.5}}""", "ch1", "Chapter One", 0.25, at)
    private val must = ReaderHighlight("h2", """{"href":"ch2.xhtml"}""", "I must not <fear>.", 0xFFB4D4F5.toInt(), at)

    private val document = AnnotationDocument(
        bookTitle = "Dune",
        author = "Frank Herbert",
        sha256 = "abc",
        exportedAt = at,
        chapters = listOf(
            AnnotationChapter("Chapter One", listOf(AnnotationItem.Highlight(fear), AnnotationItem.Bookmark(mark))),
            AnnotationChapter("Chapter *Two*", listOf(AnnotationItem.Highlight(must))),
        ),
    )

    @Test
    fun markdown() = assertEquals(
        """
        # Dune

        Frank Herbert

        *2 highlights · 1 note · 1 bookmark · Exported from Open Reader on 2023-11-14*

        ## Chapter One

        > Fear is the mind-killer.

        Litany\
        against fear

        *Squiggly underline · #F6E27A · 2023-11-14*

        **Bookmark** · 25% · 2023-11-14

        ## Chapter \*Two\*

        > I must not \<fear\>.

        *Highlight · #B4D4F5 · 2023-11-14*

        """.trimIndent(),
        AnnotationRenderer.render(document, AnnotationFormat.Markdown, ZoneOffset.UTC),
    )

    @Test
    fun html() {
        val html = AnnotationRenderer.render(document, AnnotationFormat.Html, ZoneOffset.UTC)
        val body = html.substringAfter("<body>\n")
        assertEquals(
            """
            <header>
            <h1>Dune</h1>
            <p class="author">Frank Herbert</p>
            <p class="meta">2 highlights · 1 note · 1 bookmark · Exported from Open Reader on 2023-11-14</p>
            </header>
            <section>
            <h2>Chapter One</h2>
            <figure>
            <blockquote><span style="text-decoration: underline wavy 2px #F6E27A">Fear is the mind-killer.</span></blockquote>
            <p class="note">Litany<br>against fear</p>
            <figcaption>Squiggly underline · #F6E27A · 2023-11-14</figcaption>
            </figure>
            <p class="bookmark"><strong>Bookmark</strong> · 25% · 2023-11-14</p>
            </section>
            <section>
            <h2>Chapter *Two*</h2>
            <figure>
            <blockquote><span style="background-color: #B4D4F5">I must not &lt;fear&gt;.</span></blockquote>
            <figcaption>Highlight · #B4D4F5 · 2023-11-14</figcaption>
            </figure>
            </section>
            </body>
            </html>

            """.trimIndent(),
            body,
        )
        assert(html.startsWith("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n"))
        assert("<title>Dune · Annotations</title>" in html)
    }

    @Test
    fun json() = assertEquals(
        """
        {
            "format": "open-reader-annotations",
            "version": 1,
            "exportedAt": "2023-11-14T22:13:20Z",
            "book": {
                "sha256": "abc",
                "title": "Dune",
                "author": "Frank Herbert"
            },
            "highlights": [
                {
                    "id": "h1",
                    "chapter": "Chapter One",
                    "text": "Fear is the mind-killer.",
                    "note": "Litany\nagainst fear",
                    "style": "squiggly",
                    "color": "#FFF6E27A",
                    "createdAt": "2023-11-14T22:13:20Z",
                    "modifiedAt": "2023-11-14T22:13:25Z",
                    "locator": {
                        "href": "ch1.xhtml",
                        "locations": {
                            "cfi": "epubcfi(/6/4!/4/2,/1:0,/1:24)"
                        }
                    }
                },
                {
                    "id": "h2",
                    "chapter": "Chapter *Two*",
                    "text": "I must not <fear>.",
                    "style": "highlight",
                    "color": "#FFB4D4F5",
                    "createdAt": "2023-11-14T22:13:20Z",
                    "locator": {
                        "href": "ch2.xhtml"
                    }
                }
            ],
            "bookmarks": [
                {
                    "id": "b1",
                    "chapter": "Chapter One",
                    "chapterId": "ch1",
                    "progression": 0.25,
                    "createdAt": "2023-11-14T22:13:20Z",
                    "locator": {
                        "href": "ch1.xhtml",
                        "locations": {
                            "progression": 0.5
                        }
                    }
                }
            ]
        }

        """.trimIndent(),
        AnnotationRenderer.render(document, AnnotationFormat.Json, ZoneOffset.UTC),
    )

    @Test
    fun emptyBook() {
        val empty = AnnotationDocument("Dune", null, "abc", at, emptyList())
        assertEquals(
            "# Dune\n\n*No highlights or bookmarks · Exported from Open Reader on 2023-11-14*\n",
            AnnotationRenderer.render(empty, AnnotationFormat.Markdown, ZoneOffset.UTC),
        )
    }

    @Test
    fun markdownEscapesWhatWouldBecomeMarkup() {
        val list = ReaderHighlight("h", "{}", "- not a list\n# not a heading", -1, at)
        val doc = AnnotationDocument("A_B", null, null, at, listOf(AnnotationChapter(null, listOf(AnnotationItem.Highlight(list)))))
        val md = AnnotationRenderer.render(doc, AnnotationFormat.Markdown, ZoneOffset.UTC)
        assert(md.startsWith("# A\\_B\n")) { md }
        assert("> \\- not a list\n> \\# not a heading\n" in md) { md }
    }

    @Test
    fun fileNames() {
        assertEquals("Dune - annotations.md", exportFileName("Dune", AnnotationFormat.Markdown))
        assertEquals("What If - annotations.html", exportFileName("What If?", AnnotationFormat.Html))
        assertEquals("AC DC - annotations.json", exportFileName("AC/DC", AnnotationFormat.Json))
        assertEquals("Book - annotations.json", exportFileName("///", AnnotationFormat.Json))
    }
}
