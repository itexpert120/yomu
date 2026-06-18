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

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun getBooks(ids: List<String>): List<BookEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE sha256 = :sha256)")
    suspend fun existsByHash(sha256: String): Boolean

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
}
