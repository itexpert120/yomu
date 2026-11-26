package com.itexpert120.yomu.data.reader.foliate

import com.itexpert120.yomu.core.model.ReaderSettings
import java.io.File

/** One `@font-face` of the reading font: [src] is a path on the reader page's origin. */
internal data class FoliateFontFace(val src: String, val style: String = "normal", val weight: String = "normal")

/**
 * The reading font as the foliate page loads it: its CSS [family], its [faces], and the
 * user-installed font files the page may fetch ([userFiles], served at `/userfonts/<name>`).
 */
internal data class FoliateFont(
    val family: String,
    val faces: List<FoliateFontFace>,
    val userFiles: Map<String, File> = emptyMap(),
) {
    companion object {
        const val USER_FONTS_PATH = "/userfonts/"

        /**
         * A custom font wins over the bundled one. Its files are served under their own names, so
         * switching from one installed font to another changes the URL: one shared name (say
         * `/userfonts/regular`) would let the WebView keep drawing the previous font from its cache.
         */
        fun of(settings: ReaderSettings): FoliateFont {
            val custom = settings.customFont
            if (custom != null) {
                val regular = File(custom.regularPath)
                val italic = custom.italicPath?.let(::File)
                return FoliateFont(
                    family = custom.family,
                    faces = listOfNotNull(
                        FoliateFontFace(USER_FONTS_PATH + regular.name),
                        italic?.let { FoliateFontFace(USER_FONTS_PATH + it.name, style = "italic") },
                    ),
                    userFiles = listOfNotNull(regular, italic).associateBy { it.name },
                )
            }
            val family = settings.font.cssFamily
            return FoliateFont(
                family = family,
                faces = listOfNotNull(
                    FoliateFontFace("/assets/fonts/$family-Regular.ttf"),
                    FoliateFontFace("/assets/fonts/$family-Italic.ttf", style = "italic"),
                    // Cardo is the one bundled family with a real bold.
                    if (family == "Cardo") FoliateFontFace("/assets/fonts/Cardo-Bold.ttf", weight = "bold") else null,
                ),
            )
        }
    }
}
