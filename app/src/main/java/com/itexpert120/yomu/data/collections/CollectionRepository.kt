package com.itexpert120.yomu.data.collections

import com.itexpert120.yomu.core.database.CollectionDao
import com.itexpert120.yomu.core.database.CollectionEntity
import com.itexpert120.yomu.core.model.BookCollection
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.model.ManualStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/** User collections ("shelves"), book memberships, and the manual reading status. */
interface CollectionRepository {
    fun observeCollections(): Flow<List<BookCollection>>

    /** Collection ids per book id; books in no collection are absent. */
    fun observeMemberships(): Flow<Map<String, Set<String>>>

    fun observeCollectionIds(bookId: BookId): Flow<Set<String>>

    /** Creates a collection at the end of the order and returns its id. */
    suspend fun create(name: String): String

    suspend fun rename(id: String, name: String)

    suspend fun delete(id: String)

    suspend fun reorder(idsInOrder: List<String>)

    suspend fun setMembership(collectionId: String, bookIds: List<BookId>, member: Boolean)

    suspend fun setStatus(bookIds: List<BookId>, status: ManualStatus?)
}

class RoomCollectionRepository @Inject constructor(
    private val dao: CollectionDao,
) : CollectionRepository {
    override fun observeCollections(): Flow<List<BookCollection>> = dao.observeCollections().map { rows ->
        rows.map { BookCollection(it.id, it.name, it.position) }
    }

    override fun observeMemberships(): Flow<Map<String, Set<String>>> = dao.observeMemberships().map { rows ->
        rows.groupBy({ it.bookId }, { it.collectionId }).mapValues { it.value.toSet() }
    }

    override fun observeCollectionIds(bookId: BookId): Flow<Set<String>> = dao.observeCollectionIdsFor(bookId.value).map { it.toSet() }

    override suspend fun create(name: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertCollection(CollectionEntity(id, name.trim(), dao.maxPosition() + 1, System.currentTimeMillis()))
        return id
    }

    override suspend fun rename(id: String, name: String) = dao.rename(id, name.trim())

    override suspend fun delete(id: String) {
        dao.deleteCollection(id, System.currentTimeMillis())
    }

    override suspend fun reorder(idsInOrder: List<String>) = dao.reorder(idsInOrder)

    override suspend fun setMembership(collectionId: String, bookIds: List<BookId>, member: Boolean) {
        if (bookIds.isEmpty()) return
        dao.setMembership(collectionId, bookIds.map { it.value }, member, System.currentTimeMillis())
    }

    override suspend fun setStatus(bookIds: List<BookId>, status: ManualStatus?) {
        if (bookIds.isEmpty()) return
        dao.setManualStatus(bookIds.map { it.value }, status?.name, System.currentTimeMillis())
    }
}
