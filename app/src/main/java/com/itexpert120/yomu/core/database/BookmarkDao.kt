package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    // Reading-position order (start of book to end), with recency breaking ties.
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY progression ASC, createdAt ASC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    // Current-page identity: same resource and within a 1% progression window. `href IS :href` so a
    // null href compares correctly in SQLite (a plain `=` never matches NULL).
    @Query(
        "SELECT EXISTS(SELECT 1 FROM bookmarks WHERE bookId = :bookId " +
            "AND href IS :href AND ABS(progression - :progression) < 0.01)",
    )
    suspend fun existsAt(bookId: String, href: String?, progression: Double): Boolean

    @Query(
        "DELETE FROM bookmarks WHERE bookId = :bookId " +
            "AND href IS :href AND ABS(progression - :progression) < 0.01",
    )
    suspend fun deleteAt(bookId: String, href: String?, progression: Double)
}
