package com.itexpert120.yomu.data.annotations

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream

/**
 * The parts of a stored (Readium-shaped) locator that place it in the book: the resource [href],
 * the [progression] through that resource, whole-book [totalProgression], foliate's [cfi], and the
 * chapter [title] Readium writes. Parsed without Android so ordering and export stay unit-testable.
 */
internal data class LocatorPoint(
    val href: String?,
    val progression: Double?,
    val totalProgression: Double?,
    val cfi: String?,
    val title: String?,
) {
    companion object {
        val Unknown = LocatorPoint(null, null, null, null, null)

        fun parse(locatorJson: String?): LocatorPoint {
            val locator = parseJsonObject(locatorJson) ?: return Unknown
            val locations = locator["locations"] as? JsonObject
            return LocatorPoint(
                href = locator.string("href"),
                progression = locations?.number("progression"),
                totalProgression = locations?.number("totalProgression"),
                cfi = locations?.string("cfi"),
                title = locator.string("title"),
            )
        }
    }
}

internal val LenientJson = Json { ignoreUnknownKeys = true }

internal fun parseJsonObject(text: String?): JsonObject? = text?.let { runCatching { LenientJson.parseToJsonElement(it).jsonObject }.getOrNull() }

internal fun parseJsonElement(text: String): JsonElement? = runCatching { LenientJson.parseToJsonElement(text) }.getOrNull()

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull?.takeUnless { it.isNaN() }

/**
 * A resource href in one comparable form: no leading slash, fragment or query, percent-decoded —
 * Readium and foliate spell the same file differently (`/OEBPS/a%20b.xhtml` vs `OEBPS/a b.xhtml`).
 */
internal fun normalizedHref(href: String?): String? {
    val path = href?.substringBefore('#')?.substringBefore('?')?.trimStart('/') ?: return null
    return percentDecoded(path).takeIf { it.isNotEmpty() }
}

// Not URLDecoder: that is form decoding and turns '+' into a space.
private fun percentDecoded(text: String): String {
    if ('%' !in text) return text
    val out = ByteArrayOutputStream()
    var i = 0
    while (i < text.length) {
        val hex = if (text[i] == '%' && i + 2 <= text.lastIndex) text.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            out.write(hex)
            i += 3
        } else {
            val next = text.indexOf('%', i + 1).takeIf { it >= 0 } ?: text.length
            out.write(text.substring(i, next).toByteArray(Charsets.UTF_8))
            i = next
        }
    }
    return out.toString(Charsets.UTF_8.name())
}
