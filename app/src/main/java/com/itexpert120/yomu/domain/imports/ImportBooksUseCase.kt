package com.itexpert120.yomu.domain.imports

import android.net.Uri
import com.itexpert120.yomu.core.storage.BookFiles
import com.itexpert120.yomu.core.storage.FileStorage
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.books.ImportedBook
import com.itexpert120.yomu.data.reader.readium.ReadiumMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(val imported: Int, val duplicates: Int, val failed: Int) {
    val total: Int get() = imported + duplicates + failed
}

/** Outcome of importing a single EPUB: the resolved book id plus whether it already existed. */
sealed interface ImportResult {
    data class Imported(val bookId: String) : ImportResult
    data class Duplicate(val bookId: String) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * SAF import pipeline (docs "Import Pattern"): copy into app storage, hash, dedupe by sha256,
 * extract metadata/cover via Readium, insert into the library. In-process for now.
 */
@Singleton
class ImportBooksUseCase @Inject constructor(
    private val fileStorage: FileStorage,
    private val bookFiles: BookFiles,
    private val extractor: ReadiumMetadataExtractor,
    private val repository: BookRepository,
) {
    suspend fun import(uris: List<Uri>): ImportSummary = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        var failed = 0

        for (uri in uris) {
            when (runCatching { importOne(uri) }.getOrElse { ImportResult.Failed("Import failed") }) {
                is ImportResult.Imported -> imported++
                is ImportResult.Duplicate -> duplicates++
                is ImportResult.Failed -> failed++
            }
        }

        ImportSummary(imported, duplicates, failed)
    }

    /**
     * Imports a single EPUB (e.g. an external "Open with"/share). Returns the resolved book id so
     * the caller can open it; on a duplicate it resolves to the existing library entry instead of
     * creating a second copy.
     */
    suspend fun importSingle(uri: Uri): ImportResult = withContext(Dispatchers.IO) { importOne(uri) }

    private suspend fun importOne(uri: Uri): ImportResult {
        if (!fileStorage.canImportAsEpub(uri)) {
            return ImportResult.Failed("Choose a valid EPUB file")
        }
        val bookId = UUID.randomUUID().toString()
        val staged = runCatching { bookFiles.stage(uri, bookId) }.getOrNull()
            ?: return ImportResult.Failed("The file is empty, invalid, or too large")
        // Only a committed library row keeps the staged files; every other outcome discards them.
        val result = runCatching { commit(uri, bookId, staged) }.getOrElse { ImportResult.Failed("The EPUB couldn't be imported") }
        if (result !is ImportResult.Imported) runCatching { staged.discard() }
        return result
    }

    private suspend fun commit(uri: Uri, bookId: String, staged: BookFiles.StagedBook): ImportResult {
        if (repository.isDuplicate(staged.sha256)) {
            // Resolve to the existing entry so an external open can still land on the book.
            return repository.findIdByHash(staged.sha256)?.let { ImportResult.Duplicate(it.value) }
                ?: ImportResult.Failed("Duplicate couldn't be resolved")
        }
        val metadata = runCatching { extractor.extract(staged.file) }.getOrNull()
        val displayName = fileStorage.displayName(uri)
        val coverPath = metadata?.cover?.let { staged.saveCover(it) }
        val inserted = repository.insert(
            ImportedBook(
                id = bookId,
                title = metadata?.title?.takeIf { it.isNotBlank() }
                    ?: displayName?.removeEpubSuffix()
                    ?: "Untitled",
                subtitle = null,
                author = metadata?.author ?: "Unknown author",
                description = metadata?.description,
                language = metadata?.language,
                publisher = metadata?.publisher,
                series = null,
                coverImagePath = coverPath,
                storagePath = staged.file.absolutePath,
                originalUri = uri.toString(),
                originalDisplayName = displayName,
                sha256 = staged.sha256,
                fileSizeBytes = staged.sizeBytes,
                addedAt = System.currentTimeMillis(),
                tableOfContents = metadata?.tableOfContents,
                publicationCache = metadata?.publicationCache,
            ),
        )
        if (inserted) return ImportResult.Imported(bookId)
        return repository.findIdByHash(staged.sha256)?.let { ImportResult.Duplicate(it.value) }
            ?: ImportResult.Failed("Duplicate couldn't be resolved")
    }
}

private fun String.removeEpubSuffix(): String = removeSuffix(".epub").ifBlank { this }
