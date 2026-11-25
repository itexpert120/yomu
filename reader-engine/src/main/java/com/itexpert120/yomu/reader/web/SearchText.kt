package com.itexpert120.yomu.reader.web

import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipFile

/**
 * The text matching behind in-book search: case and diacritics are ignored and any run of
 * whitespace counts as one space. The page's matcher (`fold` in yomu-reader.js) folds text the same
 * way, so a chapter that [chapterMayContain] rules out can't hold a match the page would have found.
 *
 * Opening every chapter as a document in the page is what makes searching a long book slow, so the
 * app first scans the raw chapter files here and the page only searches the chapters that matched.
 */
object SearchText {

    /** [text] folded for matching: no case, no diacritics or format characters, single spaces. */
    fun fold(text: CharSequence): String = StringBuilder(text.length).also { Folder(it).append(text) }.toString()

    /**
     * Whether chapter [path] of [zip] may contain [needle] (already [fold]ed and trimmed). True for
     * every chapter holding a match: markup is skipped crudely (tags, comments, scripts, styles),
     * which can only add chapters without one. [scratch] is reused between calls.
     */
    fun chapterMayContain(zip: ZipFile, path: String, needle: String, scratch: StringBuilder = StringBuilder()): Boolean {
        val entry = zip.getEntry(path) ?: return false
        val markup = zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8)
        scratch.setLength(0)
        foldMarkup(markup, scratch)
        return scratch.indexOf(needle) >= 0
    }

    /** Folds the text of [markup] (an XHTML document) into [out]. */
    internal fun foldMarkup(markup: String, out: StringBuilder) {
        val folder = Folder(out)
        var i = 0
        val n = markup.length
        while (i < n) {
            val c = markup[i]
            when {
                c == '<' -> i = skipMarkup(markup, i)
                c == '&' -> {
                    val end = entityEnd(markup, i)
                    val decoded = end?.let { entity(markup.substring(i + 1, it)) }
                    if (decoded != null && end != null) {
                        folder.append(decoded)
                        i = end + 1
                    } else {
                        folder.append(c)
                        i++
                    }
                }
                else -> {
                    val next = nextMarkup(markup, i)
                    folder.append(markup, i, next)
                    i = next
                }
            }
        }
    }

    // The ';' closing an entity that starts at [amp], if it's near enough to be one.
    private fun entityEnd(s: String, amp: Int): Int? {
        for (i in amp + 2..minOf(amp + 12, s.length - 1)) {
            if (s[i] == ';') return i
        }
        return null
    }

    private fun nextMarkup(s: String, from: Int): Int {
        var i = from
        while (i < s.length && s[i] != '<' && s[i] != '&') i++
        return i
    }

    // Index just past the tag, comment, or script/style element starting at [start].
    private fun skipMarkup(s: String, start: Int): Int {
        if (s.startsWith("<!--", start)) return s.indexOf("-->", start + 4).let { if (it < 0) s.length else it + 3 }
        for (element in RAW_TEXT_ELEMENTS) {
            if (s.regionMatches(start + 1, element, 0, element.length, ignoreCase = true)) {
                val after = s.getOrNull(start + 1 + element.length)
                if (after == null || after == '>' || after == '/' || after.isWhitespace()) {
                    val close = s.indexOf("</$element", start, ignoreCase = true)
                    if (close >= 0) return s.indexOf('>', close).let { if (it < 0) s.length else it + 1 }
                }
            }
        }
        return s.indexOf('>', start).let { if (it < 0) s.length else it + 1 }
    }

    private fun entity(name: String): String? = when {
        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePoint)
        name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
        else -> NAMED_ENTITIES[name]
    }

    private fun codePoint(value: Int): String? = if (Character.isValidCodePoint(value)) String(Character.toChars(value)) else null

    /** Appends folded text, collapsing whitespace runs across calls. */
    private class Folder(private val out: StringBuilder) {
        private var space = false

        fun append(text: CharSequence, start: Int = 0, end: Int = text.length) {
            var i = start
            while (i < end) {
                val c = text[i]
                if (c.code < 128) {
                    if (c == ' ' || c in '\t'..'\r') {
                        space()
                    } else {
                        out.append(if (c in 'A'..'Z') c + 32 else c)
                        space = false
                    }
                    i++
                    continue
                }
                val code = Character.codePointAt(text, i)
                val length = Character.charCount(code)
                if (isSpace(code)) {
                    space()
                } else {
                    val folded = foldCodePoint(code)
                    if (folded.isNotEmpty()) {
                        out.append(folded)
                        space = false
                    }
                }
                i += length
            }
        }

        fun append(c: Char) = append(c.toString())

        private fun space() {
            if (!space) out.append(' ')
            space = true
        }
    }

    // What JavaScript's /\s/ matches beyond ASCII.
    private fun isSpace(code: Int): Boolean = code == 0xA0 ||
        code == 0x1680 ||
        code in 0x2000..0x200A ||
        code == 0x2028 ||
        code == 0x2029 ||
        code == 0x202F ||
        code == 0x205F ||
        code == 0x3000 ||
        code == 0xFEFF

    // One code point as the page folds it: NFD, marks and format characters dropped, lower case.
    private fun foldCodePoint(code: Int): String {
        val decomposed = Normalizer.normalize(String(Character.toChars(code)), Normalizer.Form.NFD)
        val kept = StringBuilder(decomposed.length)
        var i = 0
        while (i < decomposed.length) {
            val cp = decomposed.codePointAt(i)
            when (Character.getType(cp).toByte()) {
                Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK, Character.FORMAT -> Unit
                else -> kept.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return kept.toString().lowercase(Locale.ROOT)
    }

    private val RAW_TEXT_ELEMENTS = listOf("script", "style")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "shy" to "­",
        "mdash" to "—",
        "ndash" to "–",
        "hellip" to "…",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
    )
}
