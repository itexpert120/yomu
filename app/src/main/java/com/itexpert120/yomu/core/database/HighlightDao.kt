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

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<HighlightEntity>>

    @Query("UPDATE highlights SET colorArgb = :colorArgb WHERE id = :id")
    suspend fun updateColor(id: String, colorArgb: Int)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: String)
}
