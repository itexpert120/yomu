package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Ledger, metadata, and the cross-table reads/writes the sync engine needs. */
@Dao
interface SyncDao {

    // region Ledger

    @Query("SELECT * FROM sync_records WHERE recordKey = :key")
    suspend fun ledger(key: String): SyncLedgerEntity?

    @Query("SELECT * FROM sync_records WHERE kind = :kind")
    suspend fun ledgerOfKind(kind: String): List<SyncLedgerEntity>

    @Query("SELECT * FROM sync_records WHERE bookSha256 = :sha256 AND deleted = 0")
    suspend fun liveLedgerForBook(sha256: String): List<SyncLedgerEntity>

    @Query("SELECT * FROM sync_records WHERE dirty = 1 ORDER BY physicalTime, logicalCounter LIMIT :limit")
    suspend fun dirty(limit: Int): List<SyncLedgerEntity>

    @Query("SELECT COUNT(*) FROM sync_records WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedger(rows: List<SyncLedgerEntity>)

    /** Clears [dirty] only if the row wasn't changed again while it was being pushed. */
    @Query(
        "UPDATE sync_records SET dirty = 0 WHERE recordKey = :key AND physicalTime = :physical " +
            "AND logicalCounter = :counter AND originDeviceId = :device",
    )
    suspend fun markPushed(key: String, physical: Long, counter: Long, device: String)

    @Query("DELETE FROM sync_records")
    suspend fun clearLedger()

    // endregion

    // region Metadata

    @Query("SELECT value FROM sync_metadata WHERE `key` = :key")
    suspend fun metadata(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(entity: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun deleteMetadata(key: String)

    @Query("SELECT * FROM sync_metadata WHERE `key` LIKE :prefix || '%'")
    fun observeMetadataWithPrefix(prefix: String): Flow<List<SyncMetadataEntity>>

    @Query("SELECT * FROM sync_metadata WHERE `key` LIKE :prefix || '%'")
    suspend fun metadataWithPrefix(prefix: String): List<SyncMetadataEntity>

    @Query("DELETE FROM sync_metadata")
    suspend fun clearMetadata()

    @Transaction
    suspend fun reset() {
        clearLedger()
        clearMetadata()
    }

    // endregion

    // region Local state reads

    @Query("SELECT * FROM books")
    suspend fun books(): List<BookEntity>

    @Query("SELECT * FROM books WHERE sha256 = :sha256")
    suspend fun bookByHash(sha256: String): BookEntity?

    @Query("SELECT sha256 FROM books WHERE id = :bookId")
    suspend fun shaForBook(bookId: String): String?

    @Query("SELECT * FROM bookmarks")
    suspend fun bookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM highlights")
    suspend fun highlights(): List<HighlightEntity>

    @Query("SELECT * FROM chapter_reads")
    suspend fun chapterReads(): List<ChapterReadEntity>

    @Query("SELECT * FROM chapter_progress")
    suspend fun chapterProgress(): List<ChapterProgressEntity>

    @Query("SELECT * FROM reader_settings")
    suspend fun readerSettings(): List<ReaderSettingsEntity>

    /** Sessions not yet in the ledger. Sessions never change, so only new ones need a look. */
    @Query(
        "SELECT * FROM reading_sessions WHERE uuid IS NOT NULL AND bookSha256 IS NOT NULL " +
            "AND ('session/' || uuid) NOT IN (SELECT recordKey FROM sync_records WHERE kind = 'session')",
    )
    suspend fun unsyncedSessions(): List<ReadingSessionEntity>

    // endregion

    // region Applying remote changes

    @Query(
        "UPDATE books SET title = :title, subtitle = :subtitle, author = :author, " +
            "description = :description, metadataEditedAt = :editedAt WHERE sha256 = :sha256",
    )
    suspend fun applyMetadata(sha256: String, title: String, subtitle: String?, author: String, description: String?, editedAt: Long)

    @Query(
        "UPDATE books SET progress = :progress, totalProgression = :totalProgression, " +
            "locatorJson = :locatorJson, syncLocatorJson = :locatorJson, currentChapterId = :chapterId, " +
            "lastOpenedAt = :lastOpenedAt, startedAt = :startedAt, finishedAt = :finishedAt WHERE sha256 = :sha256",
    )
    suspend fun applyProgress(
        sha256: String,
        progress: Float,
        totalProgression: Double?,
        locatorJson: String?,
        chapterId: String?,
        lastOpenedAt: Long,
        startedAt: Long,
        finishedAt: Long,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM chapter_reads WHERE bookId = :bookId AND chapterId = :chapterId)")
    suspend fun isChapterRead(bookId: String, chapterId: String): Boolean

    @Query("SELECT * FROM chapter_progress WHERE bookId = :bookId AND chapterId = :chapterId")
    suspend fun chapterProgressFor(bookId: String, chapterId: String): ChapterProgressEntity?

    @Query("DELETE FROM chapter_reads WHERE bookId = :bookId AND chapterId = :chapterId")
    suspend fun deleteChapterRead(bookId: String, chapterId: String)

    @Query("DELETE FROM chapter_progress WHERE bookId = :bookId AND chapterId = :chapterId")
    suspend fun deleteChapterProgressFor(bookId: String, chapterId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterRead(row: ChapterReadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapterProgress(row: ChapterProgressEntity)

    /** Makes one chapter's rows match a synced state ([progress] null = no progress row). */
    @Transaction
    suspend fun applyChapter(bookId: String, chapterId: String, read: Boolean, progress: ChapterProgressEntity?) {
        if (read) insertChapterRead(ChapterReadEntity(bookId, chapterId)) else deleteChapterRead(bookId, chapterId)
        if (progress != null) insertChapterProgress(progress) else deleteChapterProgressFor(bookId, chapterId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM reading_sessions WHERE uuid = :uuid)")
    suspend fun hasSession(uuid: String): Boolean

    /** Points sessions synced before their book was imported here at the now-local book. */
    @Query("UPDATE reading_sessions SET bookId = :bookId WHERE bookSha256 = :sha256 AND bookId != :bookId")
    suspend fun adoptSessions(sha256: String, bookId: String)

    @Query("SELECT * FROM reading_totals WHERE bookId = :bookId")
    suspend fun readingTotal(bookId: String): ReadingTotalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingTotal(entity: ReadingTotalEntity)

    @Query("DELETE FROM reading_totals WHERE bookId = :bookId")
    suspend fun deleteReadingTotal(bookId: String)

    /** Moves a placeholder's lifetime totals onto the imported book. */
    @Transaction
    suspend fun adoptReadingTotal(placeholderId: String, bookId: String) {
        val placeholder = readingTotal(placeholderId) ?: return
        val existing = readingTotal(bookId)
        upsertReadingTotal(
            ReadingTotalEntity(
                bookId = bookId,
                seconds = placeholder.seconds + (existing?.seconds ?: 0L),
                sessionCount = placeholder.sessionCount + (existing?.sessionCount ?: 0),
                longestSeconds = maxOf(placeholder.longestSeconds, existing?.longestSeconds ?: 0L),
            ),
        )
        deleteReadingTotal(placeholderId)
    }

    // endregion
}
