package com.itexpert120.yomu.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorPositionsTest {
    private val poem = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>The Waste Land</title>
        <style>h2 { font-size: 2em }</style></head>
        <body>
          <section id="ch1"><h2>One</h2><p>aaaaaaaaaa</p></section>
          <section id="ch2"><h2>Two</h2><p>bbbbbbbbbb</p></section>
          <section id='ch3'><h2>Six</h2><p>cc&amp;cc</p><a name="notes"/></section>
        </body></html>
    """.trimIndent()

    @Test
    fun anchorsStartAtTheShareOfTextBeforeThem() {
        val positions = anchorPositions(poem, setOf("ch1", "ch2", "ch3", "notes"))
        // Visible text: One(3) a×10 Two(3) b×10 Six(3) cc&cc(5) = 34; head, title and style don't count.
        assertEquals(0.0, positions.getValue("ch1"), 1e-9)
        assertEquals(13.0 / 34, positions.getValue("ch2"), 1e-9)
        assertEquals(26.0 / 34, positions.getValue("ch3"), 1e-9)
        assertEquals(1.0, positions.getValue("notes"), 1e-9)
    }

    @Test
    fun sectionsComeInDocumentOrder() {
        val p = anchorPositions(poem, setOf("ch1", "ch2", "ch3"))
        assertTrue(p.getValue("ch1") < p.getValue("ch2") && p.getValue("ch2") < p.getValue("ch3"))
    }

    @Test
    fun missingIdsAreAbsentAndOthersIgnored() {
        val p = anchorPositions(poem, setOf("ch2", "nowhere"))
        assertEquals(setOf("ch2"), p.keys)
        assertFalse("ch1" in p)
    }

    @Test
    fun quotedGreaterThanAndCommentsDoNotBreakTags() {
        val markup = """<body><!-- <p id="x">no</p> --><p title="a > b">xx</p><p id="y">yy</p></body>"""
        val p = anchorPositions(markup, setOf("x", "y"))
        assertEquals(setOf("y"), p.keys)
        assertEquals(0.5, p.getValue("y"), 1e-9)
    }

    @Test
    fun nonAsciiIdsMatch() {
        val p = anchorPositions("""<body><p>abcd</p><h1 id="第1章">漢字</h1></body>""", setOf("第1章"))
        assertEquals(4.0 / 6, p.getValue("第1章"), 1e-9)
    }
}
