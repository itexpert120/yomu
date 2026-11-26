package com.itexpert120.yomu.data.reader.foliate

import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.ReaderFont
import com.itexpert120.yomu.core.model.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FoliateFontTest {
    private val garamond = CustomFontRef(
        family = "EB Garamond",
        regularPath = "/data/fonts/aaa-regular.woff2",
        italicPath = "/data/fonts/aaa-italic.woff2",
    )
    private val literata = CustomFontRef(family = "Literata", regularPath = "/data/fonts/bbb-regular.woff2")

    @Test
    fun bundledFontLoadsItsAssetFiles() {
        val font = FoliateFont.of(ReaderSettings(font = ReaderFont.Rubik))
        assertEquals("Rubik", font.family)
        assertEquals(
            listOf(
                FoliateFontFace("/assets/fonts/Rubik-Regular.ttf"),
                FoliateFontFace("/assets/fonts/Rubik-Italic.ttf", style = "italic"),
            ),
            font.faces,
        )
        assertTrue(font.userFiles.isEmpty())
    }

    @Test
    fun everyBundledFontNamesFilesThatShip() {
        val shipped = File("src/main/assets/fonts").list().orEmpty().toSet()
        ReaderFont.entries.forEach { bundled ->
            FoliateFont.of(ReaderSettings(font = bundled)).faces.forEach { face ->
                assertTrue("${face.src} is not bundled", face.src.removePrefix("/assets/fonts/") in shipped)
            }
        }
    }

    @Test
    fun cardoAlsoLoadsItsBold() {
        val faces = FoliateFont.of(ReaderSettings(font = ReaderFont.Cardo)).faces
        assertTrue(FoliateFontFace("/assets/fonts/Cardo-Bold.ttf", weight = "bold") in faces)
    }

    @Test
    fun customFontWinsAndServesOnlyItsOwnFiles() {
        val font = FoliateFont.of(ReaderSettings(font = ReaderFont.Rubik, customFont = garamond))
        assertEquals("EB Garamond", font.family)
        assertEquals(
            listOf(
                FoliateFontFace("/userfonts/aaa-regular.woff2"),
                FoliateFontFace("/userfonts/aaa-italic.woff2", style = "italic"),
            ),
            font.faces,
        )
        assertEquals(
            mapOf("aaa-regular.woff2" to File(garamond.regularPath), "aaa-italic.woff2" to File(garamond.italicPath!!)),
            font.userFiles,
        )
    }

    @Test
    fun switchingInstalledFontsChangesTheUrl() {
        val first = FoliateFont.of(ReaderSettings(customFont = garamond)).faces.first().src
        val second = FoliateFont.of(ReaderSettings(customFont = literata)).faces.first().src
        assertNotEquals(first, second)
        assertEquals(1, FoliateFont.of(ReaderSettings(customFont = literata)).faces.size)
    }
}
