package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.BookEntity
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.ChapterProgressEntity
import com.itexpert120.yomu.core.database.CollectionDao
import com.itexpert120.yomu.core.database.HighlightEntity
import com.itexpert120.yomu.core.database.SyncDao
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import com.itexpert120.yomu.data.stats.StatsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** A book's edited title, subtitle, author and description. Only user edits sync. */
@Singleton
internal class MetaKind @Inject constructor(private val dao: SyncDao) : RecordKind<MetaPayload> {
    override val key = "meta"
    override val bookBound = true

    // An unedited book simply has no record to send.
    override val neverDeleted = true
    override val watchedTables = setOf("books")
    override val payload = MetaPayload.serializer()

    // A fresh import's extracted metadata never overwrites an edit made elsewhere.
    override suspend fun snapshot() = dao.books().filter { it.metadataEditedAt > 0L }.map { book ->
        record(book.sha256, book.sha256, MetaPayload(book.title, book.subtitle, book.author, book.description), book.metadataEditedAt)
    }

    override suspend fun apply(id: String, payload: MetaPayload?, ctx: ApplyContext): Applied {
        val meta = payload ?: return Applied(null)
        dao.applyMetadata(id, meta.title, meta.subtitle, meta.author, meta.description, editedAt = maxOf(ctx.hlc.physical, 1))
        return stored(meta)
    }
}

/** A newer reading position from another device, offered instead of silently moving this one. */
@Serializable
data class ProgressOffer(
    val sha256: String,
    val locatorJson: String,
    val progress: Float,
    val device: String?,
    val hlc: String,
)

/** A book's reading position and read/unread state. */
@Singleton
internal class ProgressKind @Inject constructor(private val dao: SyncDao, private val store: SyncStore) : RecordKind<ProgressPayload> {
    override val key = "progress"
    override val bookBound = true

    // An unopened book has no record to send.
    override val neverDeleted = true
    override val carriesDeviceLabel = true
    override val watchedTables = setOf("books")
    override val payload = ProgressPayload.serializer()

    override suspend fun snapshot() = dao.books().mapNotNull(::progress)

    override suspend fun apply(id: String, payload: ProgressPayload?, ctx: ApplyContext): Applied {
        val remote = payload ?: return Applied(null)
        val local = dao.bookByHash(id)?.let(::progress)
        val localPayload = local?.let { SyncJson.decodeFromString(this.payload, it.payload) }
        // Adopt silently when there is nothing local to lose, when it's the same place, or when
        // the other device marked the book read/unread (a state change, not a reading position).
        if (localPayload == null || remote.locator == null || remote.locator == localPayload.locator) {
            dao.applyProgress(
                id,
                remote.progress,
                remote.totalProgression,
                remote.locator,
                remote.chapterId,
                remote.lastOpenedAt,
                remote.startedAt,
                remote.finishedAt,
            )
            store.deleteMetadata(OFFER_PREFIX + id)
            return stored(remote)
        }
        val offer = ProgressOffer(id, remote.locator, remote.progress, ctx.device, ctx.hlc.encode())
        store.putMetadata(OFFER_PREFIX + id, SyncJson.encodeToString(ProgressOffer.serializer(), offer))
        // The ledger keeps this device's position; reading on here will then be the newest change.
        return Applied(local.payload)
    }

    // A never-opened book has no position worth sharing, and must not overwrite one from elsewhere.
    private fun progress(book: BookEntity): LocalRecord? {
        if (book.lastOpenedAt <= 0L && book.progress <= 0f && book.finishedAt <= 0L) return null
        val payload = ProgressPayload(
            locator = book.syncLocatorJson ?: book.locatorJson,
            progress = book.progress,
            totalProgression = book.totalProgression,
            chapterId = book.currentChapterId,
            lastOpenedAt = book.lastOpenedAt,
            startedAt = book.startedAt,
            finishedAt = book.finishedAt,
        )
        return record(book.sha256, book.sha256, payload, maxOf(book.lastOpenedAt, book.finishedAt))
    }

    companion object {
        const val OFFER_PREFIX = "offer/"
    }
}

/**
 * One chapter's read state and progress, one record per chapter (`<sha>|<chapterId>`) so devices
 * changing different chapters of the same book never overwrite each other.
 */
@Singleton
internal class ChapterKind @Inject constructor(private val dao: SyncDao, private val store: SyncStore) : RecordKind<ChapterPayload> {
    override val key = "chapter"
    override val bookBound = true
    override val watchedTables = setOf("chapter_reads", "chapter_progress")
    override val payload = ChapterPayload.serializer()

