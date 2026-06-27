package com.itexpert120.yomu.data.bookmarks

import com.itexpert120.yomu.core.database.BookmarkDao
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderBookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {

    override fun observeForBook(bookId: BookId): Flow<List<ReaderBookmark>> =
        dao.observeForBook(bookId.value).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(
        bookId: BookId,
        locatorJson: String,
        href: String?,
        chapterTitle: String?,
        progression: Double,
    ): ReaderBookmark {
        val entity = BookmarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId.value,
            locatorJson = locatorJson,
            href = href,
            chapterTitle = chapterTitle,
            progression = progression,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toModel()
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun existsAt(bookId: BookId, href: String?, progression: Double): Boolean =
        dao.existsAt(bookId.value, href, progression)

    override suspend fun deleteAt(bookId: BookId, href: String?, progression: Double) =
        dao.deleteAt(bookId.value, href, progression)

    private fun BookmarkEntity.toModel() = ReaderBookmark(
        id = id,
        locatorJson = locatorJson,
        href = href,
        chapterTitle = chapterTitle,
        progression = progression,
        createdAt = createdAt,
    )
}
