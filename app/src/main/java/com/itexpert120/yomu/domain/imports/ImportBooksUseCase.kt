package com.itexpert120.yomu.domain.imports

import android.net.Uri
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

/**
 * SAF import pipeline (docs "Import Pattern"): copy into app storage, hash, dedupe by sha256,
 * extract metadata/cover via Readium, insert into the library. In-process for now.
 */
@Singleton
class ImportBooksUseCase @Inject constructor(
    private val fileStorage: FileStorage,
    private val extractor: ReadiumMetadataExtractor,
    private val repository: BookRepository,
) {
    suspend fun import(uris: List<Uri>): ImportSummary = withContext(Dispatchers.IO) {
        var imported = 0
        var duplicates = 0
        var failed = 0

        for (uri in uris) {
            val bookId = UUID.randomUUID().toString()
            val copied = runCatching { fileStorage.copyEpub(bookId, uri) }.getOrNull()
            if (copied == null) {
                failed++
                continue
            }
            if (repository.isDuplicate(copied.sha256)) {
                copied.file.delete()
                duplicates++
                continue
            }

            val metadata = runCatching { extractor.extract(copied.file) }.getOrNull()
            val displayName = fileStorage.displayName(uri)
            val coverPath = metadata?.cover?.let { fileStorage.saveCover(bookId, it) }

            repository.insert(
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
                    storagePath = copied.file.absolutePath,
                    originalUri = uri.toString(),
                    originalDisplayName = displayName,
                    sha256 = copied.sha256,
                    fileSizeBytes = copied.sizeBytes,
                    addedAt = System.currentTimeMillis(),
                ),
            )
            imported++
        }

        ImportSummary(imported, duplicates, failed)
    }
}

private fun String.removeEpubSuffix(): String = removeSuffix(".epub").ifBlank { this }