    override fun bookSha(id: String, payload: ChapterPayload?) = ChapterRecordId.sha256(id)

    override suspend fun snapshot(): List<LocalRecord> {
        val shaById = store.localBooks()
        val read = dao.chapterReads().mapTo(HashSet()) { it.bookId to it.chapterId }
        val progress = dao.chapterProgress().associateBy { it.bookId to it.chapterId }
        return (read + progress.keys).mapNotNull { key ->
            val sha = shaById[key.first] ?: return@mapNotNull null
            val row = progress[key]
            val payload = ChapterPayload(key in read, row?.progress, row?.updatedAt ?: 0L, row?.manuallyRead ?: false)
            record(ChapterRecordId.of(sha, key.second), sha, payload, maxOf(payload.updatedAt, 1))
        }
    }

    override suspend fun apply(id: String, payload: ChapterPayload?, ctx: ApplyContext): Applied {
        val bookId = ctx.bookId ?: return stored(payload)
        val chapterId = ChapterRecordId.chapterId(id)
        if (payload == null) {
            dao.applyChapter(bookId, chapterId, read = false, progress = null)
            return Applied(null)
        }
        val local = if (ctx.firstContact) chapterFor(bookId, chapterId) else null
        val result = local?.mergedWith(payload) ?: payload
        dao.applyChapter(
            bookId,
            chapterId,
            read = result.read,
            progress = result.progress?.let { ChapterProgressEntity(bookId, chapterId, it, result.updatedAt, result.manual) },
        )
        val merged = encode(result)
        return Applied(merged, dirty = merged != encode(payload))
    }

    /** One chapter's current state on this device, or null if it has none. */
    private suspend fun chapterFor(bookId: String, chapterId: String): ChapterPayload? {
        val read = dao.isChapterRead(bookId, chapterId)
        val row = dao.chapterProgressFor(bookId, chapterId)
        if (!read && row == null) return null
        return ChapterPayload(read, row?.progress, row?.updatedAt ?: 0L, row?.manuallyRead ?: false)
    }
}

@Singleton
internal class BookmarkKind @Inject constructor(private val dao: SyncDao, private val store: SyncStore) : RecordKind<BookmarkPayload> {
    override val key = "bookmark"
    override val bookBound = true
    override val watchedTables = setOf("bookmarks")
    override val payload = BookmarkPayload.serializer()

    override fun bookSha(id: String, payload: BookmarkPayload?) = payload?.sha256

    override suspend fun snapshot(): List<LocalRecord> {
        val shaById = store.localBooks()
        return dao.bookmarks().mapNotNull { b ->
            val sha = shaById[b.bookId] ?: return@mapNotNull null
            record(b.id, sha, BookmarkPayload(sha, b.locatorJson, b.href, b.chapterTitle, b.progression, b.createdAt), b.createdAt)
        }
    }

    override suspend fun apply(id: String, payload: BookmarkPayload?, ctx: ApplyContext): Applied {
        if (payload == null) {
            dao.deleteBookmark(id)
        } else {
            val b = payload
            dao.upsertBookmark(BookmarkEntity(id, checkNotNull(ctx.bookId), b.locator, b.href, b.chapterTitle, b.progression, b.createdAt))
        }
        return stored(payload)
    }
}

@Singleton
internal class HighlightKind @Inject constructor(private val dao: SyncDao, private val store: SyncStore) : RecordKind<HighlightPayload> {
    override val key = "highlight"
    override val bookBound = true
    override val watchedTables = setOf("highlights")
    override val payload = HighlightPayload.serializer()

    override fun bookSha(id: String, payload: HighlightPayload?) = payload?.sha256

    override suspend fun snapshot(): List<LocalRecord> {
        val shaById = store.localBooks()
        return dao.highlights().mapNotNull { h ->
            val sha = shaById[h.bookId] ?: return@mapNotNull null
            record(h.id, sha, HighlightPayload(sha, h.locatorJson, h.text, h.colorArgb, h.createdAt), h.createdAt)
        }
    }

    override suspend fun apply(id: String, payload: HighlightPayload?, ctx: ApplyContext): Applied {
        if (payload == null) {
            dao.deleteHighlight(id)
        } else {
            val h = payload
            dao.upsertHighlight(HighlightEntity(id, checkNotNull(ctx.bookId), h.locator, h.text, h.color, h.createdAt))
        }
        return stored(payload)
    }
}

