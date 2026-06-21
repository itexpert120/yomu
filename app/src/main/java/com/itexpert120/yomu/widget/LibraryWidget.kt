package com.itexpert120.yomu.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.itexpert120.yomu.R

/**
 * Resizable library widget: the most-recently-opened books as a cover grid for fast launching.
 * Tapping a cover deep-links straight into the reader for that book (reusing the existing
 * widget → MainActivity → nav-host path). The grid adapts to the placed size — more columns as it
 * grows wide, more rows as it grows tall — via [SizeMode.Responsive] + [LocalSize].
 */
class LibraryWidget : GlanceAppWidget() {

    // Exact: recompose for the actual placed size so the grid scales continuously to any size.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Cap the query at the largest grid we could draw; loaded off the main thread.
        val books = loadLibraryBooks(context, limit = MAX_BOOKS)
        provideContent {
            LibraryContent(context, books)
        }
    }

    private companion object {
        const val COVER_TARGET_DP = 86   // approx tile width; columns scale with widget width
        const val ROW_HEIGHT_DP = 96     // approx tile height; rows scale with widget height
        const val MAX_COLS = 6
        const val MAX_ROWS = 6
        const val MAX_BOOKS = MAX_COLS * MAX_ROWS
    }

    @Composable
    private fun LibraryContent(context: Context, books: List<WidgetBook>) {
        val size = LocalSize.current
        val root = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(20.dp)
            .padding(10.dp)

        if (books.isEmpty()) {
            EmptyState(context, root)
            return
        }

        // Dynamic grid: columns scale with width, rows with height.
        val columns = ((size.width.value - 20) / COVER_TARGET_DP).toInt().coerceIn(1, MAX_COLS)
        val rows = ((size.height.value - 20) / ROW_HEIGHT_DP).toInt().coerceIn(1, MAX_ROWS)
        val capacity = (columns * rows).coerceAtMost(books.size)
        val shown = books.take(capacity)

        Column(modifier = root) {
            shown.chunked(columns).forEachIndexed { rowIndex, rowBooks ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    rowBooks.forEachIndexed { colIndex, book ->
                        BookTile(
                            context = context,
                            book = book,
                            modifier = GlanceModifier.defaultWeight().fillMaxSize(),
                        )
                        if (colIndex < rowBooks.lastIndex) Spacer(GlanceModifier.width(8.dp))
                    }
                    // Pad the final short row so tiles keep a consistent width.
                    repeat(columns - rowBooks.size) {
                        Spacer(GlanceModifier.width(8.dp))
                        Box(modifier = GlanceModifier.defaultWeight()) {}
                    }
                }
                if (rowIndex < shown.chunked(columns).lastIndex) Spacer(GlanceModifier.height(8.dp))
            }
        }
    }

    @Composable
    private fun BookTile(context: Context, book: WidgetBook, modifier: GlanceModifier) {
        Column(
            modifier = modifier.clickable(
                actionStartActivity(WidgetDeepLink.launchIntent(context, book.id)),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val coverModifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .cornerRadius(8.dp)
            val cover = book.cover
            if (cover != null) {
                Image(
                    provider = ImageProvider(cover),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier,
                )
            } else {
                Box(
                    modifier = coverModifier.background(WidgetColors.coverFallback),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_yomu_mark),
                        contentDescription = null,
                        modifier = GlanceModifier.width(24.dp).height(24.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyState(context: Context, modifier: GlanceModifier) {
        Column(
            modifier = modifier.clickable(
                actionStartActivity(WidgetDeepLink.launchIntent(context, null)),
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_yomu_mark),
                contentDescription = null,
                modifier = GlanceModifier.width(32.dp).height(32.dp),
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = "Your library is empty",
                style = TextStyle(
                    color = ColorProvider(WidgetColors.textPrimary),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "Tap to import a book",
                style = TextStyle(color = ColorProvider(WidgetColors.textMuted), fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

class LibraryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LibraryWidget()
}
