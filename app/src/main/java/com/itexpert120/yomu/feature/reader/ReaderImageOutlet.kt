package com.itexpert120.yomu.feature.reader

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.itexpert120.yomu.core.reader.ReaderImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Where a picture from the book can go: the gallery, or another app. */
interface ImageOutlet {
    /** Saves [image] to Pictures / Open Reader as [name]; false when it couldn't be. */
    suspend fun save(image: ReaderImage, name: String): Boolean

    /** Offers [image] to other apps (the share sheet). */
    suspend fun share(image: ReaderImage, name: String)
}

class AndroidImageOutlet @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImageOutlet {
    override suspend fun save(image: ReaderImage, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.${extension(image.mimeType)}")
                put(MediaStore.Images.Media.MIME_TYPE, image.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Open Reader")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = checkNotNull(resolver.insert(collection, values))
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { it.write(image.bytes) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
            } catch (failure: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw failure
            }
        }.isSuccess
    }

    override suspend fun share(image: ReaderImage, name: String) {
        val uri = withContext(Dispatchers.IO) {
            // Served by the app's FileProvider for files under cache/shared/.
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "$name.${extension(image.mimeType)}")
            file.writeBytes(image.bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = image.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // The grant reaches the app picked in the chooser through the clip.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    private fun extension(mimeType: String) = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "png"
    }
}
