package com.itexpert120.yomu.data.books

import androidx.room.withTransaction
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.ChapterReadEntity
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderTocItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which chapters of one book count as read, from its `chapter_reads` rows (read by reaching the end,
 * or marked by hand) and its `chapter_progress` rows (furthest progress per chapter). The one rule
 * ([isRead]) is shared by Book Details, Statistics and sync's chapter merge.
 */
class ChapterReadState private constructor(
    private val read: Set<String>,
    private val progress: Map<String, Float>,
) {
    fun isRead(chapterId: String): Boolean = chapterId in read

    /** Progress through the chapter (0..1): 1 once read, else the furthest reached, else 0. */
    fun percent(chapterId: String): Float = if (isRead(chapterId)) 1f else progress[chapterId]?.coerceIn(0f, 1f) ?: 0f

    val readCount: Int get() = read.size

    companion object {
        /** Chapter progress at or past this counts as having reached the chapter's end. */
        const val READ_THRESHOLD = 0.999f

        val Empty = ChapterReadState(emptySet(), emptyMap())

        /** The rule: a read row (reached or marked), or progress that reached the end. */
        fun isRead(hasReadRow: Boolean, progress: Float?): Boolean = hasReadRow || (progress ?: 0f) >= READ_THRESHOLD

        fun from(readRows: Collection<String>, progressRows: Map<String, Float>): ChapterReadState {
            val read = HashSet(readRows)
            progressRows.forEach { (chapterId, progress) -> if (isRead(false, progress)) read += chapterId }
            return ChapterReadState(read, progressRows)
        }
    }
}

/** Chapter read state: observing it, marking chapters, and recording the reading position into it. */
@Singleton
class ChapterReading @Inject constructor(
    private val dao: BookDao,
    private val database: YomuDatabase,
) {
    fun observe(book: BookId): Flow<ChapterReadState> = combine(
        dao.observeReadChapters(book.value),
        dao.observeChapterProgress(book.value),
    ) { read, progress -> ChapterReadState.from(read, progress.associate { it.chapterId to it.progress }) }

    /** Chapters read across the whole library, by the same rule as [observe]. */
    fun observeReadCount(): Flow<Int> = combine(dao.observeAllReadChapters(), dao.observeAllChapterProgress()) { read, progress ->
        val keys = read.mapTo(HashSet()) { it.bookId to it.chapterId }
        progress.forEach { if (ChapterReadState.isRead(false, it.progress)) keys += it.bookId to it.chapterId }
        keys.size
    }.distinctUntilChanged()

    /** Marks [chapterIds] read (by hand) or unread; unread also clears their progress. */
    suspend fun mark(book: BookId, chapterIds: List<String>, read: Boolean) {
        if (chapterIds.isEmpty()) return
        database.withTransaction {
            val now = System.currentTimeMillis()
            if (read) {
                dao.insertReadChapters(chapterIds.map { ChapterReadEntity(book.value, it) })
                chapterIds.forEach { dao.setChapterProgress(book.value, it, 1f, now, manuallyRead = true) }
            } else {
                dao.deleteReadChapters(book.value, chapterIds)
                chapterIds.forEach { dao.setChapterProgress(book.value, it, 0f, now, manuallyRead = false) }
            }
        }
    }

    /** "Mark to here": every jumpable chapter of [toc] up to and including [lastIndex] is marked read. */
    suspend fun markThrough(book: BookId, toc: List<ReaderTocItem>, lastIndex: Int) {
        val chapterIds = toc.take(lastIndex + 1).mapNotNull { if (it.locatorJson != null) it.id else null }.distinct()
        mark(book, chapterIds, read = true)
    }

    /**
     * Folds a saved reading position into the chapter rows. Must run inside the caller's
     * progress-save transaction.
     */
    internal suspend fun recordPosition(book: BookId, snapshot: ReadingProgressSnapshot, now: Long) {
        snapshot.completedChapterId?.let { completedChapterId ->
            (listOf(completedChapterId) + snapshot.completedSharedChapterIds).forEach {
                dao.setChapterProgress(book.value, it, 1f, now, manuallyRead = false)
                dao.insertReadChapters(listOf(ChapterReadEntity(book.value, it)))
            }
        }
        val chapterProgress = snapshot.chapterProgress?.coerceIn(0.0, 1.0)?.toFloat() ?: return
        (listOfNotNull(snapshot.chapterId) + snapshot.sharedChapterIds).forEach { chapterId ->
            recordChapterProgress(book.value, chapterId, chapterProgress, snapshot.currentHref, now)
        }
    }

    private suspend fun recordChapterProgress(bookId: String, chapterId: String, chapterProgress: Float, currentHref: String?, now: Long) {
        val reachedEnd = chapterProgress >= ChapterReadState.READ_THRESHOLD
        // Repair the v9 bug where entering a continuation resource marked the active logical
        // chapter read even though its real end had not been reached. A v10 automatic 100%
        // row is repairable too; explicit manual read overrides remain authoritative.
        if (
            !reachedEnd &&
            currentHref != null &&
            currentHref != chapterId.substringBefore('#') &&
            dao.getChapterProgress(bookId, chapterId)?.manuallyRead != true
        ) {
            dao.deleteReadChapters(bookId, listOf(chapterId))
            dao.setChapterProgress(bookId, chapterId, chapterProgress, now, manuallyRead = false)
        }
        dao.saveHighestChapterProgress(bookId, chapterId, chapterProgress, now)
        if (reachedEnd) dao.insertReadChapters(listOf(ChapterReadEntity(bookId, chapterId)))
    }
}
