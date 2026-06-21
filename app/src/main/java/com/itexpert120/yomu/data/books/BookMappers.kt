package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.database.BookEntity
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import org.json.JSONObject
import kotlin.math.absoluteValue

internal fun BookEntity.toBook(): Book {
    val locator = locatorJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    val locations = locator?.optJSONObject("locations")
    return Book(
        id = BookId(id),
        title = title,
        author = author,
        subtitle = subtitle,
        description = description,
        language = language,
        publisher = publisher,
        series = series,
        coverImagePath = coverImagePath,
        coverPalette = paletteFor(id),
        progress = progress,
        remainingLabel = remainingLabelFor(progress),
        currentHref = locator?.optString("href")?.takeIf { it.isNotEmpty() },
        currentChapterProgress = locations?.let {
            if (it.has("progression")) it.optDouble("progression").toFloat() else null
        },
        addedAt = addedAt,
        lastOpenedAt = lastOpenedAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
    )
}

internal fun ImportedBook.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    author = author,
    description = description,
    language = language,
    publisher = publisher,
    series = series,
    coverImagePath = coverImagePath,
    storagePath = storagePath,
    originalUri = originalUri,
    originalDisplayName = originalDisplayName,
    sha256 = sha256,
    fileSizeBytes = fileSizeBytes,
    progress = 0f,
    totalProgression = null,
    locatorJson = null,
    addedAt = addedAt,
    lastOpenedAt = 0L,
    startedAt = 0L,
    finishedAt = 0L,
)

private fun remainingLabelFor(progress: Float): String = when {
    progress >= 1f -> "Finished"
    progress > 0f -> "${(progress * 100).toInt()}% read"
    else -> "Unread"
}

/** Deterministic gradient used as the cover fallback when no extracted cover exists. */
private fun paletteFor(seed: String): List<Long> {
    val pair = FallbackPalettes[seed.hashCode().absoluteValue % FallbackPalettes.size]
    return pair.toList()
}

private val FallbackPalettes: List<Pair<Long, Long>> = listOf(
    0xFF17202B to 0xFF53697E,
    0xFF263A30 to 0xFF587A5F,
    0xFF35211E to 0xFF9B5948,
    0xFF352D46 to 0xFF8D7BA8,
    0xFF2F2621 to 0xFFA76F55,
    0xFF24352B to 0xFF89A179,
    0xFF2A1F1A to 0xFFB08968,
    0xFF1A1A2E to 0xFF6C63FF,
    0xFF2C1810 to 0xFFC17817,
    0xFF202830 to 0xFF6A7D8E,
)
