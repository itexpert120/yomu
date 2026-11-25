package com.itexpert120.yomu.data.annotations

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.itexpert120.yomu.core.database.BookDao
import com.itexpert120.yomu.core.database.BookmarkDao
import com.itexpert120.yomu.core.database.BookmarkEntity
import com.itexpert120.yomu.core.database.HighlightDao
import com.itexpert120.yomu.core.database.YomuDatabase
import com.itexpert120.yomu.core.database.anchor
import com.itexpert120.yomu.core.model.BookId
import com.itexpert120.yomu.core.reader.ReaderBookmark
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.books.PublicationIndex
import com.itexpert120.yomu.data.books.TocLoad
import com.itexpert120.yomu.data.highlights.toReaderHighlight
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** An export ready to save or share: the [text], a suggested [fileName] and its [mimeType]. */
data class AnnotationExport(val text: String, val fileName: String, val mimeType: String)

/**
 * Exports a book's highlights, notes and bookmarks (Markdown, HTML or Open Reader JSON) and imports
 * them back (Open Reader JSON). Imports
 * merge without duplicates; their writes reach sync like any other highlight or bookmark edit,
 * since sync diffs the tables.
 */
@Singleton
class BookAnnotations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: YomuDatabase,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val bookmarkDao: BookmarkDao,
    private val publications: PublicationIndex,
) {

    /** Everything to export for [bookId] in reading order, or null if the book is gone. */
    suspend fun document(bookId: BookId): AnnotationDocument? = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId.value) ?: return@withContext null
        val (toc, readingOrder) = outline(bookId)
        AnnotationDocument.of(
            bookTitle = book.title,
            author = book.author.takeIf { it.isNotBlank() },
            sha256 = book.sha256,
            exportedAt = System.currentTimeMillis(),
            highlights = highlightDao.forBook(bookId.value).map { it.toReaderHighlight() },
            bookmarks = bookmarkDao.positionsForBook(bookId.value).map { it.toReaderBookmark() },
            toc = toc,
            readingOrder = readingOrder,
        )
    }

    suspend fun export(bookId: BookId, format: AnnotationFormat, zone: ZoneId = ZoneId.systemDefault()): AnnotationExport? {
        val document = document(bookId) ?: return null
        return AnnotationExport(AnnotationRenderer.render(document, format, zone), exportFileName(document.bookTitle, format), format.mimeType)
    }

    /**
     * Writes [export] to the app cache and returns a `content://` Uri for it, readable by whoever
     * an intent grants it to. Served by the diagnostics FileProvider (`cache/shared/`).
     */
    suspend fun cacheUri(export: AnnotationExport): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared/annotations").apply { mkdirs() }
        val file = File(dir, export.fileName).apply { writeText(export.text) }
        FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
    }

    /** A share-sheet chooser carrying [bookId]'s annotations as a [format] attachment; null if the book is gone. */
    suspend fun shareIntent(bookId: BookId, format: AnnotationFormat): Intent? {
        val export = export(bookId, format) ?: return null
        val uri = cacheUri(export)
        val title = export.fileName.substringBeforeLast('.')
        val send = Intent(Intent.ACTION_SEND)
            .setType(export.mimeType)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read grant through the chooser (for its preview and the target).
        send.clipData = ClipData.newRawUri(title, uri)
        return Intent.createChooser(send, "Share annotations")
    }

    /** Reads and checks an annotation file (e.g. from ACTION_OPEN_DOCUMENT) against [bookId]. */
    suspend fun checkImport(bookId: BookId, uri: Uri): AnnotationImportCheck {
        val text = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        } ?: return AnnotationImportCheck.NotAnnotationFile
        return checkImport(bookId, text)
    }

    suspend fun checkImport(bookId: BookId, text: String): AnnotationImportCheck = when (val parsed = AnnotationFileCodec.parse(text)) {
        is AnnotationFileParse.Parsed -> check(bookId, parsed.file)
        AnnotationFileParse.NotAnnotationFile -> AnnotationImportCheck.NotAnnotationFile
        is AnnotationFileParse.NewerVersion -> AnnotationImportCheck.NewerVersion(parsed.version)
    }

    /** Merges [file] into [bookId] in one transaction; null if the book is gone. */
    suspend fun applyImport(bookId: BookId, file: AnnotationFile): AnnotationImportResult? = withContext(Dispatchers.IO) {
        database.withTransaction {
            bookDao.getBook(bookId.value) ?: return@withTransaction null
            val plan = plan(bookId, file)
            highlightDao.upsertAll(plan.insertHighlights + plan.updateHighlights)
            plan.insertBookmarks.forEach { bookmarkDao.insert(it) }
            AnnotationImportResult(plan.insertHighlights.size, plan.updateHighlights.size, plan.insertBookmarks.size)
        }
    }

    private suspend fun check(bookId: BookId, file: AnnotationFile): AnnotationImportCheck = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId.value) ?: return@withContext AnnotationImportCheck.BookMissing
        AnnotationImportCheck.Ready(AnnotationMerge.preview(file, book.sha256, plan(bookId, file)), file)
    }

    private suspend fun plan(bookId: BookId, file: AnnotationFile) = AnnotationMerge.plan(
        bookId = bookId.value,
        file = file,
        existingHighlights = highlightDao.forBook(bookId.value),
        existingBookmarks = bookmarkDao.positionsForBook(bookId.value),
        newId = { UUID.randomUUID().toString() },
        now = System.currentTimeMillis(),
    )

    /**
     * The book's TOC and reading order (resource hrefs in spine order — the cached resource weights
     * are keyed in that order). Parses the EPUB only if the TOC was never cached.
     */
    private suspend fun outline(bookId: BookId): Pair<List<ReaderTocItem>, List<String>> {
        publications.publicationCache(bookId)?.let { return it.toc to it.resourceWeights.keys.toList() }
        val toc = (publications.observeToc(bookId).first { it is TocLoad.Ready } as TocLoad.Ready).items
        return toc to emptyList()
    }
}

private fun BookmarkEntity.toReaderBookmark(): ReaderBookmark {
    val anchor = anchor
    return ReaderBookmark(id, anchor.locatorJson, anchor.anchor, chapterTitle, anchor.progression, createdAt)
}

/** `<title> - annotations.<ext>`, with characters file systems and share targets reject replaced. */
internal fun exportFileName(bookTitle: String, format: AnnotationFormat): String {
    val safe = bookTitle.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").replace(Regex("""\s+"""), " ").trim().take(80).trim()
    return "${safe.ifEmpty { "Book" }} - annotations.${format.extension}"
}
