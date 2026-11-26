package com.itexpert120.yomu.data.reader

/**
 * Where each of [ids] starts in an (X)HTML document: the share of the document's visible text that
 * comes before the element carrying that `id` (or an `<a name>`), 0..1. Ids not found are absent.
 *
 * Contents entries that point at anchors inside one file (`poem.xhtml#part3`) carry no position of
 * their own; this is what tells the reader which of them the page is in. Text is counted, not
 * markup, so it tracks how far into the chapter a reader is about as pages and scroll do.
 */
internal fun anchorPositions(markup: String, ids: Set<String>): Map<String, Double> {
    if (ids.isEmpty()) return emptyMap()
    val found = HashMap<String, Int>()
    var text = 0
    var i = 0
    val n = markup.length
    while (i < n) {
        val c = markup[i]
        when {
            c == '<' -> {
                when {
                    markup.startsWith("<!--", i) -> i = markup.indexOf("-->", i + 4).let { if (it < 0) n else it + 3 }
                    markup.startsWith("<![CDATA[", i) -> {
                        val end = markup.indexOf("]]>", i + 9).let { if (it < 0) n else it }
                        text += countText(markup, i + 9, end)
                        i = (end + 3).coerceAtMost(n)
                    }
                    else -> {
                        val end = tagEnd(markup, i)
                        val tag = markup.substring(i + 1, end)
                        i = (end + 1).coerceAtMost(n)
                        val name = tagName(tag)
                        if (tag.startsWith("/") || tag.startsWith("!") || tag.startsWith("?")) continue
                        for (id in anchorIds(tag)) if (id in ids) found.putIfAbsent(id, text)
                        // Text that never shows on the page.
                        if (name in INVISIBLE && !tag.endsWith("/")) {
                            val close = markup.indexOf("</$name", i, ignoreCase = true)
                            i = if (close < 0) n else tagEnd(markup, close) + 1
                        }
                    }
                }
            }
            c == '&' -> {
                // An entity is one character on the page.
                val semi = markup.indexOf(';', i)
                i = if (semi in (i + 1)..(i + 10)) semi + 1 else i + 1
                text++
            }
            else -> {
                if (!c.isWhitespace()) text++
                i++
            }
        }
    }
    if (text == 0) return found.mapValues { 0.0 }
    return found.mapValues { (_, at) -> at.toDouble() / text }
}

private val INVISIBLE = setOf("head", "script", "style", "title")

private val ANCHOR_ATTRIBUTE = Regex("""(?:^|\s)(?:id|xml:id|name)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

private fun anchorIds(tag: String): List<String> = ANCHOR_ATTRIBUTE.findAll(tag)
    .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
    .filter { it.isNotEmpty() }
    .toList()

private fun tagName(tag: String): String = tag.trimStart('/').takeWhile { !it.isWhitespace() && it != '/' && it != '>' }.substringAfter(':').lowercase()

/** The `>` closing the tag that opens at [start], skipping `>` inside quoted attribute values. */
private fun tagEnd(markup: String, start: Int): Int {
    var quote: Char? = null
    var i = start + 1
    while (i < markup.length) {
        val c = markup[i]
        if (quote != null) {
            if (c == quote) quote = null
        } else if (c == '"' || c == '\'') {
            quote = c
        } else if (c == '>') {
            return i
        }
        i++
    }
    return markup.length
}

private fun countText(markup: String, from: Int, to: Int): Int {
    var count = 0
    for (i in from until to.coerceAtMost(markup.length)) if (!markup[i].isWhitespace()) count++
    return count
}
