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

    override fun observeForBook(bookId: BookId): Flow<List<ReaderBookmark>> = dao.observeForBook(bookId.value).map { rows -> rows.map { it.toModel() } }

    override suspend fun toggle(
        bookId: BookId,
        locatorJson: String,
        href: String?,
        chapterTitle: String?,
        progression: Double?,
    ): Boolean {
        val entity = BookmarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId.value,
            locatorJson = locatorJson,
            href = href,
            chapterTitle = chapterTitle,
            // -1 is an explicit "unknown whole-book position" sentinel. Identity then falls back
            // to the exact locator JSON instead of incorrectly treating it as the start of the book.
            progression = progression ?: UNKNOWN_PROGRESSION,
            createdAt = System.currentTimeMillis(),
        )
        return dao.toggle(entity)
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun BookmarkEntity.toModel() = ReaderBookmark(
        id = id,
        locatorJson = locatorJson,
        href = href,
        chapterTitle = chapterTitle,
        progression = progression.takeIf { it >= 0 },
        createdAt = createdAt,
    )

    private companion object {
        const val UNKNOWN_PROGRESSION = -1.0
    }
}
