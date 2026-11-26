package com.itexpert120.yomu.data.reader

import java.io.File
import java.util.zip.ZipFile

/**
 * A book's own title and authors as its package document writes them: the main `dc:title` (the one
 * refined as `title-type main`, else the first) and the `dc:creator`s in order, each once.
 *
 * Readium keys a title's translations by language, so its alternate scripts (`Kusamakura` beside
 * 草枕, or an English rendering under the same `en` key as the original) can stand in for the title
 * on a phone set to English; this reads what the publisher named the book.
 */
internal data class OpfNames(val title: String?, val creators: List<String>)

internal fun readOpfNames(epub: File): OpfNames? = runCatching {
    ZipFile(epub).use { zip ->
        val container = zip.getEntry("META-INF/container.xml")?.let { zip.getInputStream(it).use { s -> s.readBytes().decodeToString() } }
            ?: return null
        val opfPath = Regex("""full-path\s*=\s*["']([^"']+)["']""").find(container)?.groupValues?.get(1) ?: return null
        val opf = zip.getEntry(opfPath)?.let { zip.getInputStream(it).use { s -> s.readBytes().decodeToString() } } ?: return null
        opfNames(opf)
    }
}.getOrNull()

internal fun opfNames(opf: String): OpfNames {
    val metadata = Regex("""<(?:\w+:)?metadata\b.*?</(?:\w+:)?metadata>""", RegexOption.DOT_MATCHES_ALL).find(opf)?.value ?: opf
    val mainIds = Regex("""<(?:\w+:)?meta\b([^>]*)>\s*main\s*</""").findAll(metadata)
        .filter { Regex("""property\s*=\s*["']title-type["']""").containsMatchIn(it.groupValues[1]) }
        .mapNotNull { Regex("""refines\s*=\s*["']#([^"']+)["']""").find(it.groupValues[1])?.groupValues?.get(1) }
        .toSet()
    val titles = elements(metadata, "title")
    val title = (titles.firstOrNull { it.id != null && it.id in mainIds } ?: titles.firstOrNull())?.text
    val creators = elements(metadata, "creator").map { it.text }.distinct()
    return OpfNames(title, creators)
}

private data class Element(val id: String?, val text: String)

/** The `dc:<name>` elements with text, in order (Dublin Core under any prefix). */
private fun elements(metadata: String, name: String): List<Element> = Regex("""<(\w+:)?$name\b([^>]*)>(.*?)</\1?$name\s*>""", RegexOption.DOT_MATCHES_ALL).findAll(metadata)
    .mapNotNull { match ->
        val text = xmlText(match.groupValues[3])
        if (text.isEmpty()) return@mapNotNull null
        val id = Regex("""(?:^|\s)id\s*=\s*["']([^"']+)["']""").find(match.groupValues[2])?.groupValues?.get(1)
        Element(id, text)
    }
    .toList()

private fun xmlText(raw: String): String = raw
    .replace(Regex("""<!\[CDATA\[(.*?)]]>""", RegexOption.DOT_MATCHES_ALL), "$1")
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("""&#x([0-9a-fA-F]+);""")) { it.groupValues[1].toInt(16).toChar().toString() }
    .replace(Regex("""&#([0-9]+);""")) { it.groupValues[1].toInt().toChar().toString() }
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    .replace(Regex("""\s+"""), " ")
    .trim()
