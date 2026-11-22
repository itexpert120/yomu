package com.itexpert120.yomu.data.books

import com.itexpert120.yomu.core.model.Book
import com.itexpert120.yomu.core.model.BookId
import kotlinx.coroutines.flow.Flow

/** What the reader needs to open a book: its file, last position, and title. */
data class ReadingTarget(
    val storagePath: String,
    val locatorJson: String?,
    val title: String,
)

data class ReadingProgressSnapshot(
    val locatorJson: String,
    // Layout-independent form of the same position (see ReaderSession.portableLocator), for sync.
    val portableLocatorJson: String? = null,
    val bookProgress: Double,
    val currentHref: String?,
    val chapterId: String?,
    val chapterProgress: Double?,
    // Sections that share [chapterId]'s start position and therefore its progress.
    val sharedChapterIds: List<String> = emptyList(),
    val completedChapterId: String? = null,
    val completedSharedChapterIds: List<String> = emptyList(),
    val completed: Boolean,
)

/**
 * Library data source backed by Room ([RoomBookRepository]): book rows and reading progress. The
 * import pipeline inserts via [insert]/[isDuplicate]; the UI reads via the observe* flows and
 * mutates via the rest. A book's table of contents lives in [PublicationIndex].
 */
interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    fun observeBook(id: BookId): Flow<Book?>

    /**
     * Marks the book Finished and its chapters read. The chapters come from [PublicationIndex]'s
     * stored copy; a book with nothing indexed is marked read without chapter rows (never parsed).
     */
    suspend fun markRead(id: BookId)
    suspend fun markUnread(id: BookId)
    suspend fun markRead(ids: List<BookId>) {
        ids.forEach { markRead(it) }
    }
    suspend fun markUnread(ids: List<BookId>) {
        ids.forEach { markUnread(it) }
    }
    suspend fun remove(ids: List<BookId>)
    suspend fun updateMetadata(
        id: BookId,
        title: String,
        subtitle: String?,
        author: String,
        description: String?,
        coverImagePath: String?,
    )

    suspend fun isDuplicate(sha256: String): Boolean

    /** The id of the already-imported book with this content hash, or null if none. */
    suspend fun findIdByHash(sha256: String): BookId?

    /** Inserts without replacing an existing hash. False means a concurrent duplicate won. */
    suspend fun insert(book: ImportedBook): Boolean

    suspend fun readingTarget(id: BookId): ReadingTarget?
    suspend fun saveProgress(id: BookId, snapshot: ReadingProgressSnapshot)
}
