package com.itexpert120.yomu.reader.layout

import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** How a chapter is set: page size, margins and type. All sizes in pixels. */
data class PageSpec(
    val width: Int,
    val height: Int,
    val marginHorizontal: Int,
    val marginTop: Int,
    val marginBottom: Int,
    val textSizePx: Float,
    val lineSpacing: Float,
    val textColor: Int,
    val typeface: Typeface,
    val justify: Boolean = true,
) {
    val textWidth get() = width - 2 * marginHorizontal
    val textHeight get() = height - marginTop - marginBottom
}

/** One page: a band of whole lines of the chapter layout. */
data class PageSlice(val firstLine: Int, val lastLine: Int, val top: Int, val bottom: Int)

/**
 * A chapter laid out once with [StaticLayout] (justified, hyphenated), then cut into pages at line
 * boundaries. Layout is pure CPU work and may run off the main thread.
 */
class ChapterLayout(val text: CharSequence, val spec: PageSpec) {
    val layout: StaticLayout
    val pages: List<PageSlice>

    init {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = spec.textSizePx
            color = spec.textColor
            typeface = spec.typeface
        }
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, spec.textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spec.lineSpacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setJustificationMode(if (spec.justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .build()
        pages = paginate(layout, spec.textHeight)
    }

    /** Draws page [index] with its margins; the canvas is the page's full size. */
    fun drawPage(canvas: Canvas, index: Int) {
        val page = pages.getOrNull(index) ?: return
        val save = canvas.save()
        canvas.translate(spec.marginHorizontal.toFloat(), (spec.marginTop - page.top).toFloat())
        canvas.clipRect(0, page.top, spec.textWidth, page.bottom)
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    private companion object {
        fun paginate(layout: StaticLayout, height: Int): List<PageSlice> {
            if (layout.lineCount == 0) return listOf(PageSlice(0, 0, 0, 0))
            val pages = ArrayList<PageSlice>()
            var first = 0
            while (first < layout.lineCount) {
                val top = layout.getLineTop(first)
                var last = first
                // Whole lines only; a line taller than the page still gets a page of its own.
                while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= height) last++
                pages.add(PageSlice(first, last, top, layout.getLineBottom(last)))
                first = last + 1
            }
            return pages
        }
    }
}
