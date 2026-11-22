package com.itexpert120.yomu.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.itexpert120.yomu.core.database.BookCollectionEntity
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookEntity
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.ChapterProgressEntity
import com.itexpert120.yomu.core.database.ChapterReadEntity
import com.itexpert120.yomu.core.database.CollectionDao
import com.itexpert120.yomu.core.database.CollectionEntity
import com.itexpert120.yomu.core.database.HighlightEntity
import com.itexpert120.yomu.core.database.ReaderSettingsEntity
import com.itexpert120.yomu.core.database.SyncDao
import com.itexpert120.yomu.core.model.CustomFontRef
import com.itexpert120.yomu.data.settings.AppSettingsRepository
import com.itexpert120.yomu.data.settings.LibraryPrefsRepository
import com.itexpert120.yomu.data.settings.ReaderSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.Proxy

/**
 * One device's local data for record-kind tests: the library tables in memory behind the real DAO
 * interfaces, the real settings repositories over in-memory preferences, and every production kind.
 */
internal class FakeSyncWorld {
    val books = mutableMapOf<String, BookEntity>()
    val chapterReads = mutableSetOf<ChapterReadEntity>()
    val chapterProgress = mutableMapOf<Pair<String, String>, ChapterProgressEntity>()
    val bookmarks = mutableMapOf<String, BookmarkEntity>()
    val highlights = mutableMapOf<String, HighlightEntity>()
    val readerSettingsRows = MutableStateFlow<Map<String, ReaderSettingsEntity>>(emptyMap())
    val collections = mutableMapOf<String, CollectionEntity>()
    val memberships = mutableSetOf<BookCollectionEntity>()
    val fonts = FakeSyncFonts()

    val store = InMemorySyncStore()
    private val dataStore = InMemoryPreferences()
    val appSettings = AppSettingsRepository(dataStore)
    val libraryPrefs = LibraryPrefsRepository(dataStore)
    val readerSettings = ReaderSettingsRepository(dataStore, FakeBookDao())

    private val syncDao = FakeSyncDao()
    private val collectionDao = FakeCollectionDao()

    val kinds = RecordKinds(
        setOf(
            MetaKind(syncDao),
            ProgressKind(syncDao, store),
            ChapterKind(syncDao, store),
            BookmarkKind(syncDao, store),
            HighlightKind(syncDao, store),
            BookSettingsKind(syncDao, store, readerSettings, fonts),
            BookCollectionsKind(syncDao, collectionDao),
            SettingsKind(appSettings, libraryPrefs, readerSettings, fonts),
            ThemeKind(readerSettings),
            FontKind(fonts, store),
            CollectionKind(collectionDao),
        ),
        store,
    )

    fun addBook(id: String, sha256: String, edit: BookEntity.() -> BookEntity = { this }): BookEntity {
        val book = BookEntity(
            id = id, title = "Title", subtitle = null, author = "Author", description = null, language = null,
            publisher = null, series = null, coverImagePath = null, storagePath = "/books/$id.epub", originalUri = null,
            originalDisplayName = null, sha256 = sha256, fileSizeBytes = 1, progress = 0f, totalProgression = null,
            locatorJson = null, currentChapterId = null, addedAt = 1, lastOpenedAt = 0, startedAt = 0, finishedAt = 0,
        ).edit()
        books[id] = book
        store.books[id] = sha256
        return book
    }

