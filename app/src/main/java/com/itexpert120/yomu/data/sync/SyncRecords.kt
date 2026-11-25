package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.DEFAULT_HIGHLIGHT_STYLE
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A record as derived from this device's current state; [kind] is the [RecordKind.key] and the
 * record's name is `<kind>/<entityId>`. Book-bound records carry the EPUB's [sha256] so books
 * match across devices. [timeHint] dates pre-sync data (see SyncEngine).
 */
internal data class LocalRecord(
    val kind: String,
    val entityId: String,
    val sha256: String?,
    val payload: String,
    val timeHint: Long,
) {
    val name: String get() = "$kind/$entityId"
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

/** One chapter's state. [progress] null = no progress row (then [updatedAt] is 0 and [manual] false). */
@Serializable
internal data class ChapterPayload(val read: Boolean, val progress: Float?, val updatedAt: Long, val manual: Boolean) {
    /** First-contact merge of two devices' pre-sync state: read on either side, furthest progress. */
    fun mergedWith(other: ChapterPayload): ChapterPayload {
        val furthest = listOf(this, other)
            .filter { it.progress != null }
            .maxWithOrNull(compareBy<ChapterPayload> { it.progress }.thenBy { it.updatedAt })
        return ChapterPayload(read || other.read, furthest?.progress, furthest?.updatedAt ?: 0L, furthest?.manual ?: false)
    }
}

/** Entity id of a chapter record: the book's hash, then the chapter id (which may contain anything). */
internal object ChapterRecordId {
    fun of(sha256: String, chapterId: String) = "$sha256|$chapterId"
    fun sha256(id: String) = id.substringBefore('|')
    fun chapterId(id: String) = id.substringAfter('|')
}

@Serializable
internal data class BookmarkPayload(
    val sha256: String,
    val locator: String,
    val href: String?,
    val chapterTitle: String?,
    val progression: Double,
    val createdAt: Long,
)

/**
 * [note], [style] and [modifiedAt] came later: they are left off the wire while at their defaults,
 * so an unedited highlight encodes exactly as older builds wrote it (and no upgrade re-pushes every
 * highlight), and older builds, which ignore unknown keys, still read a record that carries them.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class HighlightPayload(
    val sha256: String,
    val locator: String,
    val text: String,
    val color: Int,
    val createdAt: Long,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val note: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val style: String = DEFAULT_HIGHLIGHT_STYLE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val modifiedAt: Long = 0,
)

@Serializable
internal data class SessionPayload(val sha256: String, val startedAt: Long, val seconds: Long)

@Serializable
internal data class AppSettingsPayload(val theme: String, val colorStyle: String)

@Serializable
// `group` is no longer used (library grouping was replaced by tabs/collections). It stays on the
// wire, always "None", because older app versions require it to decode this record; the default
// lets a future version stop sending it.
internal data class LibraryPayload(val sort: String, val group: String = "None", val view: String, val coverCrop: Boolean)

@Serializable
internal data class FontPayload(val family: String)

@Serializable
internal data class CollectionPayload(val name: String, val position: Int)

/** [collections] is sorted so identical state always encodes identically. */
@Serializable
internal data class ShelfPayload(val collections: List<String>, val status: String?)

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
    // Decode-only: an enum value this build no longer has (e.g. a removed bundled font synced from
    // an older device) becomes the field's default rather than failing the record.
    coerceInputValues = true
}
