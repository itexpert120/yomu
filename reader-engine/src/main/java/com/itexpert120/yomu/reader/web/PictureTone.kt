package com.itexpert120.yomu.reader.web

/**
 * What the reader page measured of a picture in a book (yomu-reader.js `pictureStats`), to decide
 * whether it's line art that should be drawn light on a dark page.
 *
 * Tones are over a sample of the picture (its own pixels up to 256 px on the long side). A pixel is
 * opaque from half alpha; dark below 0.2 luminance, light above 0.8; colourful above 0.15 chroma.
 */
data class PictureStats(
    /** Share of the sampled pixels that are opaque. */
    val opaque: Double,
    /** Mean chroma (max − min channel, 0..1) of the opaque pixels. */
    val saturation: Double,
    /** Share of opaque pixels that are colourful. */
    val colorful: Double,
    /** Shares of opaque pixels that are dark and light. */
    val dark: Double,
    val light: Double,
    /** Share of the four corner patches (12% of the short side) that is light or transparent. */
    val corners: Double,
    /** The picture's own size, in pixels. */
    val naturalWidth: Int,
    val naturalHeight: Int,
    /** Its size on the page, in lines of the text around it. */
    val renderedWidthLines: Double,
    val renderedHeightLines: Double,
    /** The markup calls it a decoration (an ornament, divider, scene break… by class, id or role). */
    val decorationHint: Boolean = false,
    /** The markup calls it an illustration or cover (epub:type, a captioned figure). */
    val illustrationHint: Boolean = false,
    /** An `img` with an empty `alt`: the markup says there's nothing to describe. */
    val emptyAlt: Boolean = false,
)

/**
 * The rule for drawing book pictures light on a dark reading theme (invert, so black lines on white
 * read as light lines on the page). Only line art qualifies: anything with colour, any tonal picture
 * (manga, engravings, photos: many mid-greys, dark corners) and anything marked as an illustration
 * stays as published. When unsure, it stays.
 *
 * Size is the strongest signal, as publishers name their classes every which way and `alt` says
 * little: a picture no bigger than a few lines (an ornament, a scene break, a glyph set in the text)
 * that's colourless is line art when the markup marks it (a decoration class, an empty `alt`), and
 * otherwise when its corners are light and it's mostly near-black or near-white (tiny marks are
 * mostly anti-aliasing, so not by much); a bigger one must be almost purely two-tone (a handwritten
 * note, a page of lettering), which then reads like text.
 */
object PictureTone {
    // Colour: any meaningful amount keeps the picture as it is.
    private const val MAX_SATURATION = 0.05
    private const val MAX_COLORFUL = 0.05

    // A picture this many lines tall and wide or smaller (or this many pixels) is small.
    private const val SMALL_LINES = 3.0
    private const val SMALL_PIXELS = 64

    // A divider: wide and short.
    private const val DIVIDER_ASPECT = 4.0
    private const val DIVIDER_LINES = 1.5

    // Light corners: small marks may touch theirs (a round ornament, a note glyph).
    private const val SMALL_CORNERS = 0.5
    private const val LARGE_CORNERS = 0.9

    // Share of near-black and near-white pixels: tiny anti-aliased marks never reach 0.85.
    private const val SMALL_TWO_TONE = 0.60
    private const val LARGE_TWO_TONE = 0.96
    private const val LARGE_TWO_TONE_HINTED = 0.90

    // Mostly see-through: what's drawn must be dark (light shapes already show on a dark page).
    private const val MOSTLY_CLEAR = 0.8

    /** Whether [stats] describe line art to invert on a dark page. */
    fun invertsOnDarkPages(stats: PictureStats): Boolean = with(stats) {
        if (illustrationHint) return false
        if (saturation >= MAX_SATURATION || colorful >= MAX_COLORFUL) return false
        if (opaque <= 0.0) return false
        if (opaque < MOSTLY_CLEAR && dark < light) return false
        val twoTone = dark + light
        if (isSmall()) {
            decorationHint || emptyAlt || (corners >= SMALL_CORNERS && twoTone >= SMALL_TWO_TONE)
        } else {
            corners >= LARGE_CORNERS && twoTone >= (if (decorationHint) LARGE_TWO_TONE_HINTED else LARGE_TWO_TONE)
        }
    }

    private fun PictureStats.isSmall(): Boolean {
        if (maxOf(naturalWidth, naturalHeight) in 1..SMALL_PIXELS) return true
        if (renderedWidthLines <= SMALL_LINES && renderedHeightLines <= SMALL_LINES) return true
        val aspect = if (naturalHeight > 0) naturalWidth.toDouble() / naturalHeight else 0.0
        return aspect >= DIVIDER_ASPECT && renderedHeightLines <= DIVIDER_LINES
    }
}
