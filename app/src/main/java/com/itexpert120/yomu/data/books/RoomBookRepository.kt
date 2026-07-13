package com.itexpert120.yomu.data.books

import androidx.room.withTransaction
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookTocEntity
import com.itexpert120.yomu.core.database.BookmarkDao
import com.itexpert120.yomu.core.database.ChapterReadEntity
import com.itexpert120.yomu.core.database.HighlightDao
import com.itexpert120.yomu.core.database.YomuDatabase
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
import java.util.Collections
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBookRepository @Inject constructor(
    private val dao: BookDao,
    private val database: YomuDatabase,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val readerEngine: ReaderEngine,
) : BookRepository {

    private val tocJson = Json { ignoreUnknownKeys = true }

    // Process-lifetime scope for fire-and-forget background work (building the TOC right after import)
    // that must outlive the importing ViewModel, so it isn't cancelled when the user leaves the screen.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Process-lifetime cache of parsed TOCs, so navigating details <-> reader <-> details doesn't
    // re-read or reparse from disk. The TOC is immutable per book, so entries never go stale.
    private val tocMemory = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<ReaderTocItem>>(TOC_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<ReaderTocItem>>?,
            ): Boolean = size > TOC_CACHE_SIZE
        },
    )
    private val deletedBookIds = ConcurrentHashMap.newKeySet<String>()

    override fun observeBooks(): Flow<List<Book>> = dao.observeBooks().map { list -> list.map { it.toBook() } }

    override fun observeBook(id: BookId): Flow<Book?> = dao.observeBook(id.value).map { it?.toBook() }

    override suspend fun markRead(id: BookId) = dao.markRead(id.value, System.currentTimeMillis())

    override suspend fun markUnread(id: BookId) = dao.markUnread(id.value)

    override suspend fun remove(ids: List<BookId>) {
        val keys = ids.map { it.value }
        if (keys.isEmpty()) return
        val entities = dao.getBooks(keys)
        deletedBookIds.addAll(keys)
        keys.forEach { tocMemory.remove(it) }
        val staged = stageForDeletion(
            entities.flatMap { entity ->
                buildList {
                    add(File(entity.storagePath))
                    entity.coverImagePath?.let { add(File(it)) }
                }
            },
        )
        try {
            database.withTransaction {
                highlightDao.deleteForBooks(keys)
                bookmarkDao.deleteForBooks(keys)
                dao.deleteAllReadChapters(keys)
                dao.deleteReaderSettingsForBooks(keys)
                dao.deleteTocForBooks(keys)
                dao.deleteByIds(keys)
            }
        } catch (failure: Throwable) {
            staged.asReversed().forEach { (original, trash) ->
                if (trash.exists()) trash.renameTo(original)
            }
            deletedBookIds.removeAll(keys.toSet())
            throw failure
        }
        // The database is already committed, so an unlink failure must not turn a successful
        // deletion into a misleading UI failure. FileStorage clears any .deleting residue on launch.
        staged.forEach { (_, trash) -> runCatching { trash.delete() } }
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

    override suspend fun insert(book: ImportedBook): Boolean {
        if (dao.insert(book.toEntity()) == -1L) return false
        deletedBookIds.remove(book.id)
        // Build + cache the TOC now in the background so the first Book Details / reader open is
        // instant instead of waiting on a full publication parse. tableOfContents() is idempotent and
        // cache-checked, so this is a no-op if the book happens to be opened before it finishes.
        backgroundScope.launch { runCatching { tableOfContents(BookId(book.id)) } }
        return true
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
        if (id.value in deletedBookIds) return emptyList()
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
        if (items.isNotEmpty() && id.value !in deletedBookIds && dao.getBook(id.value) != null) {
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

    private fun stageForDeletion(files: List<File>): List<Pair<File, File>> {
        val staged = mutableListOf<Pair<File, File>>()
        try {
            files.distinctBy { it.absolutePath }.filter { it.exists() }.forEach { original ->
                val trash = File(original.parentFile, ".${original.name}.${UUID.randomUUID()}.deleting")
                check(original.renameTo(trash)) { "Couldn't stage ${original.name} for deletion" }
                staged += original to trash
            }
            return staged
        } catch (failure: Throwable) {
            staged.asReversed().forEach { (original, trash) -> trash.renameTo(original) }
            throw failure
        }
    }

    private companion object {
        const val TOC_CACHE_SIZE = 32
    }
}
