package com.itexpert120.yomu.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import com.itexpert120.yomu.core.model.HeatmapDay
import com.itexpert120.yomu.data.books.BookRepository
import com.itexpert120.yomu.data.stats.StatsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

// Yomu's dark, reader-like palette approximated with Glance primitives (Glance can't read the
// app's CompositionLocal theme). Sourced from core/designsystem DarkYomuColors. Shared by both
// home-screen widgets so they stay visually consistent.
internal object WidgetColors {
    val background = Color(0xFF171815)
    val backgroundRaised = Color(0xFF1D1F1B)
    val textPrimary = Color(0xFFF4F4EF)
    val textSecondary = Color(0xFFBFC2B8)
    val textMuted = Color(0xFF858A7E)
    val accent = Color(0xFFB8D88F)
    val track = Color(0xFF2C2F29)
    val coverFallback = Color(0xFF0C0D0B)

    /** Heatmap / bar intensity ramp (0 = none … 4 = busiest). Index 0 is the empty-cell track. */
    val intensity = arrayOf(
        Color(0xFF24271F), // 0 — no reading
        Color(0xFF3C4A2E),
        Color(0xFF5E7A3F),
        Color(0xFF8BB45F),
        Color(0xFFB8D88F), // 4 — accent
    )
}

// ---------------------------------------------------------------------------------------------
// Hilt bridge
// ---------------------------------------------------------------------------------------------

/** Hilt bridge so the widgets/receivers (not @AndroidEntryPoint) can reach app singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bookRepository(): BookRepository
    fun statsRepository(): StatsRepository
}

private fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

// ---------------------------------------------------------------------------------------------
// Library widget data
// ---------------------------------------------------------------------------------------------

/** One book tile in the library widget: enough to render a cover + title and deep-link a tap. */
data class WidgetBook(
    val id: String,
    val title: String,
    val author: String,
    val cover: Bitmap?,
)

/**
 * Loads up to [limit] most-recently-opened books for the library widget, decoding each cover at a
 * small, RemoteViews-safe size. Runs off the main thread (provideGlance is suspend).
 */
internal suspend fun loadLibraryBooks(context: Context, limit: Int): List<WidgetBook> =
    withContext(Dispatchers.IO) {
        widgetEntryPoint(context).bookRepository().recentBooks(limit).map { book ->
            WidgetBook(
                id = book.id.value,
                title = book.title,
                author = book.author,
                cover = book.coverImagePath?.let { decodeCover(it) },
            )
        }
    }

// ---------------------------------------------------------------------------------------------
// Activity widget data
// ---------------------------------------------------------------------------------------------

/**
 * Immutable snapshot for the reading-activity widget: headline figures plus the recent daily
 * series (oldest → newest) used to draw the bar chart / mini heatmap. [days] always carries the
 * full heatmap window; small widget sizes render only its tail.
 */
data class WidgetActivity(
    val todaySeconds: Long,
    val weekSeconds: Long,
    val currentStreakDays: Int,
    val days: List<HeatmapDay>,
) {
    val isEmpty: Boolean get() = days.none { it.seconds > 0L } && currentStreakDays == 0
}

/** Loads the reading-activity snapshot off the main thread from the stats repository. */
internal suspend fun loadActivity(context: Context): WidgetActivity =
    withContext(Dispatchers.IO) {
        val stats = widgetEntryPoint(context).statsRepository()
        val statsSnapshot = stats.stats.first()
        val days = stats.heatmap.first()
        val today = days.lastOrNull()?.seconds ?: 0L
        WidgetActivity(
            todaySeconds = today,
            weekSeconds = statsSnapshot.secondsLast7Days,
            currentStreakDays = statsSnapshot.currentStreakDays,
            days = days,
        )
    }

/** Human-readable reading time, compact for a widget (e.g. "0m", "45m", "2h 5m"). */
internal fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "0m"
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

// ---------------------------------------------------------------------------------------------
// Cover decoding (shared)
// ---------------------------------------------------------------------------------------------

/** Decodes a cover capped to [maxDimensionPx] on its longest side, or null if it can't be read. */
internal fun decodeCover(path: String, maxDimensionPx: Int = 320): Bitmap? {
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
