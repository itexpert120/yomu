package com.itexpert120.yomu.data.bookmarks

import com.itexpert120.yomu.core.database.BookmarkDao
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.anchor
import com.itexpert120.yomu.core.database.toBookmarkEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.BookmarkAnchor
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

    override suspend fun setBookmarked(
        bookId: BookId,
        at: BookmarkAnchor,
        chapterTitle: String?,
        bookmarked: Boolean,
    ) {
        val bookmark = at.toBookmarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId.value,
            chapterTitle = chapterTitle,
            createdAt = System.currentTimeMillis(),
        )
        dao.setBookmarked(bookmark, bookmarked)
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun BookmarkEntity.toModel(): ReaderBookmark {
        val anchor = anchor
        return ReaderBookmark(
            id = id,
            locatorJson = anchor.locatorJson,
            href = anchor.anchor,
            chapterTitle = chapterTitle,
            progression = anchor.progression,
            createdAt = createdAt,
        )
    }
}
