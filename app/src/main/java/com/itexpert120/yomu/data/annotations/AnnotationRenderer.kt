package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** The formats a book's annotations export to. */
enum class AnnotationFormat(val extension: String, val mimeType: String) {
    Markdown("md", "text/markdown"),
    Html("html", "text/html"),

    /** Open Reader's own format ([AnnotationFile]); the only one that imports back. */
    Json("json", AnnotationFile.MIME_TYPE),
}

/** Renders an [AnnotationDocument] for people (Markdown, HTML) or for import ([AnnotationFile] JSON). */
object AnnotationRenderer {

    fun render(document: AnnotationDocument, format: AnnotationFormat, zone: ZoneId = ZoneId.systemDefault()): String = when (format) {
        AnnotationFormat.Markdown -> markdown(document, zone)
        AnnotationFormat.Html -> html(document, zone)
        AnnotationFormat.Json -> AnnotationFileCodec.encode(AnnotationFileCodec.fileOf(document))
    }

    fun markdown(document: AnnotationDocument, zone: ZoneId): String = buildString {
        append("# ").append(md(document.bookTitle)).append("\n\n")
        document.author?.takeIf { it.isNotBlank() }?.let { append(md(it)).append("\n\n") }
        append('*').append(summary(document, zone)).append("*\n")
        for (chapter in document.chapters) {
            append('\n')
            chapter.title?.let { append("## ").append(md(it)).append("\n\n") }
            chapter.items.forEachIndexed { index, item ->
                if (index > 0) append('\n')
                when (item) {
                    is AnnotationItem.Highlight -> {
                        val h = item.highlight
                        h.text.trim().lines().forEach { line -> append("> ").append(md(line)).append('\n') }
                        h.note?.let { note -> append('\n').append(note.lines().joinToString("\\\n") { md(it) }).append('\n') }
                        append('\n').append('*').append(highlightCaption(h, zone)).append("*\n")
                    }
                    is AnnotationItem.Bookmark -> append("**Bookmark** · ").append(bookmarkCaption(item.bookmark, zone)).append('\n')
                }
            }
        }
    }

    fun html(document: AnnotationDocument, zone: ZoneId): String = buildString {
        append("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>").append(esc(document.bookTitle)).append(" · Annotations</title>\n")
        append("<style>\n").append(CSS).append("\n</style>\n</head>\n<body>\n<header>\n")
        append("<h1>").append(esc(document.bookTitle)).append("</h1>\n")
        document.author?.takeIf { it.isNotBlank() }?.let { append("<p class=\"author\">").append(esc(it)).append("</p>\n") }
        append("<p class=\"meta\">").append(esc(summary(document, zone))).append("</p>\n</header>\n")
        for (chapter in document.chapters) {
            append("<section>\n")
            chapter.title?.let { append("<h2>").append(esc(it)).append("</h2>\n") }
            for (item in chapter.items) {
                when (item) {
                    is AnnotationItem.Highlight -> {
                        val h = item.highlight
                        append("<figure>\n<blockquote><span style=\"").append(markStyle(h)).append("\">")
                        append(esc(h.text.trim()).replace("\n", "<br>")).append("</span></blockquote>\n")
                        h.note?.let { append("<p class=\"note\">").append(esc(it).replace("\n", "<br>")).append("</p>\n") }
                        append("<figcaption>").append(esc(highlightCaption(h, zone))).append("</figcaption>\n</figure>\n")
                    }
                    is AnnotationItem.Bookmark ->
                        append("<p class=\"bookmark\"><strong>Bookmark</strong> · ").append(esc(bookmarkCaption(item.bookmark, zone))).append("</p>\n")
                }
            }
            append("</section>\n")
        }
        append("</body>\n</html>\n")
    }

    private fun summary(document: AnnotationDocument, zone: ZoneId): String {
        val counts = listOf(
            count(document.highlightCount, "highlight"),
            count(document.noteCount, "note"),
            count(document.bookmarkCount, "bookmark"),
        ).filter { it.isNotEmpty() }
        val what = counts.joinToString(" · ").ifEmpty { "No highlights or bookmarks" }
        return "$what · Exported from Open Reader on ${date(document.exportedAt, zone)}"
    }

    private fun count(n: Int, noun: String) = when (n) {
        0 -> ""
        1 -> "1 $noun"
        else -> "$n ${noun}s"
    }

    private fun highlightCaption(h: ReaderHighlight, zone: ZoneId) = "${styleLabel(h.style)} · ${rgbHex(h.colorArgb)} · ${date(h.createdAt, zone)}"

    private fun bookmarkCaption(b: ReaderBookmark, zone: ZoneId): String {
        val at = b.progression?.let { "${(it * 100).roundToInt().coerceIn(0, 100)}% · " }.orEmpty()
        return at + date(b.createdAt, zone)
    }

    private fun styleLabel(style: HighlightStyle) = when (style) {
        HighlightStyle.Highlight -> "Highlight"
        HighlightStyle.Underline -> "Underline"
        HighlightStyle.Squiggly -> "Squiggly underline"
        HighlightStyle.Strikethrough -> "Strikethrough"
    }

    private fun markStyle(h: ReaderHighlight): String {
        val color = rgbHex(h.colorArgb)
        return when (h.style) {
            HighlightStyle.Highlight -> "background-color: $color"
            HighlightStyle.Underline -> "text-decoration: underline 2px $color"
            HighlightStyle.Squiggly -> "text-decoration: underline wavy 2px $color"
            HighlightStyle.Strikethrough -> "text-decoration: line-through 2px $color"
        }
    }

    private fun date(epochMillis: Long, zone: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    // Markdown: backslash-escape what would otherwise start emphasis, code, links, HTML or a block.
    private fun md(text: String): String {
        val escaped = text.replace(Regex("""([\\`*_\[\]<>])"""), """\\$1""")
        return escaped.replace(Regex("""^(\s*)([#+\-]|\d+\.)(\s)"""), "$1\\\\$2$3")
    }

    private fun esc(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    private val CSS = """
        body { font-family: Georgia, serif; max-width: 42rem; margin: 2rem auto; padding: 0 1rem; line-height: 1.5; color: #1c1b1a; }
        h1 { margin-bottom: 0.25rem; }
        .author { margin-top: 0; font-size: 1.1rem; }
        .meta, figcaption, .bookmark { font-family: system-ui, sans-serif; font-size: 0.85rem; color: #6b6862; }
        h2 { margin-top: 2.5rem; font-family: system-ui, sans-serif; font-size: 1.1rem; }
        figure { margin: 1.25rem 0; }
        blockquote { margin: 0; }
        .note { margin: 0.5rem 0 0; padding-left: 0.75rem; border-left: 3px solid #d6d3cc; }
    """.trimIndent()
}
