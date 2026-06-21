package com.itexpert120.yomu.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.itexpert120.yomu.data.books.BookRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Immutable snapshot the "Continue reading" widget renders. [cover] is a decoded, size-limited
 * bitmap (or null to fall back to a gradient/placeholder). [Empty] represents the no-books state.
 */
data class ContinueReadingState(
    val bookId: String?,
    val title: String,
    val author: String,
    val progress: Float,
    val progressLabel: String,
    val cover: Bitmap?,
) {
    val isEmpty: Boolean get() = bookId == null

    companion object {
        val Empty = ContinueReadingState(
            bookId = null,
            title = "",
            author = "",
            progress = 0f,
            progressLabel = "",
            cover = null,
        )
    }
}

/** Hilt bridge so the widget/receiver (not @AndroidEntryPoint) can reach app singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bookRepository(): BookRepository
}

internal fun widgetBookRepository(context: Context): BookRepository =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetEntryPoint::class.java,
    ).bookRepository()

/**
 * Loads the "Continue reading" snapshot off the main thread. Decodes the cover at a small,
 * memory-safe size — home-screen widget bitmaps must stay well under the RemoteViews limit.
 */
internal suspend fun loadContinueReadingState(context: Context): ContinueReadingState =
    withContext(Dispatchers.IO) {
        val book = widgetBookRepository(context).continueReadingBook()
            ?: return@withContext ContinueReadingState.Empty
        ContinueReadingState(
            bookId = book.id.value,
            title = book.title,
            author = book.author,
            progress = book.progress.coerceIn(0f, 1f),
            progressLabel = book.remainingLabel,
            cover = book.coverImagePath?.let { decodeCover(it) },
        )
    }

/** Decodes a cover capped to [maxDimensionPx] on its longest side, or null if it can't be read. */
private fun decodeCover(path: String, maxDimensionPx: Int = 360): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > maxDimensionPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    }.getOrNull()?.let { downscaleIfNeeded(it, maxDimensionPx) }
}

/** inSampleSize only halves; clamp the final bitmap to the exact ceiling to bound widget memory. */
private fun downscaleIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
    val longest = max(bitmap.width, bitmap.height)
    if (longest <= maxDimensionPx) return bitmap
    val scale = maxDimensionPx.toFloat() / longest
    val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, w, h, true)
}
