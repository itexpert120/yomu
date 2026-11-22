package com.itexpert120.yomu.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY position, name COLLATE NOCASE")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections")
    suspend fun collections(): List<CollectionEntity>

    @Query("SELECT * FROM book_collections")
    fun observeMemberships(): Flow<List<BookCollectionEntity>>

    @Query("SELECT * FROM book_collections")
    suspend fun memberships(): List<BookCollectionEntity>

    @Query("SELECT collectionId FROM book_collections WHERE bookId = :bookId")
    fun observeCollectionIdsFor(bookId: String): Flow<List<String>>

    @Query("SELECT collectionId FROM book_collections WHERE bookId = :bookId")
    suspend fun collectionIdsFor(bookId: String): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM collections")
    suspend fun maxPosition(): Int

    @Upsert
    suspend fun upsertCollection(collection: CollectionEntity)

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE collections SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionRow(id: String)

    @Query("SELECT bookId FROM book_collections WHERE collectionId = :collectionId")
    suspend fun bookIdsIn(collectionId: String): List<String>

    @Query("DELETE FROM book_collections WHERE collectionId = :collectionId")
    suspend fun deleteMembershipsOf(collectionId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(rows: List<BookCollectionEntity>)

    @Query("DELETE FROM book_collections WHERE collectionId = :collectionId AND bookId IN (:bookIds)")
    suspend fun deleteMemberships(collectionId: String, bookIds: List<String>)

    @Query("DELETE FROM book_collections WHERE bookId IN (:bookIds)")
    suspend fun deleteMembershipsForBooks(bookIds: List<String>)

    @Query("UPDATE books SET manualStatus = :status, shelfEditedAt = :editedAt WHERE id IN (:bookIds)")
    suspend fun setManualStatus(bookIds: List<String>, status: String?, editedAt: Long)

    // shelfEditedAt is the stored name of a book's collections-and-status edit time.
    @Query("UPDATE books SET shelfEditedAt = :editedAt WHERE id IN (:bookIds)")
    suspend fun touchCollectionEdits(bookIds: List<String>, editedAt: Long)

    /** Removes a collection and every membership in it; returns the books that were in it. */
    @Transaction
    suspend fun deleteCollection(id: String, editedAt: Long): List<String> {
        val books = bookIdsIn(id)
        deleteMembershipsOf(id)
        deleteCollectionRow(id)
        if (books.isNotEmpty()) touchCollectionEdits(books, editedAt)
        return books
    }

    /** Replaces one book's collections and manual status at once (sync applies them as a unit). */
    @Transaction
    suspend fun replaceCollectionsAndStatus(bookId: String, collectionIds: Collection<String>, status: String?, editedAt: Long) {
        deleteMembershipsForBooks(listOf(bookId))
        insertMemberships(collectionIds.map { BookCollectionEntity(bookId, it) })
        setManualStatus(listOf(bookId), status, editedAt)
    }

    @Transaction
    suspend fun reorder(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, id -> setPosition(id, index) }
    }
}
