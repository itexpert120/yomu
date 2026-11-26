package com.itexpert120.yomu.data.fonts

import com.itexpert120.yomu.data.fonts.FontRepository.Companion.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class FontRepositoryParseTest {
    @Test
    fun trueTypeResponseHasOneFacePerStyle() {
        val css = """
            @font-face {
              font-family: 'EB Garamond';
              font-style: italic;
              font-weight: 400;
              src: url(https://fonts.gstatic.com/s/ebgaramond/v33/italic.ttf) format('truetype');
            }
            @font-face {
              font-family: 'EB Garamond';
              font-style: normal;
              font-weight: 400;
              src: url(https://fonts.gstatic.com/s/ebgaramond/v33/regular.ttf) format('truetype');
            }
        """.trimIndent()
        assertEquals(
            mapOf(
                FontStyle.ITALIC to "https://fonts.gstatic.com/s/ebgaramond/v33/italic.ttf",
                FontStyle.NORMAL to "https://fonts.gstatic.com/s/ebgaramond/v33/regular.ttf",
            ),
            FontRepository.parseFaces(css),
        )
    }

    @Test
    fun browserResponsePicksTheLatinSubset() {
        val css = """
            /* cyrillic */
            @font-face {
              font-style: normal;
              src: url(https://fonts.gstatic.com/cyrillic.woff2) format('woff2');
              unicode-range: U+0301, U+0400-045F;
            }
            /* latin */
            @font-face {
              font-style: normal;
              src: url(https://fonts.gstatic.com/latin.woff2) format('woff2');
              unicode-range: U+0000-00FF, U+0131;
            }
        """.trimIndent()
        assertEquals(mapOf(FontStyle.NORMAL to "https://fonts.gstatic.com/latin.woff2"), FontRepository.parseFaces(css))
    }

    @Test
    fun savedFilesKeepTheServedFormat() {
        assertEquals("ttf", FontRepository.fontExtension("https://fonts.gstatic.com/s/a/v1/x.ttf"))
        assertEquals("woff2", FontRepository.fontExtension("https://fonts.gstatic.com/s/a/v1/x.woff2"))
        assertEquals("ttf", FontRepository.fontExtension("https://fonts.gstatic.com/l/font?kit=abc"))
    }
}
