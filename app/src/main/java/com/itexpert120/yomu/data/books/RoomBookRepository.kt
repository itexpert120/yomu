package com.itexpert120.yomu.data.books

import androidx.room.withTransaction
import com.itexpert120.yomu.app.di.ApplicationScope
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    @ApplicationScope applicationScope: CoroutineScope,
) : BookRepository {

    private val tocJson = Json { ignoreUnknownKeys = true }

    private val tocLoads = SingleFlight<String, List<ReaderTocItem>>(applicationScope)

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

    override suspend fun markUnread(id: BookId) {
        database.withTransaction {
            dao.markUnread(id.value)
            dao.deleteAllReadChapters(listOf(id.value))
            dao.deleteAllChapterProgress(listOf(id.value))
        }
    }

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
                dao.deleteAllChapterProgress(keys)
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
        val cachedToc = book.tableOfContents?.let { items ->
            BookTocEntity(book.id, tocJson.encodeToString(items))
        }
        val inserted = database.withTransaction {
            if (dao.insert(book.toEntity()) == -1L) {
                false
            } else {
                cachedToc?.let { dao.upsertToc(it) }
                true
            }
        }
        if (!inserted) return false
        deletedBookIds.remove(book.id)
        book.tableOfContents?.let { tocMemory[book.id] = it }
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

    override suspend fun saveProgress(id: BookId, snapshot: ReadingProgressSnapshot) {
        val now = System.currentTimeMillis()
        val canonical = if (snapshot.completed) 1.0 else snapshot.bookProgress.coerceIn(0.0, 0.999)
        database.withTransaction {
            dao.updateProgress(
                id = id.value,
                progress = canonical.toFloat(),
                totalProgression = canonical,
                locatorJson = snapshot.locatorJson,
                currentChapterId = snapshot.chapterId,
                completed = snapshot.completed,
                lastOpenedAt = now,
            )
            snapshot.completedChapterId?.let { completedChapterId ->
                dao.setChapterProgress(
                    id.value,
                    completedChapterId,
                    1f,
                    now,
                    manuallyRead = false,
                )
                dao.insertReadChapters(
                    listOf(ChapterReadEntity(id.value, completedChapterId)),
                )
            }
            val chapterId = snapshot.chapterId
            val chapterProgress = snapshot.chapterProgress?.coerceIn(0.0, 1.0)?.toFloat()
            if (chapterId != null && chapterProgress != null) {
                val oldProgress = dao.getChapterProgress(id.value, chapterId)
                // Repair the v9 bug where entering a continuation resource marked the active logical
                // chapter read even though its real end had not been reached. A v10 automatic 100%
                // row is repairable too; explicit manual read overrides remain authoritative.
                if (
                    chapterProgress < 0.999f &&
                    snapshot.currentHref != null &&
                    snapshot.currentHref != chapterId.substringBefore('#') &&
                    oldProgress?.manuallyRead != true
                ) {
                    dao.deleteReadChapters(id.value, listOf(chapterId))
                    dao.setChapterProgress(
                        id.value,
                        chapterId,
                        chapterProgress,
                        now,
                        manuallyRead = false,
                    )
                }
                dao.saveHighestChapterProgress(id.value, chapterId, chapterProgress, now)
                if (chapterProgress >= 0.999f) {
                    dao.insertReadChapters(listOf(ChapterReadEntity(id.value, chapterId)))
                }
            }
        }
    }

    override suspend fun recentBooks(limit: Int): List<Book> = dao.getRecentBooks(limit).map { it.toBook() }

    override fun cachedTableOfContents(id: BookId): List<ReaderTocItem>? = tocMemory[id.value]

    override suspend fun tableOfContents(id: BookId): List<ReaderTocItem> {
        if (id.value in deletedBookIds) return emptyList()
        tocMemory[id.value]?.let { return it }
        readCachedTableOfContents(id.value)?.let { return it }
        return tocLoads.run(id.value) {
            if (id.value in deletedBookIds) return@run emptyList()
            tocMemory[id.value]?.let { return@run it }
            readCachedTableOfContents(id.value)?.let { return@run it }
            val entity = dao.getBook(id.value) ?: return@run emptyList()
            val items = runCatching { readerEngine.tableOfContents(entity.storagePath) }
                .getOrNull()
                ?: return@run emptyList()
            if (persistTableOfContents(id.value, items)) items else emptyList()
        }
    }

    override suspend fun cacheTableOfContents(id: BookId, items: List<ReaderTocItem>) {
        if (id.value in deletedBookIds) return
        tocMemory[id.value]?.let { return }
        readCachedTableOfContents(id.value)?.let { return }
        persistTableOfContents(id.value, items)
    }

    private suspend fun readCachedTableOfContents(bookId: String): List<ReaderTocItem>? {
        val cached = dao.getCachedToc(bookId) ?: return null
        return runCatching { tocJson.decodeFromString<List<ReaderTocItem>>(cached) }
            .getOrNull()
            ?.also { tocMemory[bookId] = it }
    }

    private suspend fun persistTableOfContents(
        bookId: String,
        items: List<ReaderTocItem>,
    ): Boolean {
        if (bookId in deletedBookIds) return false
        val entity = BookTocEntity(bookId, tocJson.encodeToString(items))
        val persisted = database.withTransaction {
            if (bookId in deletedBookIds || dao.getBook(bookId) == null) {
                false
            } else {
                dao.upsertToc(entity)
                true
            }
        }
        if (persisted && bookId !in deletedBookIds) tocMemory[bookId] = items
        return persisted
    }

    override fun observeReadChapters(id: BookId): Flow<Set<String>> = dao.observeReadChapters(id.value).map { it.toSet() }

    override fun observeChapterProgress(id: BookId): Flow<Map<String, Float>> = dao.observeChapterProgress(id.value).map { rows -> rows.associate { it.chapterId to it.progress } }

    override suspend fun setChaptersRead(id: BookId, chapterIds: List<String>, read: Boolean) {
        if (chapterIds.isEmpty()) return
        database.withTransaction {
            val now = System.currentTimeMillis()
            if (read) {
                dao.insertReadChapters(chapterIds.map { ChapterReadEntity(id.value, it) })
                chapterIds.forEach {
                    dao.setChapterProgress(id.value, it, 1f, now, manuallyRead = true)
                }
            } else {
                dao.deleteReadChapters(id.value, chapterIds)
                chapterIds.forEach {
                    dao.setChapterProgress(id.value, it, 0f, now, manuallyRead = false)
                }
            }
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
