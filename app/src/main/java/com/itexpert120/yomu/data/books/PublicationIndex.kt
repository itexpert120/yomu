package com.itexpert120.yomu.data.books

import androidx.room.withTransaction
import com.itexpert120.yomu.app.di.ApplicationScope
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookTocEntity
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import com.itexpert120.yomu.core.reader.ReaderPublicationCacheCodec
import com.itexpert120.yomu.core.reader.ReaderTocItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A book's table of contents as the index has it: still loading, or ready (possibly empty). */
sealed interface TocLoad {
    data object Loading : TocLoad

    data class Ready(val items: List<ReaderTocItem>) : TocLoad
}

/**
 * Each book's publication index: its table of contents and resource weights
 * ([ReaderPublicationCache]), immutable for the book's lifetime.
 *
 * Served from memory (a process-lifetime LRU), then the persisted copy, and only then by asking the
 * [ReaderEngine] to parse the EPUB; concurrent loads of one book share a single parse, and whatever
 * a parse produces is persisted for the next open. Only [observeToc] ever parses.
 *
 * Deleting a book calls [forget] once its rows are gone: from then on the index answers empty for
 * it, so a TOC load that was racing the delete cannot resurrect it in memory or storage.
 */
@Singleton
class PublicationIndex internal constructor(
    private val store: PublicationStore,
    private val engine: ReaderEngine,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    @Inject constructor(
        dao: BookDao,
        database: YomuDatabase,
        engine: ReaderEngine,
        @ApplicationScope scope: CoroutineScope,
    ) : this(RoomPublicationStore(dao, database), engine, scope, Dispatchers.IO)

    private val loads = SingleFlight<String, List<ReaderTocItem>>(scope)
    private val memory = Collections.synchronizedMap(
        object : LinkedHashMap<String, ReaderPublicationCache>(MEMORY_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReaderPublicationCache>?): Boolean = size > MEMORY_SIZE
        },
    )
    private val forgotten = ConcurrentHashMap.newKeySet<String>()

    /**
     * The book's table of contents. Starts [TocLoad.Ready] when it is already in memory (so a repeat
     * open shows no loading state); otherwise starts [TocLoad.Loading] and loads in the background,
     * parsing the EPUB if nothing is stored. A book that can't be read, or no longer exists, is
     * Ready with no items.
     */
    fun observeToc(book: BookId): StateFlow<TocLoad> {
        primed(book)?.let { return MutableStateFlow(it).asStateFlow() }
        val state = MutableStateFlow<TocLoad>(TocLoad.Loading)
        scope.launch {
            val items = try {
                toc(book)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
            state.value = TocLoad.Ready(items)
        }
        return state.asStateFlow()
    }

    /** What the index already has for the book (memory, then storage); never parses the EPUB. */
    suspend fun publicationCache(book: BookId): ReaderPublicationCache? {
        if (book.value in forgotten) return null
        memory[book.value]?.let { return it }
        val stored = withContext(io) { store.read(book) } ?: return null
        return stored.takeIf { keep(book, it) }
    }

    /** Records what the reader or importer learned about the book; ignored once it's forgotten or gone. */
    suspend fun remember(book: BookId, cache: ReaderPublicationCache) {
        persist(book, cache)
    }

    /** Called after the books are deleted: they read as empty from now on. */
    fun forget(books: Collection<BookId>) {
        books.forEach {
            forgotten += it.value
            memory.remove(it.value)
        }
    }

    private fun primed(book: BookId): TocLoad? {
        if (book.value in forgotten) return TocLoad.Ready(emptyList())
        return memory[book.value]?.let { TocLoad.Ready(it.toc) }
    }

    private suspend fun toc(book: BookId): List<ReaderTocItem> {
        publicationCache(book)?.let { return it.toc }
        if (book.value in forgotten) return emptyList()
        return loads.run(book.value) {
            publicationCache(book)?.let { return@run it.toc }
            val path = withContext(io) { store.storagePath(book) } ?: return@run emptyList()
            val items = try {
                withContext(io) { engine.tableOfContents(path) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: return@run emptyList()
            if (persist(book, ReaderPublicationCache(toc = items))) items else emptyList()
        }
    }

    private suspend fun persist(book: BookId, cache: ReaderPublicationCache): Boolean {
        if (book.value in forgotten) return false
        if (!withContext(io) { store.write(book, cache) }) return false
        return keep(book, cache)
    }

    // Memory only takes entries for live books; the check-then-put is under the map's lock so a
    // concurrent forget() can't be overtaken.
    private fun keep(book: BookId, cache: ReaderPublicationCache): Boolean = synchronized(memory) {
        if (book.value in forgotten) {
            false
        } else {
            memory[book.value] = cache
            true
        }
    }

    private companion object {
        const val MEMORY_SIZE = 32
    }
}

/** Where [PublicationIndex] persists publication caches: Room in the app, in-memory in tests. */
internal interface PublicationStore {
    suspend fun read(book: BookId): ReaderPublicationCache?

    /** Stores [cache] only while the book exists, atomically with that check; false when it's gone. */
    suspend fun write(book: BookId, cache: ReaderPublicationCache): Boolean

    /** The book's EPUB on disk, or null when the book doesn't exist. */
    suspend fun storagePath(book: BookId): String?
}

internal class RoomPublicationStore(
    private val dao: BookDao,
    private val database: YomuDatabase,
) : PublicationStore {
    override suspend fun read(book: BookId): ReaderPublicationCache? {
        val row = dao.getCachedToc(book.value) ?: return null
        return ReaderPublicationCacheCodec.decode(row.json, row.resourceWeightsJson)
    }

    override suspend fun write(book: BookId, cache: ReaderPublicationCache): Boolean {
        val entity = BookTocEntity(
            bookId = book.value,
            json = ReaderPublicationCacheCodec.encodeToc(cache.toc),
            resourceWeightsJson = cache.resourceWeights.takeIf { it.isNotEmpty() }
                ?.let(ReaderPublicationCacheCodec::encodeWeights),
        )
        return database.withTransaction {
            if (dao.getBook(book.value) == null) {
                false
            } else {
                dao.upsertToc(entity)
                true
            }
        }
    }

    override suspend fun storagePath(book: BookId): String? = dao.getBook(book.value)?.storagePath
}
