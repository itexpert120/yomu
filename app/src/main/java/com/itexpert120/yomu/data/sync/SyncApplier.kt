package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.ChapterProgressEntity
import com.itexpert120.yomu.core.database.HighlightEntity
import com.itexpert120.yomu.core.database.SyncDao
import com.itexpert120.yomu.core.database.SyncMetadataEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ColorStyle
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.core.model.CustomReaderTheme
import com.itexpert120.yomu.core.model.GroupMode
import com.itexpert120.yomu.core.model.LibraryViewMode
import com.itexpert120.yomu.core.model.SortMode
import com.itexpert120.yomu.core.model.ThemePreference
import com.itexpert120.yomu.data.fonts.FontRepository
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import com.itexpert120.yomu.data.stats.StatsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the ledger should record after applying: the payload now true locally, whether to push it
 * back (a first-contact merge), and whether it waits for a book this device doesn't have yet.
 */
internal data class Applied(
    val ledgerPayload: String?,
    val dirty: Boolean = false,
    val sha256: String? = null,
    val awaitingBook: Boolean = false,
)

/** A newer reading position from another device, offered instead of silently moving this one. */
@Serializable
data class ProgressOffer(
    val sha256: String,
    val locatorJson: String,
    val progress: Float,
    val device: String?,
    val hlc: String,
)

