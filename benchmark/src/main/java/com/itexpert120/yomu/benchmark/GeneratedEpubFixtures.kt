package com.itexpert120.yomu.benchmark

import android.content.Context
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates deterministic EPUBs for launch benchmarks instead of checking binary books into git.
 * The stress fixture intentionally has 1,500 spine/TOC entries to exercise cache validation and
 * the old quadratic TOC/ZIP path.
 */
object GeneratedEpubFixtures {
    fun generateAll(context: Context): Map<String, File> = mapOf(
        "normal" to generate(context, "normal", chapterCount = 24),
        "stress-1500" to generate(context, "stress-1500", chapterCount = 1_500),
    )

    private fun generate(context: Context, name: String, chapterCount: Int): File {
        val root = File(context.cacheDir, "reader-benchmark-fixtures").apply { mkdirs() }
        val output = File(root, "$name.epub")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mime.size.toLong()
                    crc = CRC32().apply { update(mime) }.value
                },
            )
            zip.write(mime)
            zip.closeEntry()
            add(zip, "META-INF/container.xml", CONTAINER)
            add(zip, "OEBPS/nav.xhtml", nav(chapterCount))
            add(zip, "OEBPS/content.opf", opf(chapterCount))
            repeat(chapterCount) { index ->
                val number = index + 1
                add(
                    zip,
                    "OEBPS/chapter$number.xhtml",
                    """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter $number</title></head><body><h1>Chapter $number</h1><p>Benchmark text for chapter $number.</p></body></html>""",
                )
            }
        }
        return output
    }

    private fun add(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }

    private fun nav(chapterCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Benchmark</title></head><body><nav epub:type=\"toc\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><ol>")
        repeat(chapterCount) { index ->
            val number = index + 1
            append("<li><a href=\"chapter$number.xhtml\">Chapter $number</a></li>")
        }
        append("</ol></nav></body></html>")
    }

    private fun opf(chapterCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">yomu-benchmark-$chapterCount</dc:identifier><dc:title>Benchmark $chapterCount</dc:title><dc:language>en</dc:language></metadata><manifest><item id=\"nav\" properties=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\"/>")
        repeat(chapterCount) { index ->
            val number = index + 1
            append("<item id=\"chapter$number\" href=\"chapter$number.xhtml\" media-type=\"application/xhtml+xml\"/>")
        }
        append("</manifest><spine>")
        repeat(chapterCount) { index -> append("<itemref idref=\"chapter${index + 1}\"/>") }
        append("</spine></package>")
    }

    private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
}
