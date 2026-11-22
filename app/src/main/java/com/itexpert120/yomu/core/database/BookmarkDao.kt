package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.itexpert120.yomu.core.reader.BookmarkAnchor
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    // Reading-position order (start of book to end), with recency breaking ties.
    @Query(
        "SELECT * FROM bookmarks WHERE bookId = :bookId " +
            "ORDER BY CASE WHEN progression < 0 THEN 1 ELSE 0 END, progression ASC, createdAt ASC",
    )
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt, id")
    suspend fun positionsForBook(bookId: String): List<BookmarkEntity>

    suspend fun bookmarksAt(bookId: String, at: BookmarkAnchor): List<BookmarkEntity> = positionsForBook(bookId).filter { it.anchor.matches(at) }

    /**
     * Idempotent: bookmarking adds [bookmark] only when nothing is already at its anchor; unbookmarking
     * removes every bookmark at that anchor (including legacy duplicates).
     */
    @Transaction
    suspend fun setBookmarked(bookmark: BookmarkEntity, bookmarked: Boolean) {
        val existing = bookmarksAt(bookmark.bookId, bookmark.anchor)
        if (bookmarked) {
            if (existing.isEmpty()) insert(bookmark)
        } else {
            existing.forEach { deleteById(it.id) }
        }
    }

    @Query("DELETE FROM bookmarks WHERE bookId IN (:bookIds)")
    suspend fun deleteForBooks(bookIds: List<String>)
}

// The column is non-null, so an unknown whole-book position is stored as this sentinel. It is only
// encoded and decoded here; everything above the database sees a null progression.
private const val UNKNOWN_PROGRESSION = -1.0

val BookmarkEntity.anchor: BookmarkAnchor
    get() = BookmarkAnchor(href, locatorJson, progression.takeIf { it >= 0 })

fun BookmarkAnchor.toBookmarkEntity(
    id: String,
    bookId: String,
    chapterTitle: String?,
    createdAt: Long,
): BookmarkEntity = BookmarkEntity(
    id = id,
    bookId = bookId,
    locatorJson = locatorJson,
    href = anchor,
    chapterTitle = chapterTitle,
    progression = progression ?: UNKNOWN_PROGRESSION,
    createdAt = createdAt,
)