    private inner class FakeSyncDao : SyncDao by unsupported() {
        override suspend fun books() = this@FakeSyncWorld.books.values.toList()
        override suspend fun bookByHash(sha256: String) = this@FakeSyncWorld.books.values.firstOrNull { it.sha256 == sha256 }
        override suspend fun shaForBook(bookId: String) = this@FakeSyncWorld.books[bookId]?.sha256
        override suspend fun bookmarks() = this@FakeSyncWorld.bookmarks.values.toList()
        override suspend fun highlights() = this@FakeSyncWorld.highlights.values.toList()
        override suspend fun chapterReads() = this@FakeSyncWorld.chapterReads.toList()
        override suspend fun chapterProgress() = this@FakeSyncWorld.chapterProgress.values.toList()
        override suspend fun readerSettings() = readerSettingsRows.value.values.toList()
        override suspend fun isChapterRead(bookId: String, chapterId: String) = ChapterReadEntity(bookId, chapterId) in this@FakeSyncWorld.chapterReads
        override suspend fun chapterProgressFor(bookId: String, chapterId: String) = this@FakeSyncWorld.chapterProgress[bookId to chapterId]

        override suspend fun applyMetadata(sha256: String, title: String, subtitle: String?, author: String, description: String?, editedAt: Long) = updateBook(sha256) { copy(title = title, subtitle = subtitle, author = author, description = description, metadataEditedAt = editedAt) }

        override suspend fun applyProgress(
            sha256: String,
            progress: Float,
            totalProgression: Double?,
            locatorJson: String?,
            chapterId: String?,
            lastOpenedAt: Long,
            startedAt: Long,
            finishedAt: Long,
        ) = updateBook(sha256) {
            copy(
                progress = progress,
                totalProgression = totalProgression,
                locatorJson = locatorJson,
                syncLocatorJson = locatorJson,
                currentChapterId = chapterId,
                lastOpenedAt = lastOpenedAt,
                startedAt = startedAt,
                finishedAt = finishedAt,
            )
        }

        override suspend fun applyChapter(bookId: String, chapterId: String, read: Boolean, progress: ChapterProgressEntity?) {
            val row = ChapterReadEntity(bookId, chapterId)
            if (read) chapterReads += row else chapterReads -= row
            if (progress != null) chapterProgress[bookId to chapterId] = progress else chapterProgress.remove(bookId to chapterId)
        }

        override suspend fun upsertBookmark(bookmark: BookmarkEntity) {
            bookmarks[bookmark.id] = bookmark
        }

        override suspend fun deleteBookmark(id: String) {
            bookmarks.remove(id)
        }

        override suspend fun upsertHighlight(highlight: HighlightEntity) {
            highlights[highlight.id] = highlight
        }

        override suspend fun deleteHighlight(id: String) {
            highlights.remove(id)
        }

        private fun updateBook(sha256: String, change: BookEntity.() -> BookEntity) {
            val book = this@FakeSyncWorld.books.values.firstOrNull { it.sha256 == sha256 } ?: return
            this@FakeSyncWorld.books[book.id] = book.change()
        }
    }

    private inner class FakeCollectionDao : CollectionDao by unsupported() {
        override suspend fun collections() = this@FakeSyncWorld.collections.values.toList()
        override suspend fun memberships() = this@FakeSyncWorld.memberships.toList()

        override suspend fun upsertCollection(collection: CollectionEntity) {
            this@FakeSyncWorld.collections[collection.id] = collection
        }

        override suspend fun deleteCollection(id: String, editedAt: Long): List<String> {
            this@FakeSyncWorld.collections.remove(id)
            val books = this@FakeSyncWorld.memberships.filter { it.collectionId == id }.map { it.bookId }
            this@FakeSyncWorld.memberships.removeAll { it.collectionId == id }
            return books
        }

        override suspend fun replaceShelf(bookId: String, collectionIds: Collection<String>, status: String?, editedAt: Long) {
            this@FakeSyncWorld.memberships.removeAll { it.bookId == bookId }
            this@FakeSyncWorld.memberships += collectionIds.map { BookCollectionEntity(bookId, it) }
            val book = this@FakeSyncWorld.books.getValue(bookId)
            this@FakeSyncWorld.books[bookId] = book.copy(manualStatus = status, shelfEditedAt = editedAt)
        }
    }

    private inner class FakeBookDao : BookDao by unsupported() {
        override fun observeReaderSettings(bookId: String): Flow<ReaderSettingsEntity?> = readerSettingsRows.map { it[bookId] }
        override suspend fun getReaderSettings(bookId: String) = readerSettingsRows.value[bookId]
        override suspend fun upsertReaderSettings(entity: ReaderSettingsEntity) = readerSettingsRows.update { it + (entity.bookId to entity) }
        override suspend fun deleteReaderSettings(bookId: String) = readerSettingsRows.update { it - bookId }
    }
}

private class InMemoryPreferences : DataStore<Preferences> {
    private val mutex = Mutex()
    override val data = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
        transform(data.value).also { data.value = it }
    }
}

internal class FakeSyncFonts : SyncFonts {
    val families = mutableSetOf<String>()

    /** When false, installs fail as if offline. */
    var online = true

    override suspend fun installed() = families.associateWith { CustomFontRef(it, "/fonts/$it.woff2") }

    override suspend fun install(family: String): Boolean {
        if (online) families += family
        return online
    }

    override suspend fun remove(family: String) {
        families -= family
    }
}

/** An implementation whose every member not overridden by the fake throws. */
private inline fun <reified T : Any> unsupported(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
    throw UnsupportedOperationException("${T::class.simpleName}.${method.name} is not faked")
} as T