/** Writes incoming records into Room and DataStore. Callers serialise all calls (SyncEngine's mutex). */
@Singleton
internal class SyncApplier @Inject constructor(
    private val dao: SyncDao,
    private val snapshotter: SyncSnapshotter,
    private val readerSettings: ReaderSettingsRepository,
    private val appSettings: AppSettingsRepository,
    private val libraryPrefs: LibraryPrefsRepository,
    private val fonts: FontRepository,
    private val stats: StatsRepository,
) {
    /**
     * [firstContact]: this device has never reconciled the record (no ledger row), so local data
     * may predate sync and gets merged rather than replaced where that's meaningful.
     */
    suspend fun apply(kind: SyncKind, id: String, payload: String?, firstContact: Boolean, device: String?, hlc: Hlc): Applied {
        val sha = when (kind) {
            SyncKind.Bookmark -> payload?.let { SyncJson.decodeFromString(BookmarkPayload.serializer(), it).sha256 }
            SyncKind.Highlight -> payload?.let { SyncJson.decodeFromString(HighlightPayload.serializer(), it).sha256 }
            SyncKind.Session -> payload?.let { SyncJson.decodeFromString(SessionPayload.serializer(), it).sha256 }
            SyncKind.Chapter -> ChapterRecordId.sha256(id)
            else -> if (kind.bookBound) id else null
        }
        val book = sha?.let { dao.bookByHash(it) }
        // A book this device doesn't have: remember the record and apply it if the book is imported.
        if (kind.bookBound && kind != SyncKind.Session && payload != null && book == null) {
            return Applied(payload, sha256 = sha, awaitingBook = true)
        }

        return applyPresent(kind, id, payload, firstContact, device, hlc, book?.id).copy(sha256 = sha)
    }

    private suspend fun applyPresent(
        kind: SyncKind,
        id: String,
        payload: String?,
        firstContact: Boolean,
        device: String?,
        hlc: Hlc,
        bookId: String?,
    ): Applied {
        return when (kind) {
            SyncKind.Meta -> {
                val meta = SyncJson.decodeFromString(MetaPayload.serializer(), payload ?: return Applied(null))
                dao.applyMetadata(id, meta.title, meta.subtitle, meta.author, meta.description, editedAt = maxOf(hlc.physical, 1))
                Applied(payload)
            }

            SyncKind.Progress -> applyProgress(id, payload ?: return Applied(null), device, hlc)

            SyncKind.Chapter -> {
                val localBookId = bookId ?: return Applied(payload)
                val chapterId = ChapterRecordId.chapterId(id)
                if (payload == null) {
                    dao.applyChapter(localBookId, chapterId, read = false, progress = null)
                    return Applied(null)
                }
                val remote = SyncJson.decodeFromString(ChapterPayload.serializer(), payload)
                val local = if (firstContact) snapshotter.chapterFor(localBookId, chapterId) else null
                val result = local?.mergedWith(remote) ?: remote
                dao.applyChapter(
                    localBookId,
                    chapterId,
                    read = result.read,
                    progress = result.progress?.let { ChapterProgressEntity(localBookId, chapterId, it, result.updatedAt, result.manual) },
                )
                val resultPayload = SyncJson.encodeToString(ChapterPayload.serializer(), result)
                Applied(resultPayload, dirty = resultPayload != payload)
            }

            SyncKind.Bookmark -> {
                if (payload == null) {
                    dao.deleteBookmark(id)
                } else {
                    val b = SyncJson.decodeFromString(BookmarkPayload.serializer(), payload)
                    dao.upsertBookmark(BookmarkEntity(id, checkNotNull(bookId), b.locator, b.href, b.chapterTitle, b.progression, b.createdAt))
                }
                Applied(payload)
            }

            SyncKind.Highlight -> {
                if (payload == null) {
                    dao.deleteHighlight(id)
                } else {
                    val h = SyncJson.decodeFromString(HighlightPayload.serializer(), payload)
                    dao.upsertHighlight(HighlightEntity(id, checkNotNull(bookId), h.locator, h.text, h.color, h.createdAt))
                }
                Applied(payload)
            }

            SyncKind.BookSettings -> {
                val localBook = BookId(bookId ?: return Applied(payload))
                if (payload == null) {
                    readerSettings.clearForBook(localBook)
                    return Applied(null)
                }
                val local = readerSettings.effective(localBook).first()
                val merged = ReaderSettingsSync.merge(local, payload, ::installedFont)
                readerSettings.setForBook(localBook, merged)
                Applied(ReaderSettingsSync.toPayload(merged))
            }

            SyncKind.Session -> {
                val s = SyncJson.decodeFromString(SessionPayload.serializer(), payload ?: return Applied(null))
                if (!dao.hasSession(id)) {
                    stats.applySyncedSession(id, bookId ?: placeholderId(s.sha256), s.sha256, s.startedAt, s.seconds)
                }
                Applied(payload)
            }

            SyncKind.Settings -> applySettings(id, payload ?: return Applied(null))

            SyncKind.Theme -> {
                if (payload == null) {
                    readerSettings.deleteCustomTheme(id)
                } else {
                    readerSettings.saveCustomTheme(SyncJson.decodeFromString(CustomReaderTheme.serializer(), payload))
                }
                Applied(payload)
            }

            SyncKind.Font -> {
                if (payload == null) {
                    fonts.remove(id)
                } else if (installedFont(id) == null) {
                    // Remembered until the download succeeds, so a failed install never reads as a removal.
                    dao.putMetadata(SyncMetadataEntity(PENDING_FONT_PREFIX + id, id))
                    installPendingFont(id)
                }
                Applied(payload)
            }
        }
    }

    /** Applies everything already known for a book that was just imported on this device. */
    suspend fun onBookAdded(sha256: String, bookId: String) {
        dao.adoptSessions(sha256, bookId)
        dao.adoptReadingTotal(placeholderId(sha256), bookId)
        dao.liveLedgerForBook(sha256)
            .filter { it.kind != SyncKind.Session.key }
            .sortedBy { it.kind }
            .forEach { row ->
                val kind = SyncKind.of(row.recordKey) ?: return@forEach
                val hlc = Hlc(row.physicalTime, row.logicalCounter.toInt(), row.originDeviceId)
                apply(kind, row.entityId, row.payloadJson, firstContact = false, device = null, hlc = hlc)
            }
    }

    /** Retries font downloads that failed earlier (offline, for instance). */
    suspend fun retryPendingFonts() {
        dao.metadataWithPrefix(PENDING_FONT_PREFIX).forEach { installPendingFont(it.value) }
    }

    suspend fun pendingFonts(): Set<String> = dao.metadataWithPrefix(PENDING_FONT_PREFIX).map { it.value }.toSet()

    private suspend fun installPendingFont(family: String) {
        if (installedFont(family) != null || fonts.install(family).isSuccess) {
            dao.deleteMetadata(PENDING_FONT_PREFIX + family)
            refreshInstalledFonts()
        }
    }

    private suspend fun applyProgress(sha: String, payload: String, device: String?, hlc: Hlc): Applied {
        val remote = SyncJson.decodeFromString(ProgressPayload.serializer(), payload)
        val local = snapshotter.progressFor(sha)
        val localPayload = local?.let { SyncJson.decodeFromString(ProgressPayload.serializer(), it.payload) }
        // Adopt silently when there is nothing local to lose, when it's the same place, or when
        // the other device marked the book read/unread (a state change, not a reading position).
        if (localPayload == null || remote.locator == null || remote.locator == localPayload.locator) {
            dao.applyProgress(
                sha,
                remote.progress,
                remote.totalProgression,
                remote.locator,
                remote.chapterId,
                remote.lastOpenedAt,
                remote.startedAt,
                remote.finishedAt,
            )
            dao.deleteMetadata(OFFER_PREFIX + sha)
            return Applied(payload)
        }
        val offer = ProgressOffer(sha, remote.locator, remote.progress, device, hlc.encode())
        dao.putMetadata(SyncMetadataEntity(OFFER_PREFIX + sha, SyncJson.encodeToString(ProgressOffer.serializer(), offer)))
        // The ledger keeps this device's position; reading on here will then be the newest change.
        return Applied(local.payload)
    }

    private suspend fun applySettings(id: String, payload: String): Applied = when (id) {
        SettingsId.APP -> {
            val app = SyncJson.decodeFromString(AppSettingsPayload.serializer(), payload)
            runCatching { ThemePreference.valueOf(app.theme) }.getOrNull()?.let { appSettings.setThemePreference(it) }
            ColorStyle.fromStoredName(app.colorStyle)?.let { appSettings.setColorStyle(it) }
            Applied(payload)
        }

        SettingsId.LIBRARY -> {
            val library = SyncJson.decodeFromString(LibraryPayload.serializer(), payload)
            SortMode.entries.firstOrNull { it.name == library.sort }?.let { libraryPrefs.setSortMode(it) }
            GroupMode.entries.firstOrNull { it.name == library.group }?.let { libraryPrefs.setGroupMode(it) }
            LibraryViewMode.entries.firstOrNull { it.name == library.view }?.let { libraryPrefs.setViewMode(it) }
            libraryPrefs.setCoverCrop(library.coverCrop)
            Applied(payload)
        }

        SettingsId.READER_DEFAULTS -> {
            val merged = ReaderSettingsSync.merge(readerSettings.global.first(), payload, ::installedFont)
            readerSettings.setGlobal(merged)
            Applied(ReaderSettingsSync.toPayload(merged))
        }

        else -> Applied(payload)
    }

    private var installedCache: Map<String, CustomFontRef> = emptyMap()

    private fun installedFont(family: String) = installedCache[family]

    /** Refreshes the installed-font lookup used while merging settings. Call before a batch. */
    suspend fun refreshInstalledFonts() {
        installedCache = fonts.installed.first().associateBy { it.family }
    }

    companion object {
        const val OFFER_PREFIX = "offer/"
        private const val PENDING_FONT_PREFIX = "fontPending/"

        fun placeholderId(sha256: String) = "sync:$sha256"
    }
}
