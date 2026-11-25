package com.itexpert120.yomu.reader.web

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SearchTextTest {

    @Test
    fun folds_case_diacritics_and_whitespace() {
        assertEquals("cafe creme au lait", SearchText.fold("Café  Crème\n\tau LAIT"))
        assertEquals("cooperate", SearchText.fold("co­operate"))
        val out = StringBuilder()
        SearchText.foldMarkup("Fish & Chips &amp c&eacute;", out)
        assertEquals("fish & chips &amp c&eacute;", out.toString())
    }

    @Test
    fun markup_text_joins_across_tags_like_the_page() {
        val out = StringBuilder()
        SearchText.foldMarkup(
            """<html><head><style>p { color: red }</style></head><body>
              <p>The <i>Fool</i> &amp; the&#160;Tarot</p><!-- note --><script>var x</script>
              <p>Klein Moretti</p></body></html>""",
            out,
        )
        assertEquals(" the fool & the tarot klein moretti", out.toString())
    }

    @Test
    fun finds_only_sections_whose_text_matches() {
        val epub = File.createTempFile("search", ".epub")
        try {
            ZipOutputStream(epub.outputStream()).use { zip ->
                fun put(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
                put("a.xhtml", "<p>Nothing here</p>")
                put("b.xhtml", "<p>A <b>Beyon</b>der appears</p>")
                put("c.xhtml", "<p class=\"beyonder\">other</p>")
                put("d.xhtml", "<p>BEYONDÉR</p>")
            }
            val paths = listOf("a.xhtml", "b.xhtml", "c.xhtml", "missing.xhtml", "d.xhtml")
            val needle = SearchText.fold(" Beyonder ").trim()
            val found = ZipFile(epub).use { zip -> paths.indices.filter { SearchText.chapterMayContain(zip, paths[it], needle) } }
            assertEquals(listOf(1, 4), found)
        } finally {
            epub.delete()
        }
    }
}
