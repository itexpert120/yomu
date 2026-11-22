package com.itexpert120.yomu.data.collections

import androidx.room.withTransaction
import com.itexpert120.yomu.core.database.BookCollectionEntity
import com.itexpert120.yomu.core.database.CollectionDao
import com.itexpert120.yomu.core.database.CollectionEntity
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookCollection
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import com.itexpert120.yomu.data.books.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The library catalogue: books joined with their collections ([observe]), and the organize
 * writes. Every write that changes a book's manual status or collections stamps the book's
 * collections edit time, which is what sync uses to order those edits.
 */
@Singleton
class LibraryCatalog @Inject constructor(
    private val books: BookRepository,
    private val database: YomuDatabase,
    private val dao: CollectionDao,
) {
    fun observe(): Flow<Catalog> = combine(
        books.observeBooks(),
        dao.observeCollections(),
        dao.observeMemberships(),
    ) { bookList, collectionRows, membershipRows ->
        val memberships = membershipRows.groupBy({ it.bookId }, { it.collectionId })
        Catalog(
            entries = bookList.map { CatalogEntry(it, memberships[it.id.value]?.toSet().orEmpty()) },
            collections = collectionRows.map { BookCollection(it.id, it.name, it.position) },
        )
    }

    suspend fun setManualStatus(ids: List<BookId>, status: ManualStatus?) {
        if (ids.isEmpty()) return
        dao.setManualStatus(ids.map { it.value }, status?.name, now())
    }

    suspend fun setMembership(collectionId: String, ids: List<BookId>, member: Boolean) {
        if (ids.isEmpty()) return
        val bookIds = ids.map { it.value }
        database.withTransaction {
            if (member) {
                dao.insertMemberships(bookIds.map { BookCollectionEntity(it, collectionId) })
            } else {
                dao.deleteMemberships(collectionId, bookIds)
            }
            dao.touchCollectionEdits(bookIds, now())
        }
    }

    /** Creates a collection at the end of the order with the books [add] in it; returns its id. */
    suspend fun createCollection(name: String, add: List<BookId> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        database.withTransaction {
            val now = now()
            dao.upsertCollection(CollectionEntity(id, name.trim(), dao.maxPosition() + 1, now))
            if (add.isNotEmpty()) {
                val bookIds = add.map { it.value }
                dao.insertMemberships(bookIds.map { BookCollectionEntity(it, id) })
                dao.touchCollectionEdits(bookIds, now)
            }
        }
        return id
    }

    suspend fun renameCollection(id: String, name: String) = dao.rename(id, name.trim())

    /** Deletes a collection; its books stay in the library, with their collections edit stamped. */
    suspend fun deleteCollection(id: String) {
        dao.deleteCollection(id, now())
    }

    suspend fun reorderCollections(idsInOrder: List<String>) = dao.reorder(idsInOrder)

    private fun now() = System.currentTimeMillis()
}
