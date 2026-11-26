package com.itexpert120.yomu.reader.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/** One file of the reading order (a chapter, usually), addressed by its path inside the zip. */
data class SpineItem(val index: Int, val path: String, val mediaType: String)

/**
 * An opened EPUB: its title and reading order. Only the container and the package document are read
 * up front; chapter files are read on demand with [readText].
 */
class EpubBook private constructor(
    private val zip: ZipFile,
    val title: String?,
    val spine: List<SpineItem>,
) : Closeable {

    /** The raw text of a file inside the book, e.g. a chapter's XHTML. */
    fun readText(path: String): String {
        val entry = zip.getEntry(path) ?: throw EpubException("Missing file in book: $path")
        return zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override fun close() = zip.close()

    companion object {
        fun open(file: File): EpubBook {
            val zip = ZipFile(file)
            try {
                val opfPath = rootfilePath(zip)
                val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
                val pkg = parsePackage(zip, opfPath)
                val spine = pkg.spineIds.mapNotNull { id -> pkg.manifest[id] }
                    .mapIndexed { index, item -> SpineItem(index, resolve(opfDir, item.href), item.mediaType) }
                if (spine.isEmpty()) throw EpubException("Book has no reading order")
                return EpubBook(zip, pkg.title, spine)
            } catch (e: Exception) {
                zip.close()
                throw if (e is EpubException) e else EpubException("Couldn't read book", e)
            }
        }

        private fun rootfilePath(zip: ZipFile): String {
            val entry = zip.getEntry("META-INF/container.xml") ?: throw EpubException("Not an EPUB: no container")
            val parser = newParser(zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) })
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name.localName() == "rootfile") {
                    parser.getAttributeValue(null, "full-path")?.let { return it }
                }
            }
            throw EpubException("Not an EPUB: no package document")
        }

        private class ManifestItem(val href: String, val mediaType: String)

        private class Package(val title: String?, val manifest: Map<String, ManifestItem>, val spineIds: List<String>)

        private fun parsePackage(zip: ZipFile, opfPath: String): Package {
            val entry = zip.getEntry(opfPath) ?: throw EpubException("Missing package document")
            val parser = newParser(zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) })
            var title: String? = null
            val manifest = HashMap<String, ManifestItem>()
            val spine = ArrayList<String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name.localName()) {
                    "title" -> if (title == null) title = parser.nextText().trim().ifEmpty { null }
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) {
                            manifest[id] = ManifestItem(href, parser.getAttributeValue(null, "media-type").orEmpty())
                        }
                    }
                    "itemref" -> parser.getAttributeValue(null, "idref")?.let(spine::add)
                }
            }
            return Package(title, manifest, spine)
        }

        /** Resolves an href relative to the package document's folder into a zip path. */
        internal fun resolve(baseDir: String, href: String): String {
            // URLDecoder is form decoding; keep a literal '+' as itself.
            val decoded = URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
            val parts = ArrayList<String>()
            if (baseDir.isNotEmpty()) parts.addAll(baseDir.split('/'))
            for (segment in decoded.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(segment)
                }
            }
            return parts.joinToString("/")
        }

        internal fun newParser(text: String): XmlPullParser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // A UTF-8 byte-order mark (or stray whitespace) before "<?xml" is common in the wild —
            // e.g. Calibre-made books' container.xml — and the pull parser rejects the whole file.
            setInput(withoutLeadingJunk(text).reader())
        }

        internal fun withoutLeadingJunk(text: String): String = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')

        private fun String.localName() = substringAfter(':')
    }
}

class EpubException(message: String, cause: Throwable? = null) : Exception(message, cause)
