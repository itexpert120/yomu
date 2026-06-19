package com.itexpert120.yomu.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns app-private storage for imported EPUBs and extracted covers. Files are copied in from SAF
 * URIs so the app has stable, deletable, hashable local copies (docs/android-build-patterns.md).
 */
@Singleton
class FileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val epubsDir: File = File(context.filesDir, "epubs").apply { mkdirs() }
    private val coversDir: File = File(context.filesDir, "covers").apply { mkdirs() }

    data class CopiedFile(val file: File, val sha256: String, val sizeBytes: Long)

    /** Copies the EPUB at [uri] into app storage under [bookId], returning its path + sha256. */
    suspend fun copyEpub(bookId: String, uri: Uri): CopiedFile = withContext(Dispatchers.IO) {
        val target = File(epubsDir, "$bookId.epub")
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("Unable to open input stream for $uri")
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        CopiedFile(target, sha, target.length())
    }

    /** Persists [bitmap] as a PNG cover for [bookId], returning the absolute path. */
    suspend fun saveCover(bookId: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val target = File(coversDir, "$bookId.png")
        target.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        target.absolutePath
    }

    /**
     * Copies a user-picked image as a new cover for [bookId]. Uses a fresh filename so Coil
     * doesn't serve a cached old cover for the same path.
     */
    suspend fun saveCoverFromUri(bookId: String, uri: Uri, stamp: Long): String =
        withContext(Dispatchers.IO) {
            val target = File(coversDir, "$bookId-$stamp.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open input stream for $uri")
            target.absolutePath
        }

    /** The original display name from the SAF document, used as a title fallback. */
    fun displayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }
}
