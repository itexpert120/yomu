package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookRepository @Inject constructor(
    private val dao: BookDao,
) : BookRepository {

    override fun observeBooks(): Flow<List<Book>> =
        dao.observeBooks().map { list -> list.map { it.toBook() } }

    override fun observeBook(id: BookId): Flow<Book?> =
        dao.observeBook(id.value).map { it?.toBook() }

    override suspend fun markRead(id: BookId) = dao.markRead(id.value)

    override suspend fun markUnread(id: BookId) = dao.markUnread(id.value)

    override suspend fun remove(ids: List<BookId>) {
        val keys = ids.map { it.value }
        val entities = dao.getBooks(keys)
        dao.deleteByIds(keys)
        // Clean up the imported EPUB + extracted cover for each removed book.
        entities.forEach { entity ->
            runCatching { File(entity.storagePath).delete() }
            entity.coverImagePath?.let { runCatching { File(it).delete() } }
        }
    }

    override suspend fun updateMetadata(
        id: BookId,
        title: String,
        subtitle: String?,
        author: String,
        description: String?,
        coverImagePath: String?,
    ) = dao.updateMetadata(id.value, title, subtitle, author, description, coverImagePath)

    override suspend fun isDuplicate(sha256: String): Boolean = dao.existsByHash(sha256)

    override suspend fun insert(book: ImportedBook) = dao.insert(book.toEntity())
}
