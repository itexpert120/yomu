package com.itexpert120.yomu.data.books

import androidx.room.withTransaction
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookmarkDao
import com.itexpert120.yomu.core.database.CollectionDao
import com.itexpert120.yomu.core.database.HighlightDao
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.storage.BookFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookRepository @Inject constructor(
    private val dao: BookDao,
    private val database: YomuDatabase,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val collectionDao: CollectionDao,
    private val chapterReading: ChapterReading,
    private val bookFiles: BookFiles,
    private val publications: PublicationIndex,
) : BookRepository {

    private val deletionMutex = Mutex()

    override fun observeBooks(): Flow<List<Book>> = dao.observeBooks().map { list -> list.map { it.toBook() } }

    override fun observeBook(id: BookId): Flow<Book?> = dao.observeBook(id.value).map { it?.toBook() }

    override suspend fun markRead(id: BookId) = markRead(listOf(id))

    override suspend fun markRead(ids: List<BookId>) {
        // Chapters come from what the index already has; a book it knows nothing about is marked
        // read without chapter rows rather than parsing its EPUB here.
        val chapters = ids.distinct().associateWith { id ->
            publications.publicationCache(id)?.toc.orEmpty()
                .filter { it.locatorJson != null }.map { it.id }.distinct()
        }
        database.withTransaction {
            chapters.forEach { (id, chapterIds) ->
                check(dao.getBook(id.value) != null) { "Book no longer exists" }
                dao.markRead(id.value, System.currentTimeMillis())
                chapterReading.mark(id, chapterIds, read = true)
            }
        }
    }

    override suspend fun markUnread(id: BookId) = markUnread(listOf(id))

    override suspend fun markUnread(ids: List<BookId>) {
        database.withTransaction {
            ids.forEach { dao.markUnread(it.value) }
            dao.deleteAllReadChapters(ids.map { it.value })
            dao.deleteAllChapterProgress(ids.map { it.value })
        }
    }

    override suspend fun remove(ids: List<BookId>): Unit = deletionMutex.withLock {
        withContext(Dispatchers.IO) { removeBooks(ids) }
    }

    private suspend fun removeBooks(ids: List<BookId>) {
        val keys = ids.map { it.value }
        if (keys.isEmpty()) return
        val entities = dao.getBooks(keys)
        bookFiles.deleting(entities.flatMap { listOfNotNull(it.storagePath, it.coverImagePath) }) {
            database.withTransaction {
                highlightDao.deleteForBooks(keys)
                bookmarkDao.deleteForBooks(keys)
                dao.deleteAllReadChapters(keys)
                dao.deleteAllChapterProgress(keys)
                dao.deleteReaderSettingsForBooks(keys)
                dao.deleteTocForBooks(keys)
                collectionDao.deleteMembershipsForBooks(keys)
                dao.deleteByIds(keys)
            }
        }
        publications.forget(ids)
    }

    override suspend fun updateMetadata(
        id: BookId,
        title: String,
        subtitle: String?,
        author: String,
        description: String?,
        coverImagePath: String?,
    ) = dao.updateMetadata(id.value, title, subtitle, author, description, coverImagePath, System.currentTimeMillis())

    override suspend fun isDuplicate(sha256: String): Boolean = dao.existsByHash(sha256)

    override suspend fun findIdByHash(sha256: String): BookId? = dao.findIdByHash(sha256)?.let { BookId(it) }

    override suspend fun insert(book: ImportedBook): Boolean = dao.insert(book.toEntity()) != -1L

    override suspend fun readingTarget(id: BookId): ReadingTarget? {
        val row = dao.getReadingTarget(id.value) ?: return null
        return ReadingTarget(storagePath = row.storagePath, locatorJson = row.locatorJson, title = row.title)
    }

    override suspend fun saveProgress(id: BookId, snapshot: ReadingProgressSnapshot) {
        val now = System.currentTimeMillis()
        val canonical = if (snapshot.completed) 1.0 else snapshot.bookProgress.coerceIn(0.0, 0.999)
        database.withTransaction {
            // Before the update: it compares against the progress stored so far.
            dao.clearUnpinnedStatusOnProgress(id.value, canonical.toFloat(), now)
            dao.updateProgress(
                id = id.value,
                progress = canonical.toFloat(),
                totalProgression = canonical,
                locatorJson = snapshot.locatorJson,
                syncLocatorJson = snapshot.portableLocatorJson,
                currentChapterId = snapshot.chapterId,
                completed = snapshot.completed,
                lastOpenedAt = now,
            )
            chapterReading.recordPosition(id, snapshot, now)
        }
    }
}
