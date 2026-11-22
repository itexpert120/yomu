package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.BookEntity
import com.itexpert120.yomu.core.database.CollectionDao
import com.itexpert120.yomu.core.database.SyncDao
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.data.fonts.FontRepository
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives sync records from this device's current state, one kind at a time. Each payload is built
 * only from the fields [SyncApplier] writes back, so applying a record and re-snapshotting it gives
 * the same string (otherwise every applied change would echo back as a local edit).
 */
@Singleton
internal class SyncSnapshotter @Inject constructor(
    private val dao: SyncDao,
    private val collectionDao: CollectionDao,
    private val readerSettings: ReaderSettingsRepository,
    private val appSettings: AppSettingsRepository,
    private val libraryPrefs: LibraryPrefsRepository,
    private val fonts: FontRepository,
) {
    /** Content hashes of the books on this device, by local id. */
    suspend fun localBooks(): Map<String, String> = dao.books().associate { it.id to it.sha256 }

    suspend fun snapshot(kind: SyncKind): List<LocalRecord> = when (kind) {
        SyncKind.Meta -> dao.books().mapNotNull(::meta)
        SyncKind.Progress -> dao.books().mapNotNull(::progress)
        SyncKind.Chapter -> chapters()
        SyncKind.Bookmark -> bookmarks()
        SyncKind.Highlight -> highlights()
        SyncKind.BookSettings -> bookSettings()
        SyncKind.Shelf -> shelves()
        SyncKind.Collection -> collectionDao.collections().map {
            val payload = CollectionPayload(it.name, it.position)
            LocalRecord(SyncKind.Collection, it.id, null, SyncJson.encodeToString(CollectionPayload.serializer(), payload), maxOf(it.createdAt, 1))
        }
        SyncKind.Session -> sessions()
        SyncKind.Settings -> settings()
        SyncKind.Theme -> readerSettings.customThemes.first().map(::theme)
        SyncKind.Font -> fonts.installed.first().map {
            LocalRecord(SyncKind.Font, it.family, null, SyncJson.encodeToString(FontPayload.serializer(), FontPayload(it.family)), 1)
        }
    }

    /** The current progress record for one book, or null if it has never been opened here. */
    suspend fun progressFor(sha256: String): LocalRecord? = dao.bookByHash(sha256)?.let(::progress)

    // Only user edits sync, so a fresh import's extracted metadata never overwrites an edit made elsewhere.
    private fun meta(book: BookEntity): LocalRecord? {
        if (book.metadataEditedAt <= 0L) return null
        val payload = MetaPayload(book.title, book.subtitle, book.author, book.description)
        return LocalRecord(SyncKind.Meta, book.sha256, book.sha256, SyncJson.encodeToString(MetaPayload.serializer(), payload), book.metadataEditedAt)
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
        return LocalRecord(
            SyncKind.Progress,
            book.sha256,
            book.sha256,
            SyncJson.encodeToString(ProgressPayload.serializer(), payload),
            maxOf(book.lastOpenedAt, book.finishedAt),
        )
    }

    private suspend fun chapters(): List<LocalRecord> {
        val shaById = localBooks()
        val read = dao.chapterReads().mapTo(HashSet()) { it.bookId to it.chapterId }
        val progress = dao.chapterProgress().associateBy { it.bookId to it.chapterId }
        return (read + progress.keys).mapNotNull { key ->
            val sha = shaById[key.first] ?: return@mapNotNull null
            val row = progress[key]
            val payload = ChapterPayload(key in read, row?.progress, row?.updatedAt ?: 0L, row?.manuallyRead ?: false)
            LocalRecord(
                SyncKind.Chapter,
                ChapterRecordId.of(sha, key.second),
                sha,
                SyncJson.encodeToString(ChapterPayload.serializer(), payload),
                maxOf(payload.updatedAt, 1),
            )
        }
    }

    /** One chapter's current state on this device, or null if it has none. */
    suspend fun chapterFor(bookId: String, chapterId: String): ChapterPayload? {
        val read = dao.isChapterRead(bookId, chapterId)
        val row = dao.chapterProgressFor(bookId, chapterId)
        if (!read && row == null) return null
        return ChapterPayload(read, row?.progress, row?.updatedAt ?: 0L, row?.manuallyRead ?: false)
    }

    private suspend fun bookmarks(): List<LocalRecord> {
        val shaById = localBooks()
        return dao.bookmarks().mapNotNull { b ->
            val sha = shaById[b.bookId] ?: return@mapNotNull null
            val payload = BookmarkPayload(sha, b.locatorJson, b.href, b.chapterTitle, b.progression, b.createdAt)
            LocalRecord(SyncKind.Bookmark, b.id, sha, SyncJson.encodeToString(BookmarkPayload.serializer(), payload), b.createdAt)
        }
    }

    private suspend fun highlights(): List<LocalRecord> {
        val shaById = localBooks()
        return dao.highlights().mapNotNull { h ->
            val sha = shaById[h.bookId] ?: return@mapNotNull null
            val payload = HighlightPayload(sha, h.locatorJson, h.text, h.colorArgb, h.createdAt)
            LocalRecord(SyncKind.Highlight, h.id, sha, SyncJson.encodeToString(HighlightPayload.serializer(), payload), h.createdAt)
        }
    }

    private suspend fun bookSettings(): List<LocalRecord> {
        val shaById = localBooks()
        return dao.readerSettings().mapNotNull { row ->
            val sha = shaById[row.bookId] ?: return@mapNotNull null
            val settings = readerSettings.decodeOrNull(row.json) ?: return@mapNotNull null
            LocalRecord(SyncKind.BookSettings, sha, sha, ReaderSettingsSync.toPayload(settings), 1)
        }
    }

    // A book in no collection and with no manual status has no shelf worth sending.
    private suspend fun shelves(): List<LocalRecord> {
        val members = collectionDao.memberships().groupBy({ it.bookId }, { it.collectionId })
        return dao.books().mapNotNull { book ->
            val ids = members[book.id].orEmpty().sorted()
            if (ids.isEmpty() && book.manualStatus == null) return@mapNotNull null
            val payload = ShelfPayload(ids, book.manualStatus)
            LocalRecord(SyncKind.Shelf, book.sha256, book.sha256, SyncJson.encodeToString(ShelfPayload.serializer(), payload), maxOf(book.shelfEditedAt, 1))
        }
    }

    private suspend fun sessions(): List<LocalRecord> = dao.unsyncedSessions().map { s ->
        val sha = checkNotNull(s.bookSha256)
        val payload = SessionPayload(sha, s.startedAt, s.seconds)
        LocalRecord(SyncKind.Session, checkNotNull(s.uuid), sha, SyncJson.encodeToString(SessionPayload.serializer(), payload), s.startedAt)
    }

    private suspend fun settings(): List<LocalRecord> {
        val app = AppSettingsPayload(appSettings.themePreference.first().name, appSettings.colorStyle.first().name)
        val library = libraryPrefs.preferences.first().let { LibraryPayload(it.sortMode.name, LEGACY_GROUP_NONE, it.viewMode.name, it.coverCrop) }
        // Settings carry no edit time; dating them at the epoch means a joining device adopts the
        // group's settings instead of overwriting them with its own.
        return listOf(
            LocalRecord(SyncKind.Settings, SettingsId.APP, null, SyncJson.encodeToString(AppSettingsPayload.serializer(), app), 1),
            LocalRecord(SyncKind.Settings, SettingsId.LIBRARY, null, SyncJson.encodeToString(LibraryPayload.serializer(), library), 1),
            LocalRecord(SyncKind.Settings, SettingsId.READER_DEFAULTS, null, ReaderSettingsSync.toPayload(readerSettings.global.first()), 1),
        )
    }

    private fun theme(theme: CustomReaderTheme) = LocalRecord(SyncKind.Theme, theme.id, null, SyncJson.encodeToString(CustomReaderTheme.serializer(), theme), 1)
}

// Group by was retired, but older app versions still require the payload's `group` field to decode
// library settings, so it is always sent as their "no grouping" value.
private const val LEGACY_GROUP_NONE = "None"
