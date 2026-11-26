package com.itexpert120.yomu.reader.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are the stats yomu-reader.js computes, measured on a Yen Press/Kobo EPUB (Love Unseen
 * Beneath the Clear Night Sky), plus made-up cases for the rules alone.
 */
class PictureToneTest {
    private fun stats(
        sat: Double = 0.0,
        colorful: Double = 0.0,
        dark: Double,
        light: Double,
        corners: Double,
        width: Int,
        height: Int,
        widthLines: Double,
        heightLines: Double,
        opaque: Double = 1.0,
        decoration: Boolean = false,
        illustration: Boolean = false,
        emptyAlt: Boolean = false,
    ) = PictureStats(opaque, sat, colorful, dark, light, corners, width, height, widthLines, heightLines, decoration, illustration, emptyAlt)

    private fun inverts(s: PictureStats) = PictureTone.invertsOnDarkPages(s)

    // Love Unseen Beneath the Clear Night Sky: tiny ornaments and glyphs, whatever their class or alt

    @Test
    fun loveUnseenOrnamentsAndGlyphsInvert() {
        val small = listOf(
            stats(sat = 0.004, dark = 0.127, light = 0.726, corners = 1.0, width = 40, height = 41, widthLines = 1.5, heightLines = 1.5), // Art_cir
            stats(sat = 0.006, dark = 0.234, light = 0.55, corners = 1.0, width = 18, height = 19, widthLines = 0.7, heightLines = 0.7), // Art_sborn, 0.78
            stats(sat = 0.007, dark = 0.336, light = 0.517, corners = 0.938, width = 17, height = 34, widthLines = 0.7, heightLines = 1.3), // Art_sborn1
            stats(sat = 0.004, dark = 0.217, light = 0.667, corners = 1.0, width = 31, height = 35, widthLines = 1.2, heightLines = 1.3), // Art_sborn2
            stats(sat = 0.004, dark = 0.204, light = 0.741, corners = 0.861, width = 27, height = 41, widthLines = 1.0, heightLines = 1.5), // Art_music
            stats(sat = 0.006, dark = 0.303, light = 0.577, corners = 0.583, width = 28, height = 37, widthLines = 1.0, heightLines = 1.4), // Art_music2, alt="image"
            stats(sat = 0.004, dark = 0.251, light = 0.662, corners = 0.889, width = 22, height = 32, widthLines = 0.8, heightLines = 1.2), // music
        )
        small.forEach { assertTrue(it.toString(), inverts(it)) }
    }

    @Test
    fun loveUnseenColourCoverStays() {
        // The cover and colour inserts (0.07–0.27 saturation).
        assertFalse(inverts(stats(sat = 0.07, colorful = 0.2, dark = 0.1, light = 0.6, corners = 0.9, width = 1200, height = 1800, widthLines = 24.0, heightLines = 36.0)))
    }

    @Test
    fun loveUnseenFullPageMangaStays() {
        // Art_P1.jpg.
        assertFalse(inverts(stats(sat = 0.012, dark = 0.002, light = 0.148, corners = 0.189, width = 1200, height = 1800, widthLines = 24.0, heightLines = 36.0)))
    }

    @Test
    fun loveUnseenTitleAndBannerStay() {
        // Art_tit.jpg (0.69 two-tone) and Art_Pii.jpg, an 1800×153 banner set at the text's width.
        assertFalse(inverts(stats(sat = 0.005, dark = 0.0, light = 0.69, corners = 0.872, width = 1200, height = 1800, widthLines = 24.0, heightLines = 36.0)))
        assertFalse(inverts(stats(sat = 0.0, dark = 0.03, light = 0.768, corners = 0.639, width = 1800, height = 153, widthLines = 26.0, heightLines = 2.2)))
    }

    @Test
    fun loveUnseenPureTwoTonePagesInvert() {
        // Art_P247.jpg (376×472, a handwritten note) and Art_Piii.jpg (a near-white full page): pure
        // enough to read like text on a dark page.
        assertTrue(inverts(stats(sat = 0.003, dark = 0.0, light = 0.97, corners = 1.0, width = 376, height = 472, widthLines = 12.0, heightLines = 15.0)))
        assertTrue(inverts(stats(sat = 0.001, dark = 0.0, light = 0.992, corners = 0.998, width = 1200, height = 1800, widthLines = 24.0, heightLines = 36.0)))
    }

    // The rules on their own

    @Test
    fun blackLinesOnATransparentBackgroundInvert() {
        assertTrue(inverts(stats(opaque = 0.12, dark = 0.9, light = 0.02, corners = 1.0, width = 300, height = 40, widthLines = 12.0, heightLines = 1.4)))
    }

    @Test
    fun lightShapesOnATransparentBackgroundStay() {
        // Already light on a dark page; inverting would hide them.
        assertFalse(inverts(stats(opaque = 0.2, dark = 0.05, light = 0.9, corners = 1.0, width = 300, height = 40, widthLines = 12.0, heightLines = 1.4)))
    }

    @Test
    fun aWideDividerCountsAsSmall() {
        assertTrue(inverts(stats(dark = 0.1, light = 0.72, corners = 1.0, width = 600, height = 30, widthLines = 25.0, heightLines = 1.2)))
    }

    @Test
    fun aMarkedSmallPictureNeedsOnlyToBeColourless() {
        // A tiny mark that is mostly anti-aliasing (0.5 two-tone, a corner touched) with an ornament
        // class or an empty alt.
        val mark = stats(sat = 0.01, dark = 0.2, light = 0.3, corners = 0.4, width = 18, height = 19, widthLines = 0.7, heightLines = 0.7)
        assertFalse(inverts(mark))
        assertTrue(inverts(mark.copy(decorationHint = true)))
        assertTrue(inverts(mark.copy(emptyAlt = true)))
        assertFalse(inverts(mark.copy(emptyAlt = true, saturation = 0.2, colorful = 0.4)))
    }

    @Test
    fun anEmptyAltDoesNotMakeALargePictureLineArt() {
        assertFalse(inverts(stats(dark = 0.05, light = 0.8, corners = 1.0, width = 1200, height = 1800, widthLines = 24.0, heightLines = 36.0, emptyAlt = true)))
    }

    @Test
    fun anIllustrationHintAlwaysStays() {
        assertFalse(inverts(stats(dark = 0.02, light = 0.97, corners = 1.0, width = 30, height = 30, widthLines = 1.0, heightLines = 1.0, illustration = true)))
    }

    @Test
    fun aDecorationHintNeverOverridesColour() {
        assertFalse(inverts(stats(sat = 0.2, colorful = 0.3, dark = 0.2, light = 0.7, corners = 1.0, width = 30, height = 30, widthLines = 1.0, heightLines = 1.0, decoration = true)))
    }

    @Test
    fun aDecorationHintLoosensALargePicture() {
        val big = stats(dark = 0.05, light = 0.87, corners = 1.0, width = 800, height = 300, widthLines = 20.0, heightLines = 7.0)
        assertFalse(inverts(big))
        assertTrue(inverts(big.copy(decorationHint = true)))
    }
}
