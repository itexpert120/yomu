package com.itexpert120.yomu.data.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class OpfNamesTest {
    private fun opf(metadata: String) = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" xml:lang="en">
        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$metadata</metadata><manifest/></package>"""

    @Test
    fun theMainTitleNotItsAlternateScripts() {
        val names = opfNames(
            opf(
                """
                <dc:title id="題名" xml:lang="ja-JP" dir="rtl">草枕</dc:title>
                <meta refines="#題名" property="title-type" id="title-type">main</meta>
                <meta refines="#題名" property="alternate-script" xml:lang="en">Kusamakura</meta>
                <dc:creator id="著者">夏目 漱石</dc:creator>
                <meta refines="#著者" property="alternate-script" xml:lang="en">Natsume, Sōseki</meta>
                """,
            ),
        )
        assertEquals("草枕", names.title)
        assertEquals(listOf("夏目 漱石"), names.creators)
    }

    @Test
    fun titleTypeMainWinsOverAnEarlierSubtitle() {
        val names = opfNames(
            opf(
                """<dc:title id="sub">A Subtitle</dc:title><dc:title id="t">The Title</dc:title>
                <meta refines="#t" property="title-type">main</meta>""",
            ),
        )
        assertEquals("The Title", names.title)
    }

    @Test
    fun aCreatorListedTwiceCountsOnceAndDctermsMetaIsIgnored() {
        val names = opfNames(
            opf(
                """<dc:creator>אורי עידן</dc:creator><meta property="dcterms:creator">אורי עידן</meta>
                <dc:title>מפליגים בישראל</dc:title><dc:creator>אורי עידן</dc:creator>""",
            ),
        )
        assertEquals("מפליגים בישראל", names.title)
        assertEquals(listOf("אורי עידן"), names.creators)
    }

    @Test
    fun entitiesAndEpub2Opf() {
        val names = opfNames(
            """<package><opf:metadata xmlns:opf="x"><dc:title>Tom &amp; Jerry&#x2019;s</dc:title>
            <dc:creator opf:role="aut" opf:file-as="Twain, Mark">Mark   Twain</dc:creator></opf:metadata></package>""",
        )
        assertEquals("Tom & Jerry’s", names.title)
        assertEquals(listOf("Mark Twain"), names.creators)
    }

    @Test
    fun noTitleNoCreators() {
        assertEquals(OpfNames(null, emptyList()), opfNames(opf("<dc:language>en</dc:language>")))
    }
}
