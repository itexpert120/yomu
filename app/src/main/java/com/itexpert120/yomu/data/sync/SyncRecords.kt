package com.itexpert120.yomu.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Record kinds. A logical record name is `<kind>/<id>`; book-bound kinds use the EPUB's SHA-256 (or
 * the item's UUID with the hash inside the payload) so books match across devices.
 */
internal enum class SyncKind(val key: String, val bookBound: Boolean, val neverDeleted: Boolean = false) {
    // Never deleted: an unedited or unopened book simply has no record to send.
    Meta("meta", bookBound = true, neverDeleted = true),
    Progress("progress", bookBound = true, neverDeleted = true),
    Chapters("chapters", bookBound = true),
    Bookmark("bookmark", bookBound = true),
    Highlight("highlight", bookBound = true),
    BookSettings("bookSettings", bookBound = true),

    // Append-only: a session pruned or removed locally still counts everywhere else.
    Session("session", bookBound = true, neverDeleted = true),
    Settings("settings", bookBound = false),
    Theme("theme", bookBound = false),
    Font("font", bookBound = false),
    ;

    fun name(id: String) = "$key/$id"

    companion object {
        fun of(name: String): SyncKind? = name.substringBefore('/').let { k -> entries.firstOrNull { it.key == k } }
    }
}

/** A record as derived from this device's current state. [timeHint] dates pre-sync data (see SyncEngine). */
internal data class LocalRecord(
    val kind: SyncKind,
    val entityId: String,
    val sha256: String?,
    val payload: String,
    val timeHint: Long,
) {
    val name: String get() = kind.name(entityId)
}

/** Plaintext inside each encrypted blob. [payload] null = deleted. [device] labels progress offers. */
@Serializable
internal data class SyncEnvelope(val name: String, val payload: String? = null, val device: String? = null)

@Serializable
internal data class MetaPayload(val title: String, val subtitle: String?, val author: String, val description: String?)

@Serializable
internal data class ProgressPayload(
    // Portable locator when known (see ReaderSession.portableLocator), else the engine locator.
    val locator: String?,
    val progress: Float,
    val totalProgression: Double?,
    val chapterId: String?,
    val lastOpenedAt: Long,
    val startedAt: Long,
    val finishedAt: Long,
)

@Serializable
internal data class ChapterState(val id: String, val progress: Float, val updatedAt: Long, val manual: Boolean)

@Serializable
internal data class ChaptersPayload(val read: List<String>, val progress: List<ChapterState>)

@Serializable
internal data class BookmarkPayload(
    val sha256: String,
    val locator: String,
    val href: String?,
    val chapterTitle: String?,
    val progression: Double,
    val createdAt: Long,
)

@Serializable
internal data class HighlightPayload(val sha256: String, val locator: String, val text: String, val color: Int, val createdAt: Long)

@Serializable
internal data class SessionPayload(val sha256: String, val startedAt: Long, val seconds: Long)

@Serializable
internal data class AppSettingsPayload(val theme: String, val colorStyle: String)

@Serializable
internal data class LibraryPayload(val sort: String, val group: String, val view: String, val coverCrop: Boolean)

@Serializable
internal data class FontPayload(val family: String)

/** Ids for the singleton settings records. */
internal object SettingsId {
    const val APP = "app"
    const val LIBRARY = "library"
    const val READER_DEFAULTS = "readerDefaults"
}

/** Deterministic encoding: identical state must produce identical payload strings on every device. */
internal val SyncJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = true
}
