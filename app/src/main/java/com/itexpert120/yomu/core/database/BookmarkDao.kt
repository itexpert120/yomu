package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    // Current-page identity: same resource and within a 1% progression window. `href IS :href` so a
    // null href compares correctly in SQLite (a plain `=` never matches NULL).
    @Query(
        "SELECT * FROM bookmarks WHERE bookId = :bookId AND href IS :href AND " +
            "((:progression >= 0 AND progression >= 0 AND ABS(progression - :progression) < 0.01) " +
            "OR (:progression < 0 AND progression < 0 AND locatorJson = :locatorJson)) LIMIT 1",
    )
    suspend fun findAt(
        bookId: String,
        href: String?,
        progression: Double,
        locatorJson: String,
    ): BookmarkEntity?

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
