package com.itexpert120.yomu.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import com.itexpert120.yomu.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The whole lifecycle of a book's files in app storage: the EPUB copy and its cover. New files are
 * staged ([stage], [stageCover]) and discarded unless the library row that points at them commits;
 * deletions are staged around the database commit ([deleting]); and [reconcile] repairs whatever a
 * process death left behind, against the committed rows.
 */
@Singleton
class BookFiles internal constructor(
    filesDir: File,
    private val openInput: (Uri) -> InputStream?,
    scope: CoroutineScope,
    private val rename: (File, File) -> Boolean = File::renameTo,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : this(context.filesDir, { context.contentResolver.openInputStream(it) }, scope)

    // Created on first write (always off the main thread), so constructing this does no disk I/O.
    private val epubsDir = File(filesDir, EPUBS_DIR)
    private val coversDir = File(filesDir, COVERS_DIR)

    // Leftover `.partial` copies from an interrupted import. Copies wait for this sweep so it can
    // never delete a copy that is still being written.
    private val partialSweep: Job = scope.launch(Dispatchers.IO) {
        sequenceOf(epubsDir, coversDir).forEach { directory ->
            directory.listFiles()
                ?.filter { it.name.endsWith(PARTIAL) }
                ?.forEach { runCatching { it.delete() } }
        }
    }

    /** An imported EPUB copied into app storage, not yet owned by a library row. */
    inner class StagedBook internal constructor(val file: File, val sha256: String, val sizeBytes: Long) {
        var coverPath: String? = null
            private set

        /**
         * Saves [bitmap] as this book's cover. Covers are shown at most a few hundred dp tall, so
         * they are stored downscaled as lossy WebP (older full-size PNG covers keep their path).
         */
        suspend fun saveCover(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
            val target = File(coversDir.created(), "${file.nameWithoutExtension}.webp")
            val scaled = if (bitmap.height > MAX_COVER_HEIGHT_PX) {
                val width = (bitmap.width.toLong() * MAX_COVER_HEIGHT_PX / bitmap.height).toInt().coerceAtLeast(1)
                bitmap.scale(width, MAX_COVER_HEIGHT_PX, filter = true)
            } else {
                bitmap
            }
            try {
                target.outputStream().use { out -> check(scaled.compress(coverFormat(), COVER_QUALITY, out)) { "Couldn't encode cover" } }
            } catch (failure: Throwable) {
                target.delete()
                throw failure
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
            target.absolutePath.also { coverPath = it }
        }

        /** Deletes the copy and its cover: the import did not commit (failed, or a duplicate). */
        suspend fun discard() = withContext(Dispatchers.IO) {
            file.delete()
            coverPath?.let { File(it).delete() }
            Unit
        }
    }

    /** A user-picked cover copied into app storage, not yet saved on the book. */
    inner class StagedCover internal constructor(val path: String) {
        suspend fun discard() = deleteCover(path)
    }

    /** Copies and validates the EPUB at [uri] as the file of the new book [bookId]. */
    suspend fun stage(uri: Uri, bookId: String): StagedBook = stage(bookId) { openInput(uri) }

    internal suspend fun stage(bookId: String, open: () -> InputStream?): StagedBook = withContext(Dispatchers.IO) {
        partialSweep.join()
        val directory = epubsDir.created()
        val target = File(directory, "$bookId.epub")
        val partial = File(directory, "$bookId$PARTIAL")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            var total = 0L
            open()?.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        check(total <= MAX_EPUB_BYTES) { "EPUB is larger than 500 MB" }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Unable to open the EPUB")
            check(total > 0L) { "EPUB is empty" }
            validateEpubArchive(partial)
            check(partial.renameTo(target)) { "Unable to finalize imported EPUB" }
            StagedBook(target, digest.digest().joinToString("") { "%02x".format(it) }, total)
        } catch (failure: Throwable) {
            partial.delete()
            target.delete()
            throw failure
        }
    }

    /**
     * Copies a user-picked image as a new cover for [bookId]. The name is fresh ([stamp]) so Coil
     * doesn't serve a cached old cover for the same path; [reconcile] sweeps it if never saved.
     */
    suspend fun stageCover(bookId: String, uri: Uri, stamp: Long = System.currentTimeMillis()): StagedCover = stageCover(bookId, stamp) { openInput(uri) }

    internal suspend fun stageCover(bookId: String, stamp: Long, open: () -> InputStream?): StagedCover = withContext(Dispatchers.IO) {
        val target = File(coversDir.created(), "$bookId-$stamp.png")
        try {
            open()?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                ?: error("Unable to open the image")
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
        StagedCover(target.absolutePath)
    }

    /** Deletes a cover that is no longer the book's (ignores anything outside the covers folder). */
    suspend fun deleteCover(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile == coversDir && file.exists()) {
            check(file.delete()) { "Couldn't remove cover" }
        }
    }

    /**
     * Deletes the files at [paths] around [commit] (the database delete): they are first renamed
     * aside, restored if staging or [commit] fails, and unlinked only once it succeeded. A process
     * death in between is settled by [reconcile] from the committed rows.
     */
    suspend fun <T> deleting(paths: Collection<String>, commit: suspend () -> T): T {
        val staged = withContext(Dispatchers.IO) { stageForDeletion(paths.map(::File)) }
        val result = try {
            commit()
        } catch (failure: Throwable) {
            withContext(Dispatchers.IO) { restore(staged) }
            throw failure
        }
        // The database is already committed, so an unlink failure must not turn a successful
        // deletion into a failure; reconcile clears the residue next time.
        withContext(Dispatchers.IO) { staged.forEach { (_, trash) -> runCatching { trash.delete() } } }
        return result
    }

    /**
     * Settles files against the committed library ([livePaths]: every row's EPUB and cover path):
     * a file staged for deletion is restored if its row is live and removed otherwise, and covers
     * no row points at (an edited cover never saved, a cover of an import that never committed)
     * are removed. Runs when the database opens, before any new file can be written.
     */
    fun reconcile(livePaths: Set<String>) {
        val liveNames = livePaths.mapTo(HashSet()) { File(it).name }
        sequenceOf(epubsDir, coversDir).forEach { directory ->
            directory.listFiles()?.filter { it.isFile }?.forEach file@{ trash ->
                val name = StagedName.matchEntire(trash.name)?.groupValues?.get(1) ?: return@file
                val original = File(directory, name)
                if (original.absolutePath in livePaths || name in liveNames) {
                    check(original.exists() || trash.renameTo(original)) {
                        "Couldn't restore library file ${original.name}; staged copy retained"
                    }
                } else {
                    trash.delete()
                }
            }
        }
        coversDir.listFiles()
            ?.filter { it.isFile && it.name !in liveNames && !it.name.endsWith(DELETING) && !it.name.endsWith(PARTIAL) }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun stageForDeletion(files: List<File>): List<Pair<File, File>> {
        val staged = mutableListOf<Pair<File, File>>()
        try {
            files.distinctBy { it.absolutePath }.filter { it.exists() }.forEach { original ->
                val trash = File(original.parentFile, ".${original.name}.${UUID.randomUUID()}$DELETING")
                check(rename(original, trash)) { "Couldn't stage ${original.name} for deletion" }
                staged += original to trash
            }
            return staged
        } catch (failure: Throwable) {
            restore(staged)
            throw failure
        }
    }

    private fun restore(staged: List<Pair<File, File>>) {
        // Failed restores keep their reversible name for reconcile.
        staged.asReversed().forEach { (original, trash) ->
            if (!original.exists() && trash.exists()) trash.renameTo(original)
        }
    }

    private fun File.created(): File = apply { mkdirs() }

    private fun validateEpubArchive(file: File) {
        ZipFile(file).use { zip ->
            val mimetype = zip.getEntry("mimetype") ?: error("Not an EPUB archive")
            val declared = zip.getInputStream(mimetype).bufferedReader().use { it.readText() }.trim()
            check(declared == EPUB_MIME) { "Not an EPUB archive" }
            check(zip.getEntry("META-INF/container.xml") != null) { "EPUB container is missing" }
        }
    }

    @Suppress("DEPRECATION")
    private fun coverFormat(): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        // Before API 30 plain WEBP is lossy for any quality below 100.
        Bitmap.CompressFormat.WEBP
    }

    internal companion object {
        const val EPUB_MIME = "application/epub+zip"
        private const val EPUBS_DIR = "epubs"
        private const val COVERS_DIR = "covers"
        private const val PARTIAL = ".partial"
        private const val DELETING = ".deleting"
        private const val MAX_EPUB_BYTES = 500L * 1024 * 1024
        private const val MAX_COVER_HEIGHT_PX = 900
        private const val COVER_QUALITY = 85

        // Staged names keep the original name so reconcile can decide restore vs delete.
        private val StagedName = Regex("^\\.(.+)\\.[0-9a-fA-F-]{36}\\.deleting$")
    }
}
