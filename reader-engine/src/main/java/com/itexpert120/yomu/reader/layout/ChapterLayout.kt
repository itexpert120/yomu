package com.itexpert120.yomu.reader.layout

import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.StyleSpan
import com.itexpert120.yomu.reader.text.ChapterText

/** How a chapter is set: page size, margins and type. All sizes in pixels. */
data class PageSpec(
    val width: Int,
    val height: Int,
    val marginHorizontal: Int,
    val marginTop: Int,
    val marginBottom: Int,
    val textSizePx: Float,
    /** Line height in ems, as CSS `line-height` (not Android's multiplier of the font's own). */
    val lineHeightEm: Float,
    /** Space after each paragraph, in ems. */
    val paragraphGapEm: Float,
    val textColor: Int,
    val typeface: Typeface,
    /** Used for italic runs when the font has a real italic; otherwise italics are slanted. */
    val italicTypeface: Typeface? = null,
    val justify: Boolean = false,
    val hyphenate: Boolean = false,
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
        prepareSpans(text, paint, spec)
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, spec.textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(if (spec.hyphenate) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE)
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
        /** Gives paragraph spans the font's metrics and the spec's spacing; swaps in a real italic. */
        fun prepareSpans(text: CharSequence, paint: TextPaint, spec: PageSpec) {
            if (text !is Spannable) return
            val metrics = paint.fontMetrics
            for (span in text.getSpans(0, text.length, ChapterText.ParagraphSpan::class.java)) {
                span.fontSizePx = paint.textSize
                span.ascentEm = metrics.ascent / paint.textSize
                span.descentEm = metrics.descent / paint.textSize
                span.lineHeightEm = spec.lineHeightEm
                span.gapEm = spec.paragraphGapEm
            }
            val italic = spec.italicTypeface ?: return
            for (span in text.getSpans(0, text.length, StyleSpan::class.java)) {
                if (span.style != Typeface.ITALIC) continue
                text.setSpan(TypefaceOverrideSpan(italic), text.getSpanStart(span), text.getSpanEnd(span), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.removeSpan(span)
            }
        }

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

/** Draws a run in a specific typeface (e.g. the font's real italic), keeping bold from the paint. */
private class TypefaceOverrideSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = apply(paint)

    override fun updateMeasureState(paint: TextPaint) = apply(paint)

    private fun apply(paint: TextPaint) {
        val bold = paint.typeface?.isBold == true
        paint.typeface = if (bold) Typeface.create(typeface, Typeface.BOLD) else typeface
    }
}
