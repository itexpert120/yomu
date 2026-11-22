package com.itexpert120.yomu.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private storage for downloaded fonts, plus what an import asks of a SAF document (is it an
 * EPUB, its display name). A book's own files live in [BookFiles].
 */
@Singleton
class FileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // Created on first use (always off the main thread), so constructing this during the first
    // composition does no disk I/O.
    private val fontsDir: File by lazy { File(context.filesDir, "fonts").apply { mkdirs() } }

    /** Writes raw [bytes] (a downloaded font file) to app storage under [name], returning its path. */
    suspend fun saveFont(name: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = File(fontsDir, name)
        target.writeBytes(bytes)
        target.absolutePath
    }

    /** Deletes a stored font file by absolute path (best-effort; ignores files outside the fonts dir). */
    suspend fun deleteFont(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile == fontsDir && file.exists()) check(file.delete()) { "Couldn't remove font" }
        Unit
    }

    fun canImportAsEpub(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        val name = displayName(uri)?.lowercase()
        return mime == BookFiles.EPUB_MIME || name?.endsWith(".epub") == true
    }

    /** The original display name from the SAF document, used as a title fallback. */
    fun displayName(uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