/** A book's per-book reader settings override, in synced form (see [ReaderSettingsSync]). */
@Singleton
internal class BookSettingsKind @Inject constructor(
    private val dao: SyncDao,
    private val store: SyncStore,
    private val readerSettings: ReaderSettingsRepository,
    private val fonts: SyncFonts,
) : RecordKind<JsonObject> {
    override val key = "bookSettings"
    override val bookBound = true
    override val watchedTables = setOf("reader_settings")
    override val payload = JsonObject.serializer()

    override suspend fun snapshot(): List<LocalRecord> {
        val shaById = store.localBooks()
        return dao.readerSettings().mapNotNull { row ->
            val sha = shaById[row.bookId] ?: return@mapNotNull null
            val settings = readerSettings.decodeOrNull(row.json) ?: return@mapNotNull null
            LocalRecord(key, sha, sha, ReaderSettingsSync.toPayload(settings), 1)
        }
    }

    override suspend fun apply(id: String, payload: JsonObject?, ctx: ApplyContext): Applied {
        val book = BookId(ctx.bookId ?: return stored(payload))
        if (payload == null) {
            readerSettings.clearForBook(book)
            return Applied(null)
        }
        val merged = ReaderSettingsSync.merge(readerSettings.effective(book).first(), payload, fonts.installed()::get)
        readerSettings.setForBook(book, merged)
        return Applied(ReaderSettingsSync.toPayload(merged))
    }
}

/**
 * A book's collection memberships and manual status, applied as one unit. The wire key is the
 * legacy "shelf"; older builds ignore it.
 */
@Singleton
internal class BookCollectionsKind @Inject constructor(private val dao: SyncDao, private val collectionDao: CollectionDao) : RecordKind<ShelfPayload> {
    override val key = "shelf"
    override val bookBound = true
    override val watchedTables = setOf("books", "book_collections")
    override val payload = ShelfPayload.serializer()

    // A book in no collection and with no manual status has nothing to send.
    override suspend fun snapshot(): List<LocalRecord> {
        val members = collectionDao.memberships().groupBy({ it.bookId }, { it.collectionId })
        return dao.books().mapNotNull { book ->
            val ids = members[book.id].orEmpty().sorted()
            if (ids.isEmpty() && book.manualStatus == null) return@mapNotNull null
            record(book.sha256, book.sha256, ShelfPayload(ids, book.manualStatus), maxOf(book.shelfEditedAt, 1))
        }
    }

    override suspend fun apply(id: String, payload: ShelfPayload?, ctx: ApplyContext): Applied {
        val bookId = ctx.bookId ?: return stored(payload)
        // An unknown status name (from a newer build) is kept as-is so it round-trips.
        collectionDao.replaceShelf(bookId, payload?.collections.orEmpty(), payload?.status, editedAt = maxOf(ctx.hlc.physical, 1))
        return stored(payload)
    }
}

/**
 * Reading sessions, append-only: a session pruned or removed locally still counts everywhere
 * else. Sessions for a book not on this device are kept under a placeholder book id.
 */
@Singleton
internal class SessionKind @Inject constructor(private val dao: SyncDao, private val stats: StatsRepository) : RecordKind<SessionPayload> {
    override val key = "session"
    override val bookBound = true
    override val neverDeleted = true
    override val appliesWithoutBook = true
    override val watchedTables = setOf("reading_sessions")
    override val payload = SessionPayload.serializer()

    override fun bookSha(id: String, payload: SessionPayload?) = payload?.sha256

    override suspend fun snapshot() = dao.unsyncedSessions().map { s ->
        val sha = checkNotNull(s.bookSha256)
        record(checkNotNull(s.uuid), sha, SessionPayload(sha, s.startedAt, s.seconds), s.startedAt)
    }

    override suspend fun apply(id: String, payload: SessionPayload?, ctx: ApplyContext): Applied {
        val s = payload ?: return Applied(null)
        if (!dao.hasSession(id)) {
            stats.applySyncedSession(id, ctx.bookId ?: placeholderId(s.sha256), s.sha256, s.startedAt, s.seconds)
        }
        return stored(s)
    }

    override suspend fun onBookAdded(sha256: String, bookId: String) {
        dao.adoptSessions(sha256, bookId)
        dao.adoptReadingTotal(placeholderId(sha256), bookId)
    }

    private fun placeholderId(sha256: String) = "sync:$sha256"
}
