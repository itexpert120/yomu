package com.itexpert120.yomu.core.model

/**
 * A Google Font that can be installed (distinct from the bundled [ReaderFont]s). [category] is the
 * Google Fonts category ("Serif", "Sans Serif", "Display", "Handwriting", "Monospace").
 */
data class CuratedFont(val family: String, val category: String)

/**
 * Hand-picked, readable Google Fonts shown at the top of the "add fonts" sheet for quick discovery;
 * the full ~1900-family catalog is searchable below (bundled as assets/google_fonts.txt). Families
 * already bundled (Lora, Merriweather, Karla, Rubik, Nunito, Cardo) are intentionally omitted here.
 */
val CURATED_GOOGLE_FONTS: List<CuratedFont> = listOf(
    CuratedFont("EB Garamond", "Serif"),
    CuratedFont("Literata", "Serif"),
    CuratedFont("Source Serif 4", "Serif"),
    CuratedFont("PT Serif", "Serif"),
    CuratedFont("Bitter", "Serif"),
    CuratedFont("Crimson Pro", "Serif"),
    CuratedFont("Libre Baskerville", "Serif"),
    CuratedFont("Vollkorn", "Serif"),
    CuratedFont("Spectral", "Serif"),
    CuratedFont("Noto Serif", "Serif"),
    CuratedFont("Inter", "Sans Serif"),
    CuratedFont("Source Sans 3", "Sans Serif"),
    CuratedFont("Work Sans", "Sans Serif"),
    CuratedFont("Atkinson Hyperlegible", "Sans Serif"),
    CuratedFont("Lexend", "Sans Serif"),
)
