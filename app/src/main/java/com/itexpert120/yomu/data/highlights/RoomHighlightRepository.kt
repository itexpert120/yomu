package com.itexpert120.yomu.data.highlights

import com.itexpert120.yomu.core.database.HighlightDao
import com.itexpert120.yomu.core.database.HighlightEntity
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderHighlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHighlightRepository @Inject constructor(
    private val dao: HighlightDao,
) : HighlightRepository {

    override fun observeForBook(bookId: BookId): Flow<List<ReaderHighlight>> =
        dao.observeForBook(bookId.value).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(
        bookId: BookId,
        locatorJson: String,
        text: String,
        colorArgb: Int,
    ): ReaderHighlight {
        val entity = HighlightEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId.value,
            locatorJson = locatorJson,
            text = text,
            colorArgb = colorArgb,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toModel()
    }

    override suspend fun updateColor(id: String, colorArgb: Int) = dao.updateColor(id, colorArgb)

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun HighlightEntity.toModel() = ReaderHighlight(
        id = id,
        locatorJson = locatorJson,
        text = text,
        colorArgb = colorArgb,
        createdAt = createdAt,
    )
}
