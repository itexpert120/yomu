package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    /**
     * The [limit] most-recently-opened books (newest first), for the resizable library widget's
     * fast-launch grid. Books that have never been opened sort last (lastOpenedAt defaults to 0).
     */
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC LIMIT :limit")
    suspend fun getRecentBooks(limit: Int): List<BookEntity>

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun getBooks(ids: List<String>): List<BookEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE sha256 = :sha256)")
    suspend fun existsByHash(sha256: String): Boolean

    @Query("SELECT id FROM books WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findIdByHash(sha256: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Query("UPDATE books SET progress = 1.0 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE books SET progress = 0.0, totalProgression = NULL, locatorJson = NULL WHERE id = :id")
    suspend fun markUnread(id: String)

    @Query(
        "UPDATE books SET title = :title, subtitle = :subtitle, author = :author, " +
                "description = :description, coverImagePath = :coverImagePath WHERE id = :id",
    )
    suspend fun updateMetadata(
        id: String,
        title: String,
        subtitle: String?,
        author: String,
        description: String?,
        coverImagePath: String?,
    )

    @Query(
        "UPDATE books SET progress = :progress, totalProgression = :totalProgression, " +
                "locatorJson = :locatorJson, lastOpenedAt = :lastOpenedAt WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        progress: Float,
        totalProgression: Double?,
        locatorJson: String?,
        lastOpenedAt: Long,
    )

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    // region Chapter read-state

    @Query("SELECT chapterId FROM chapter_reads WHERE bookId = :bookId")
    fun observeReadChapters(bookId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReadChapters(rows: List<ChapterReadEntity>)

    @Query("DELETE FROM chapter_reads WHERE bookId = :bookId AND chapterId IN (:chapterIds)")
    suspend fun deleteReadChapters(bookId: String, chapterIds: List<String>)

    @Query("DELETE FROM chapter_reads WHERE bookId IN (:bookIds)")
    suspend fun deleteAllReadChapters(bookIds: List<String>)

    // endregion

    // region Per-book reader settings

    @Query("SELECT * FROM reader_settings WHERE bookId = :bookId")
    fun observeReaderSettings(bookId: String): Flow<ReaderSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReaderSettings(entity: ReaderSettingsEntity)

    @Query("DELETE FROM reader_settings WHERE bookId = :bookId")
    suspend fun deleteReaderSettings(bookId: String)

    @Query("DELETE FROM reader_settings WHERE bookId IN (:bookIds)")
    suspend fun deleteReaderSettingsForBooks(bookIds: List<String>)

    // endregion

    // region Cached table of contents

    @Query("SELECT json FROM book_toc WHERE bookId = :bookId")
    suspend fun getCachedToc(bookId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToc(entity: BookTocEntity)

    @Query("DELETE FROM book_toc WHERE bookId IN (:bookIds)")
    suspend fun deleteTocForBooks(bookIds: List<String>)

    // endregion

    // region Reading statistics

    @Query("SELECT * FROM reading_days")
    fun observeReadingDays(): Flow<List<ReadingDayEntity>>

    @Query("SELECT seconds FROM reading_days WHERE date = :date")
    suspend fun getReadingDaySeconds(date: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReadingDay(entity: ReadingDayEntity)

    @Query("SELECT COUNT(*) FROM chapter_reads")
    fun observeChapterReadCount(): Flow<Int>

    @Insert
    suspend fun insertReadingSession(entity: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<ReadingSessionEntity>>

    /**
     * (startedAt, seconds) for every session, newest first. Drives the weekday / hour-of-day
     * distributions and the session aggregates (count, average, longest) — the seconds are small
     * (one row per finished session) so loading them is cheap.
     */
    @Query("SELECT startedAt, seconds FROM reading_sessions ORDER BY startedAt DESC")
    fun observeSessionTimes(): Flow<List<SessionTime>>

    @Query("DELETE FROM reading_sessions WHERE bookId IN (:bookIds)")
    suspend fun deleteSessionsForBooks(bookIds: List<String>)

    // endregion
}

/** Lightweight projection of a reading session: when it started and how long it lasted. */
data class SessionTime(
    val startedAt: Long,
    val seconds: Long,
)
