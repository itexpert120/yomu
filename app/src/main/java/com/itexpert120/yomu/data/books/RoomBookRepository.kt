package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookTocEntity
import com.itexpert120.yomu.core.database.ChapterReadEntity
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderTocItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookRepository @Inject constructor(
    private val dao: BookDao,
    private val readerEngine: ReaderEngine,
) : BookRepository {

    private val tocJson = Json { ignoreUnknownKeys = true }

    // Process-lifetime scope for fire-and-forget background work (building the TOC right after import)
    // that must outlive the importing ViewModel, so it isn't cancelled when the user leaves the screen.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Process-lifetime cache of parsed TOCs, so navigating details <-> reader <-> details doesn't
    // re-read or reparse from disk. The TOC is immutable per book, so entries never go stale.
    private val tocMemory = java.util.concurrent.ConcurrentHashMap<String, List<ReaderTocItem>>()

    override fun observeBooks(): Flow<List<Book>> = dao.observeBooks().map { list -> list.map { it.toBook() } }

    override fun observeBook(id: BookId): Flow<Book?> = dao.observeBook(id.value).map { it?.toBook() }

    override suspend fun markRead(id: BookId) = dao.markRead(id.value, System.currentTimeMillis())

    override suspend fun markUnread(id: BookId) = dao.markUnread(id.value)

    override suspend fun remove(ids: List<BookId>) {
        val keys = ids.map { it.value }
        val entities = dao.getBooks(keys)
        dao.deleteByIds(keys)
        dao.deleteAllReadChapters(keys)
        dao.deleteReaderSettingsForBooks(keys)
        dao.deleteTocForBooks(keys)
        // Intentionally keep this book's reading_sessions: removing a book should not erase the
        // reading time/history it contributed to overall statistics. The session rows survive (the
        // stats history falls back to "Unknown book" once the book row is gone).
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

    override suspend fun findIdByHash(sha256: String): BookId? = dao.findIdByHash(sha256)?.let { BookId(it) }

    override suspend fun insert(book: ImportedBook) {
        dao.insert(book.toEntity())
        // Build + cache the TOC now in the background so the first Book Details / reader open is
        // instant instead of waiting on a full publication parse. tableOfContents() is idempotent and
        // cache-checked, so this is a no-op if the book happens to be opened before it finishes.
        backgroundScope.launch { runCatching { tableOfContents(BookId(book.id)) } }
    }

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
    }

    override suspend fun recentBooks(limit: Int): List<Book> = dao.getRecentBooks(limit).map { it.toBook() }

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

    override fun observeReadChapters(id: BookId): Flow<Set<String>> = dao.observeReadChapters(id.value).map { it.toSet() }

    override suspend fun setChaptersRead(id: BookId, chapterIds: List<String>, read: Boolean) {
        if (chapterIds.isEmpty()) return
        if (read) {
            dao.insertReadChapters(chapterIds.map { ChapterReadEntity(id.value, it) })
        } else {
            dao.deleteReadChapters(id.value, chapterIds)
        }
    }
}
