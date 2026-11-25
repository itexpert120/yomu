package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.reader.HighlightStyle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.Base64
import java.util.zip.ZipFile

/**
 * Highlights and bookmarks Calibre's viewer stored inside an EPUB it exported
 * (`META-INF/calibre_bookmarks.txt`: `encoding=json+base64:` then a base64 JSON array), turned into
 * an [AnnotationFile] that imports like Open Reader's own. The CFI conversion follows foliate-js
 * (MIT; `epubcfi.js` fromCalibreHighlight / fromCalibrePos): Calibre writes in-document paths with a
 * leading `/2` step and the spine index separately, while a standard CFI starts at the package
 * document's spine (`/6/<2 × (index + 1)>!`).
 */
object CalibreAnnotations {
    const val ENTRY = "META-INF/calibre_bookmarks.txt"
    private const val MAGIC = "encoding=json+base64:"

    /** The annotations in [epub], or null when it has none (or they can't be read). */
    fun read(epub: File, readingOrder: List<String>): AnnotationFile? {
        val text = runCatching {
            ZipFile(epub).use { zip ->
                val entry = zip.getEntry(ENTRY) ?: return null
                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }.getOrNull() ?: return null
        return parse(text, readingOrder)
    }

    /**
     * [readingOrder]: the book's spine as locator hrefs, to turn Calibre's spine indices into hrefs.
     * Removed highlights, reading positions and anything unrecognised are skipped.
     */
    fun parse(text: String, readingOrder: List<String>): AnnotationFile? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(MAGIC)) return null
        val json = runCatching { String(Base64.getMimeDecoder().decode(trimmed.removePrefix(MAGIC)), Charsets.UTF_8) }.getOrNull() ?: return null
        val entries = parseJsonElement(json) as? JsonArray ?: return null
        val highlights = mutableListOf<AnnotationFileHighlight>()
        val bookmarks = mutableListOf<AnnotationFileBookmark>()
        for (entry in entries) {
            val obj = entry as? JsonObject ?: continue
            when (obj.str("type")) {
                "highlight" -> highlight(obj, readingOrder)?.let(highlights::add)
                "bookmark" -> bookmark(obj, readingOrder)?.let(bookmarks::add)
            }
        }
        if (highlights.isEmpty() && bookmarks.isEmpty()) return null
        return AnnotationFile(highlights = highlights, bookmarks = bookmarks)
    }

    private fun highlight(obj: JsonObject, readingOrder: List<String>): AnnotationFileHighlight? {
        if ((obj["removed"] as? JsonPrimitive)?.booleanOrNull == true) return null
        val text = obj.str("highlighted_text")?.takeIf { it.isNotBlank() } ?: return null
        val spine = (obj["spine_index"] as? JsonPrimitive)?.intOrNull ?: return null
        val start = obj.str("start_cfi") ?: return null
        val end = obj.str("end_cfi") ?: return null
        val cfi = highlightCfi(spine, start, end) ?: return null
        val href = readingOrder.getOrNull(spine) ?: obj.str("spine_name") ?: return null
        val chapter = (obj["toc_family_titles"] as? JsonArray)?.lastOrNull()?.let { (it as? JsonPrimitive)?.content }
        val (style, color) = style(obj["style"] as? JsonObject)
        val timestamp = obj.str("timestamp")?.takeIf { parseInstant(it) != null }
        val note = obj.str("notes")?.takeIf { it.isNotBlank() }
        return AnnotationFileHighlight(
            chapter = chapter,
            text = text,
            note = note,
            style = style.key,
            color = argbHex(color),
            createdAt = timestamp,
            // A note counts as an edit, so it beats an unedited copy of the same highlight on import.
            modifiedAt = timestamp?.takeIf { note != null },
            locator = locator(href, cfi, chapter, text),
        )
    }

    private fun bookmark(obj: JsonObject, readingOrder: List<String>): AnnotationFileBookmark? {
        if (obj.str("pos_type") != "epubcfi") return null
        val pos = obj.str("pos") ?: return null
        val steps = steps(unwrap(pos).substringBefore('!'))
        if (steps.size < 2) return null
        val spine = stepIndex(steps[0])?.let { it / 2 - 1 } ?: return null
        val href = readingOrder.getOrNull(spine) ?: return null
        val cfi = "epubcfi(/6${steps[0]}!${steps.drop(2).joinToString("")})"
        val title = obj.str("title")
        return AnnotationFileBookmark(
            chapter = title,
            chapterId = href,
            createdAt = obj.str("timestamp")?.takeIf { parseInstant(it) != null },
            locator = locator(href, cfi, title, null),
        )
    }

    private fun locator(href: String, cfi: String, title: String?, text: String?): JsonElement = buildJsonObject {
        put("href", href)
        put("type", "application/xhtml+xml")
        title?.let { put("title", it) }
        put("locations", buildJsonObject { put("cfi", cfi) })
        // Lets the Readium engine, which can't read CFIs, find the highlight by its text.
        text?.let { put("text", buildJsonObject { put("highlight", it) }) }
    }

    /** A range CFI for a highlight: the shared leading steps as the parent, then start and end. */
    internal fun highlightCfi(spineIndex: Int, startCfi: String, endCfi: String): String? {
        if (spineIndex < 0) return null
        val from = steps(startCfi.drop(2))
        val to = steps(endCfi.drop(2))
        if (from.isEmpty() || to.isEmpty()) return null
        var shared = 0
        while (shared < minOf(from.size, to.size) && sameParentStep(from[shared], to[shared])) shared++
        val parent = from.take(shared).joinToString("")
        val start = from.drop(shared).joinToString("")
        val end = to.drop(shared).joinToString("")
        return "epubcfi(/6/${(spineIndex + 1) * 2}!$parent,$start,$end)"
    }

    // As foliate-js: the same index, and neither with a (non-zero) character offset.
    private fun sameParentStep(a: String, b: String) = stepIndex(a) == stepIndex(b) && !hasOffset(a) && !hasOffset(b)

    private fun stepIndex(step: String): Int? = step.drop(1).takeWhile(Char::isDigit).toIntOrNull()

    private fun hasOffset(step: String): Boolean {
        val offset = withoutAssertions(step).substringAfter(':', "").takeWhile { it.isDigit() }
        return offset.isNotEmpty() && offset.toInt() != 0
    }

    private fun unwrap(cfi: String) = cfi.trim().removePrefix("epubcfi(").removeSuffix(")")

    /** Splits a CFI path into its `/n…` steps, keeping `[…]` assertions (which may hold a `/`) intact. */
    internal fun steps(path: String): List<String> {
        val steps = mutableListOf<String>()
        val current = StringBuilder()
        var inAssertion = false
        var escaped = false
        for (c in path.trim()) {
            when {
                escaped -> escaped = false
                c == '^' -> escaped = true
                inAssertion -> if (c == ']') inAssertion = false
                c == '[' -> inAssertion = true
                c == '/' -> {
                    if (current.isNotEmpty()) steps += current.toString()
                    current.clear()
                }
            }
            current.append(c)
        }
        if (current.isNotEmpty()) steps += current.toString()
        return steps.filter { it.startsWith('/') }
    }

    private fun withoutAssertions(step: String): String = buildString {
        var depth = 0
        var escaped = false
        for (c in step) {
            when {
                escaped -> escaped = false
                c == '^' -> escaped = true
                c == '[' -> depth++
                c == ']' -> depth--
                depth == 0 -> append(c)
            }
        }
    }

    /** Calibre's highlight style: a builtin or custom colour, or a text decoration. */
    private fun style(style: JsonObject?): Pair<HighlightStyle, Int> {
        if (style == null) return HighlightStyle.Highlight to DEFAULT_HIGHLIGHT_ARGB
        val which = style.str("which").orEmpty()
        return if (style.str("kind") == "decoration") {
            val line = style.str("text-decoration-line").orEmpty()
            val kind = when {
                "wavy" in which || style.str("text-decoration-style") == "wavy" -> HighlightStyle.Squiggly
                "strike" in which || "line-through" in line -> HighlightStyle.Strikethrough
                else -> HighlightStyle.Underline
            }
            kind to (cssColor(style.str("text-decoration-color")) ?: DECORATION_RED)
        } else {
            HighlightStyle.Highlight to (cssColor(style.str("background-color")) ?: BUILTIN_COLORS[which] ?: DEFAULT_HIGHLIGHT_ARGB)
        }
    }

    private fun cssColor(value: String?): Int? = value?.takeIf { it.startsWith('#') }?.let(::parseArgbHex)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // Calibre's builtin highlight colours, mapped to the reader's palette where it has one.
    private val BUILTIN_COLORS = mapOf(
        "yellow" to DEFAULT_HIGHLIGHT_ARGB,
        "green" to 0xFFB8E3A8.toInt(),
        "blue" to 0xFFB4D4F5.toInt(),
        "red" to 0xFFF5C2D7.toInt(),
        "purple" to 0xFFD7C6F2.toInt(),
    )
    private const val DECORATION_RED = 0xFFD9534F.toInt()
}
