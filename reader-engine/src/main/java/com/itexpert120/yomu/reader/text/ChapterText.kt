package com.itexpert120.yomu.reader.text

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineHeightSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import com.itexpert120.yomu.reader.epub.EpubBook
import org.xmlpull.v1.XmlPullParser

/**
 * Turns a chapter's XHTML into styled text for native layout. Supported: paragraphs and other
 * blocks, headings, emphasis, bold, small/sup/sub, line breaks, lists and block quotes. Images,
 * tables, links and publisher CSS come later; their text still flows.
 */
object ChapterText {

    fun parse(xhtml: String): Spanned {
        val builder = Builder()
        val parser = EpubBook.newParser(stripDoctype(xhtml))
        for ((name, value) in HtmlEntities) {
            runCatching { parser.defineEntityReplacementText(name, value) }
        }
        var skipDepth = 0
        while (true) {
            val event = try {
                parser.next()
            } catch (e: Exception) {
                // A malformed tail shouldn't lose the chapter: keep what was read.
                break
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfter(':').lowercase()
                    if (skipDepth > 0 || tag in SkippedTags) {
                        skipDepth++
                        continue
                    }
                    builder.open(tag)
                }
                XmlPullParser.END_TAG -> {
                    if (skipDepth > 0) {
                        skipDepth--
                        continue
                    }
                    builder.close(parser.name.substringAfter(':').lowercase())
                }
                XmlPullParser.TEXT -> if (skipDepth == 0) builder.text(parser.text)
            }
        }
        return builder.finish()
    }

    private fun stripDoctype(xhtml: String): String {
        val start = xhtml.indexOf("<!DOCTYPE", ignoreCase = true)
        if (start < 0) return xhtml
        val end = xhtml.indexOf('>', start)
        return if (end < 0) xhtml else xhtml.removeRange(start, end + 1)
    }

    private class Builder {
        val text = SpannableStringBuilder()
        private val open = ArrayList<Pair<String, Int>>()
        private var paragraphStart = 0
        private var pendingSpace = false
        private var listDepth = 0

        private val atParagraphStart get() = text.length == paragraphStart

        fun open(tag: String) {
            when (tag) {
                in BlockTags -> {
                    endParagraph()
                    if (tag == "li") {
                        text.append("• ")
                    }
                    if (tag == "ul" || tag == "ol") listDepth++
                }
                "br" -> {
                    text.append('\n')
                    pendingSpace = false
                }
            }
            open.add(tag to text.length)
        }

        fun close(tag: String) {
            val index = open.indexOfLast { it.first == tag }
            if (index < 0) return
            val start = open[index].second
            while (open.size > index) open.removeAt(open.lastIndex)
            val end = text.length
            if (tag in BlockTags) {
                if (end > start) style(tag, start, end)
                if (tag == "ul" || tag == "ol") listDepth--
                endParagraph()
            } else if (end > start) {
                style(tag, start, end)
            }
        }

        fun text(raw: String) {
            for (ch in raw) {
                if (ch.isWhitespace() && ch != ' ') {
                    pendingSpace = true
                } else {
                    if (pendingSpace && !atParagraphStart && text.lastOrNull() != '\n') text.append(' ')
                    pendingSpace = false
                    text.append(ch)
                }
            }
        }

        fun finish(): Spanned {
            endParagraph()
            // Drop the final paragraph break so the chapter doesn't end in an empty line.
            if (text.endsWith("\n")) text.delete(text.length - 1, text.length)
            return text
        }

        private fun endParagraph() {
            pendingSpace = false
            if (atParagraphStart) return
            if (text.last() != '\n') text.append('\n')
            text.setSpan(ParagraphGapSpan(PARAGRAPH_GAP_EM), paragraphStart, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            paragraphStart = text.length
        }

        private fun style(tag: String, start: Int, end: Int) {
            val spans: List<Any> = when (tag) {
                "em", "i", "cite", "dfn", "var" -> listOf(StyleSpan(Typeface.ITALIC))
                "strong", "b" -> listOf(StyleSpan(Typeface.BOLD))
                "small" -> listOf(RelativeSizeSpan(0.85f))
                "sup" -> listOf(SuperscriptSpan(), RelativeSizeSpan(0.75f))
                "sub" -> listOf(SubscriptSpan(), RelativeSizeSpan(0.75f))
                "h1" -> listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.5f))
                "h2" -> listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.3f))
                "h3" -> listOf(StyleSpan(Typeface.BOLD), RelativeSizeSpan(1.15f))
                "h4", "h5", "h6" -> listOf(StyleSpan(Typeface.BOLD))
                "blockquote" -> listOf(LeadingMarginSpan.Standard(QUOTE_INDENT_PX))
                else -> emptyList()
            }
            for (span in spans) text.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Adds space below a paragraph's last line, as a fraction of its line height. */
    class ParagraphGapSpan(private val em: Float) : LineHeightSpan {
        override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, lineHeight: Int, fm: Paint.FontMetricsInt) {
            val spanEnd = (text as? Spanned)?.getSpanEnd(this) ?: return
            if (end >= spanEnd) {
                val gap = ((fm.descent - fm.ascent) * em).toInt()
                fm.descent += gap
                fm.bottom += gap
            }
        }
    }

    private const val PARAGRAPH_GAP_EM = 0.6f
    private const val QUOTE_INDENT_PX = 48

    private val BlockTags = setOf(
        "p", "div", "section", "article", "header", "footer", "aside", "nav", "main", "figure", "figcaption",
        "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol", "dl", "dt", "dd", "blockquote", "pre", "hr",
        "table", "tr", "body",
    )

    private val SkippedTags = setOf("head", "script", "style", "title", "svg", "math", "noscript")

    private val HtmlEntities = mapOf(
        "nbsp" to " ", "mdash" to "—", "ndash" to "–", "hellip" to "…",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "copy" to "©", "reg" to "®", "trade" to "™", "middot" to "·",
        "laquo" to "«", "raquo" to "»", "shy" to "­", "thinsp" to " ",
        "ensp" to " ", "emsp" to " ", "zwnj" to "‌", "zwj" to "‍",
        "deg" to "°", "times" to "×", "eacute" to "é", "egrave" to "è",
        "aacute" to "á", "agrave" to "à", "ccedil" to "ç", "uuml" to "ü",
        "ouml" to "ö", "auml" to "ä", "szlig" to "ß", "bull" to "•",
        "prime" to "′", "Prime" to "″", "sect" to "§", "para" to "¶",
    )
}
