package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookTocEntity
import com.itexpert120.yomu.core.database.ChapterReadEntity
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import android.content.Context
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookRepository @Inject constructor(
    private val dao: BookDao,
    private val readerEngine: ReaderEngine,
    @ApplicationContext private val context: Context,
) : BookRepository {

    private val tocJson = Json { ignoreUnknownKeys = true }

    // Process-lifetime cache of parsed TOCs, so navigating details <-> reader <-> details doesn't
    // re-read or reparse from disk. The TOC is immutable per book, so entries never go stale.
    private val tocMemory = java.util.concurrent.ConcurrentHashMap<String, List<ReaderTocItem>>()

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
        dao.deleteAllReadChapters(keys)
        dao.deleteReaderSettingsForBooks(keys)
        dao.deleteTocForBooks(keys)
        dao.deleteSessionsForBooks(keys)
        keys.forEach { tocMemory.remove(it) }
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

    override suspend fun findIdByHash(sha256: String): BookId? =
        dao.findIdByHash(sha256)?.let { BookId(it) }

    override suspend fun insert(book: ImportedBook) = dao.insert(book.toEntity())

    override suspend fun readingTarget(id: BookId): ReadingTarget? {
        val entity = dao.getBook(id.value) ?: return null
        return ReadingTarget(
            storagePath = entity.storagePath,
            locatorJson = entity.locatorJson,
            title = entity.title,
        )
    }

    override suspend fun saveProgress(id: BookId, locatorJson: String, totalProgression: Double) {
        dao.updateProgress(
            id = id.value,
            progress = totalProgression.toFloat(),
            totalProgression = totalProgression,
            locatorJson = locatorJson,
            lastOpenedAt = System.currentTimeMillis(),
        )
        // Keep the "Continue reading" home-screen widget in sync with the latest position.
        WidgetUpdater.refreshContinueReading(context)
    }

    override suspend fun continueReadingBook(): Book? =
        (dao.getContinueReadingBook() ?: dao.getMostRecentBook())?.toBook()

    override fun cachedTableOfContents(id: BookId): List<ReaderTocItem>? = tocMemory[id.value]

    override suspend fun tableOfContents(id: BookId): List<ReaderTocItem> {
        tocMemory[id.value]?.let { return it }
        dao.getCachedToc(id.value)?.let { cached ->
            runCatching { tocJson.decodeFromString<List<ReaderTocItem>>(cached) }
                .getOrNull()
                ?.let {
                    tocMemory[id.value] = it
                    return it
                }
        }
        val entity = dao.getBook(id.value) ?: return emptyList()
        val items = readerEngine.tableOfContents(entity.storagePath)
        if (items.isNotEmpty()) {
            tocMemory[id.value] = items
            runCatching { dao.upsertToc(BookTocEntity(id.value, tocJson.encodeToString(items))) }
        }
        return items
    }

    override fun observeReadChapters(id: BookId): Flow<Set<String>> =
        dao.observeReadChapters(id.value).map { it.toSet() }

    override suspend fun setChaptersRead(id: BookId, chapterIds: List<String>, read: Boolean) {
        if (chapterIds.isEmpty()) return
        if (read) {
            dao.insertReadChapters(chapterIds.map { ChapterReadEntity(id.value, it) })
        } else {
            dao.deleteReadChapters(id.value, chapterIds)
        }
    }
}
