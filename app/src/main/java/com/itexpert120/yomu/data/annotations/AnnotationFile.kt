package com.itexpert120.yomu.data.annotations

import com.itexpert120.yomu.core.reader.HighlightStyle
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderHighlight
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Open Reader's annotation export, version [VERSION]: one book's highlights (with notes) and
 * bookmarks. [book].sha256 is the EPUB's content hash, the identity sync matches books by. Dates
 * are ISO-8601 instants; colours `#AARRGGBB`; [AnnotationFileHighlight.style] a `HighlightStyle.key`;
 * locators are the reader's own locator objects (Readium-shaped: href, locations, text).
 *
 * Compatibility: readers ignore unknown keys, so later versions may add fields freely; a change
 * that older readers would misread must bump [VERSION], and files with a newer version are refused.
 */
@Serializable
data class AnnotationFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String? = null,
    val book: AnnotationFileBook = AnnotationFileBook(),
    val highlights: List<AnnotationFileHighlight> = emptyList(),
    val bookmarks: List<AnnotationFileBookmark> = emptyList(),
) {
    companion object {
        const val FORMAT = "open-reader-annotations"
        const val VERSION = 1
        const val MIME_TYPE = "application/json"
    }
}

@Serializable
data class AnnotationFileBook(val sha256: String? = null, val title: String? = null, val author: String? = null)

@Serializable
data class AnnotationFileHighlight(
    val id: String? = null,
    val chapter: String? = null,
    val text: String,
    val note: String? = null,
    val style: String = HighlightStyle.Highlight.key,
    val color: String? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val locator: JsonElement,
)

/** [chapterId] is the bookmark's logical chapter anchor (a TOC id, or a resource href on older ones). */
@Serializable
data class AnnotationFileBookmark(
    val id: String? = null,
    val chapter: String? = null,
    val chapterId: String? = null,
    val progression: Double? = null,
    val createdAt: String? = null,
    val locator: JsonElement,
)

/** Reading an annotation file: its contents, or why it can't be imported. */
sealed interface AnnotationFileParse {
    data class Parsed(val file: AnnotationFile) : AnnotationFileParse

    /** Not JSON, or not an Open Reader annotation file. */
    data object NotAnnotationFile : AnnotationFileParse

    /** Written by a newer Open Reader in a format this build can't read. */
    data class NewerVersion(val version: Int) : AnnotationFileParse
}

object AnnotationFileCodec {
    private val writer = Json {
        prettyPrint = true
        encodeDefaults = true
        // Absent = null: unknown values are simply left out.
        explicitNulls = false
    }
    private val reader = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun encode(file: AnnotationFile): String = writer.encodeToString(AnnotationFile.serializer(), file) + "\n"

    fun parse(text: String): AnnotationFileParse {
        val root = runCatching { reader.parseToJsonElement(text).jsonObject }.getOrNull() ?: return AnnotationFileParse.NotAnnotationFile
        if ((root["format"] as? JsonPrimitive)?.content != AnnotationFile.FORMAT) return AnnotationFileParse.NotAnnotationFile
        val version = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return AnnotationFileParse.NotAnnotationFile
        if (version > AnnotationFile.VERSION) return AnnotationFileParse.NewerVersion(version)
        val file = runCatching { reader.decodeFromJsonElement(AnnotationFile.serializer(), root) }.getOrNull()
            ?: return AnnotationFileParse.NotAnnotationFile
        return AnnotationFileParse.Parsed(file)
    }

    /** The file for [document]: its annotations in reading order, each labelled with its chapter. */
    fun fileOf(document: AnnotationDocument): AnnotationFile {
        val highlights = mutableListOf<AnnotationFileHighlight>()
        val bookmarks = mutableListOf<AnnotationFileBookmark>()
        for (chapter in document.chapters) {
            for (item in chapter.items) {
                when (item) {
                    is AnnotationItem.Highlight -> highlights += item.highlight.toFile(chapter.title)
                    is AnnotationItem.Bookmark -> bookmarks += item.bookmark.toFile(chapter.title)
                }
            }
        }
        return AnnotationFile(
            exportedAt = isoInstant(document.exportedAt),
            book = AnnotationFileBook(document.sha256, document.bookTitle, document.author),
            highlights = highlights,
            bookmarks = bookmarks,
        )
    }

    private fun ReaderHighlight.toFile(chapter: String?) = AnnotationFileHighlight(
        id = id,
        chapter = chapter,
        text = text,
        note = note,
        style = style.key,
        color = argbHex(colorArgb),
        createdAt = isoInstant(createdAt),
        modifiedAt = modifiedAt.takeIf { it > 0 }?.let(::isoInstant),
        locator = locatorElement(locatorJson),
    )

    private fun ReaderBookmark.toFile(chapter: String?) = AnnotationFileBookmark(
        id = id,
        chapter = chapter ?: chapterTitle,
        chapterId = href,
        progression = progression,
        createdAt = isoInstant(createdAt),
        locator = locatorElement(locatorJson),
    )

    // A locator that isn't a JSON object (never written by the app) still survives, as a string.
    private fun locatorElement(locatorJson: String): JsonElement = parseJsonElement(locatorJson)?.takeIf { it is JsonObject } ?: JsonPrimitive(locatorJson)
}

/** A file locator back in the app's stored form. */
internal fun JsonElement.toLocatorJson(): String = if (this is JsonPrimitive && isString) content else toString()

internal fun isoInstant(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

internal fun parseInstant(text: String?): Long? = text?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

internal fun argbHex(argb: Int): String = "#%08X".format(argb)

internal fun rgbHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** `#RRGGBB` (opaque) or `#AARRGGBB`; null when malformed. */
internal fun parseArgbHex(text: String?): Int? {
    val hex = text?.trim()?.removePrefix("#") ?: return null
    val value = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> (0xFF000000 or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}
