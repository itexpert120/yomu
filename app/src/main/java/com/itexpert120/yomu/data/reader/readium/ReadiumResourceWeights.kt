package com.itexpert120.yomu.data.reader.readium

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.ceil

private const val POSITION_CHUNK_BYTES = 1024.0

/** Stable cache key shared by import-time and open-time resource metadata. */
internal fun Link.normalizedResourceCacheKey(): String = url().normalize().removeFragment().removeQuery().toString()

/**
 * Computes the position weight for each reading-order resource from the EPUB central directory.
 * This is intentionally metadata-only: it does not read resource bodies.
 */
internal suspend fun resourceWeightMap(
    filePath: String,
    readingOrder: List<Link>,
): Map<String, Int> = withContext(Dispatchers.IO) {
    runCatching {
        ZipFile(File(filePath)).use { archive ->
            readingOrder.associate { link ->
                val entryName = link.url().toString()
                    .substringBefore('#')
                    .substringBefore('?')
                    .removePrefix("/")
                val length = (
                    archive.getEntry(entryName)
                        ?: archive.getEntry(java.net.URLDecoder.decode(entryName, Charsets.UTF_8.name()))
                    )?.size ?: 0L
                link.normalizedResourceCacheKey() to ceil(
                    length.coerceAtLeast(1L).toDouble() / POSITION_CHUNK_BYTES,
                ).toInt().coerceAtLeast(1)
            }
        }
    }.getOrElse {
        readingOrder.associate { it.normalizedResourceCacheKey() to 1 }
    }
}
