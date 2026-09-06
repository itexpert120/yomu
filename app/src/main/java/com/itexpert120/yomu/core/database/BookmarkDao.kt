package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.itexpert120.yomu.core.reader.BookmarkIdentity
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

    suspend fun findAt(
        bookId: String,
        href: String?,
        progression: Double,
        locatorJson: String,
    ): BookmarkEntity? = positionsForBook(bookId).firstOrNull {
        BookmarkIdentity.samePosition(
            href,
            locatorJson,
            progression.takeIf { value -> value >= 0 },
            it.href,
            it.locatorJson,
            it.progression.takeIf { value -> value >= 0 },
        )
    }

    @Transaction
    suspend fun toggle(bookmark: BookmarkEntity): Boolean {
        val existing = findAt(
            bookmark.bookId,
            bookmark.href,
            bookmark.progression,
            bookmark.locatorJson,
        )
        return if (existing != null) {
            deleteById(existing.id)
            false
        } else {
            insert(bookmark) != -1L
        }
    }

    @Query("DELETE FROM bookmarks WHERE bookId IN (:bookIds)")
    suspend fun deleteForBooks(bookIds: List<String>)
}
