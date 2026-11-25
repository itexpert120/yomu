package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    /** Insert or update (e.g. a colour change reuses the same id). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(highlight: HighlightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(highlights: List<HighlightEntity>)

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt, id")
    suspend fun forBook(bookId: String): List<HighlightEntity>

    @Query("UPDATE highlights SET colorArgb = :colorArgb, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateColor(id: String, colorArgb: Int, modifiedAt: Long)

    @Query("UPDATE highlights SET note = :note, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateNote(id: String, note: String?, modifiedAt: Long)

    @Query("UPDATE highlights SET style = :style, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateStyle(id: String, style: String, modifiedAt: Long)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM highlights WHERE bookId IN (:bookIds)")
    suspend fun deleteForBooks(bookIds: List<String>)
}
