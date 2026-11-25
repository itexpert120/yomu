package com.itexpert120.yomu.data.annotations

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CalibreAnnotationsTest {
    @get:Rule val temp = TemporaryFolder()

    private val readingOrder = listOf("OEBPS/cover.xhtml", "OEBPS/ch1.xhtml", "OEBPS/ch2.xhtml", "OEBPS/ch3.xhtml")

    private val annotations = """
        [
          {"type":"highlight","uuid":"u1","timestamp":"2021-03-04T05:06:07.123Z","start_cfi":"/2/4/2/1:0","end_cfi":"/2/4/2/1:24",
           "spine_index":1,"spine_name":"ch1.xhtml","highlighted_text":"Fear is the mind-killer.","notes":"Litany",
           "style":{"kind":"color","type":"builtin","which":"green"},"toc_family_titles":["Part One","Chapter 1"]},
          {"type":"highlight","uuid":"u2","timestamp":"2021-03-04T05:06:08Z","start_cfi":"/2/4/2[p^]1]/1:10","end_cfi":"/2/4/4/1:5",
           "spine_index":2,"spine_name":"ch2.xhtml","highlighted_text":"wavy","style":{"kind":"decoration","type":"builtin","which":"wavy"}},
          {"type":"highlight","uuid":"u3","removed":true,"timestamp":"2021-03-04T05:06:08Z","start_cfi":"/2/4","end_cfi":"/2/6",
           "spine_index":2,"highlighted_text":"gone"},
          {"type":"bookmark","title":"Where I was","pos_type":"epubcfi","pos":"epubcfi(/8/2/4/2/1:3)","timestamp":"2021-03-05T00:00:00Z"},
          {"type":"last-read","pos":"epubcfi(/4/2/4)","pos_type":"epubcfi"}
        ]
    """.trimIndent()

    private val stored = "encoding=json+base64:" + Base64.getEncoder().encodeToString(annotations.toByteArray())

    @Test
    fun rangeCfisMatchFoliate() {
        // Same results as foliate-js fromCalibreHighlight for these inputs.
        assertEquals("epubcfi(/6/4!/4/2,/1:0,/1:24)", CalibreAnnotations.highlightCfi(1, "/2/4/2/1:0", "/2/4/2/1:24"))
        assertEquals("epubcfi(/6/6!/4,/2[p^]1]/1:10,/4/1:5)", CalibreAnnotations.highlightCfi(2, "/2/4/2[p^]1]/1:10", "/2/4/4/1:5"))
    }

    @Test
    fun stepsKeepAssertionsWhole() {
        assertEquals(listOf("/4", "/2[a/b]", "/1:3"), CalibreAnnotations.steps("/4/2[a/b]/1:3"))
    }

    @Test
    fun parsesHighlightsAndBookmarks() {
        val file = CalibreAnnotations.parse(stored, readingOrder)!!
        assertEquals(2, file.highlights.size)

        val fear = file.highlights[0]
        assertEquals("Fear is the mind-killer.", fear.text)
        assertEquals("Litany", fear.note)
        assertEquals("highlight", fear.style)
        assertEquals("#FFB8E3A8", fear.color)
        assertEquals("Chapter 1", fear.chapter)
        assertEquals("2021-03-04T05:06:07.123Z", fear.createdAt)
        assertEquals("2021-03-04T05:06:07.123Z", fear.modifiedAt)
        assertEquals(
            """{"href":"OEBPS/ch1.xhtml","type":"application/xhtml+xml","title":"Chapter 1",""" +
                """"locations":{"cfi":"epubcfi(/6/4!/4/2,/1:0,/1:24)"},"text":{"highlight":"Fear is the mind-killer."}}""",
            fear.locator.toLocatorJson(),
        )

        val wavy = file.highlights[1]
        assertEquals("squiggly", wavy.style)
        assertNull(wavy.note)
        assertNull(wavy.modifiedAt)

        val bookmark = file.bookmarks.single()
        assertEquals("Where I was", bookmark.chapter)
        assertEquals("OEBPS/ch3.xhtml", bookmark.chapterId)
        val locations = (bookmark.locator as JsonObject)["locations"]!!.jsonObject
        assertEquals("epubcfi(/6/8!/4/2/1:3)", locations["cfi"]!!.jsonPrimitive.content)
    }

    @Test
    fun importsLikeAnOpenReaderFile() {
        val file = CalibreAnnotations.parse(stored, readingOrder)!!
        var ids = 0
        val plan = AnnotationMerge.plan("book", file, emptyList(), emptyList(), newId = { "id-${++ids}" }, now = 0)
        assertEquals(2, plan.insertHighlights.size)
        assertEquals(1, plan.insertBookmarks.size)
        assertEquals(1_614_834_367_123, plan.insertHighlights[0].createdAt)
    }

    @Test
    fun readsTheEntryFromAnEpub() {
        val epub = temp.newFile("book.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(CalibreAnnotations.ENTRY))
            zip.write(stored.toByteArray())
            zip.closeEntry()
        }
        assertEquals(2, CalibreAnnotations.read(epub, readingOrder)?.highlights?.size)

        val plain = temp.newFile("plain.epub")
        ZipOutputStream(plain.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
        }
        assertNull(CalibreAnnotations.read(plain, readingOrder))
    }

    @Test
    fun ignoresAnythingElse() {
        assertNull(CalibreAnnotations.parse("not calibre", readingOrder))
        assertNull(CalibreAnnotations.parse("encoding=json+base64:!!!", readingOrder))
        val empty = "encoding=json+base64:" + Base64.getEncoder().encodeToString("[]".toByteArray())
        assertNull(CalibreAnnotations.parse(empty, readingOrder))
    }
}
