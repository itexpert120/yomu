package com.itexpert120.yomu.data.reader.readium

import android.os.Parcel
import android.os.Parcelable
import com.itexpert120.yomu.core.reader.HighlightStyle
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import java.net.URLEncoder

/**
 * Readium draws highlights and underlines itself; a squiggly underline and a strikethrough are
 * decoration styles of our own, drawn by the HTML templates in [readerDecorationTemplates].
 */
internal data class SquigglyStyle(override val tint: Int) :
    Decoration.Style,
    Decoration.Style.Tinted {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeInt(tint)

    companion object CREATOR : Parcelable.Creator<SquigglyStyle> {
        override fun createFromParcel(source: Parcel) = SquigglyStyle(source.readInt())

        override fun newArray(size: Int) = arrayOfNulls<SquigglyStyle>(size)
    }
}

internal data class StrikethroughStyle(override val tint: Int) :
    Decoration.Style,
    Decoration.Style.Tinted {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeInt(tint)

    companion object CREATOR : Parcelable.Creator<StrikethroughStyle> {
        override fun createFromParcel(source: Parcel) = StrikethroughStyle(source.readInt())

        override fun newArray(size: Int) = arrayOfNulls<StrikethroughStyle>(size)
    }
}

/** The decoration style a highlight in [style] draws with, in colour [tint]. */
internal fun highlightDecorationStyle(style: HighlightStyle, tint: Int): Decoration.Style = when (style) {
    HighlightStyle.Highlight -> Decoration.Style.Highlight(tint = tint)
    HighlightStyle.Underline -> Decoration.Style.Underline(tint = tint)
    HighlightStyle.Squiggly -> SquigglyStyle(tint)
    HighlightStyle.Strikethrough -> StrikethroughStyle(tint)
}

/** Readium's default templates, plus ours for the squiggly and strikethrough styles. */
internal fun readerDecorationTemplates(): HtmlDecorationTemplates = HtmlDecorationTemplates.defaultTemplates().apply {
    set(
        SquigglyStyle::class,
        HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            element = { decoration ->
                val color = cssHex((decoration.style as? Decoration.Style.Tinted)?.tint ?: 0)
                val wave = "<svg xmlns='http://www.w3.org/2000/svg' width='8' height='4'>" +
                    "<path d='M0 3 Q2 0 4 3 T8 3' fill='none' stroke='$color' stroke-width='1.4'/></svg>"
                val uri = "data:image/svg+xml," + URLEncoder.encode(wave, "UTF-8").replace("+", "%20")
                """<div class="yomu-squiggly" style="background-image: url(&quot;$uri&quot;)"/>"""
            },
            stylesheet = """
                .yomu-squiggly {
                    background-repeat: repeat-x;
                    background-position: left bottom;
                    background-size: 8px 4px;
                    margin-bottom: -3px;
                }
            """.trimIndent(),
        ),
    )
    set(
        StrikethroughStyle::class,
        HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            element = { decoration ->
                val color = cssHex((decoration.style as? Decoration.Style.Tinted)?.tint ?: 0)
                """<div class="yomu-strike" style="--yomu-strike: $color"/>"""
            },
            stylesheet = """
                .yomu-strike {
                    background: linear-gradient(to bottom, transparent calc(55% - 1.25px),
                        var(--yomu-strike) calc(55% - 1.25px), var(--yomu-strike) calc(55% + 1.25px),
                        transparent calc(55% + 1.25px));
                }
            """.trimIndent(),
        ),
    )
}

private fun cssHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
