package com.itexpert120.yomu.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.itexpert120.yomu.R
import kotlin.math.roundToInt

// Yomu's dark, reader-like palette approximated with Glance primitives (Glance can't read the
// app's CompositionLocal theme). Sourced from core/designsystem DarkYomuColors.
private object WidgetColors {
    val background = Color(0xFF171815)
    val backgroundRaised = Color(0xFF1D1F1B)
    val textPrimary = Color(0xFFF4F4EF)
    val textSecondary = Color(0xFFBFC2B8)
    val textMuted = Color(0xFF858A7E)
    val accent = Color(0xFFB8D88F)
    val track = Color(0xFF2C2F29)
    val coverFallback = Color(0xFF0C0D0B)
}

/**
 * "Continue reading" home-screen widget: the most-recently-read book's cover, title, author and
 * progress. Tapping it deep-links into the reader for that book. Renders an empty state when the
 * library has nothing in progress.
 */
class ContinueReadingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        // Data is fetched off the main thread before composition starts (provideGlance is suspend).
        val state = loadContinueReadingState(context)
        provideContent {
            GlanceTheme {
                WidgetContent(context, state)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context, state: ContinueReadingState) {
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(WidgetColors.background)
        .cornerRadius(20.dp)
        .padding(14.dp)

    if (state.isEmpty) {
        EmptyState(context, rootModifier)
        return
    }

    Row(
        modifier = rootModifier.clickable(
            actionStartActivity(WidgetDeepLink.launchIntent(context, state.bookId)),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(state)
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Continue reading",
                style = TextStyle(
                    color = ColorProvider(WidgetColors.accent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = state.title,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.textPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            if (state.author.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = state.author,
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.textSecondary),
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            ProgressBar(state.progress)
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = state.progressLabel,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.textMuted),
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Cover(state: ContinueReadingState) {
    val coverModifier = GlanceModifier
        .size(width = 64.dp, height = 96.dp)
        .cornerRadius(8.dp)
    val cover = state.cover
    if (cover != null) {
        Image(
            provider = ImageProvider(cover),
            contentDescription = state.title,
            modifier = coverModifier,
        )
    } else {
        // Fallback tile with the app mark when no extracted cover exists.
        Box(
            modifier = coverModifier.background(WidgetColors.coverFallback),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_yomu_mark),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val fraction = progress.coerceIn(0f, 1f)
    // Glance lacks fractional widths, so approximate the bar with equal-weight segment cells and
    // colour the filled count — a real proportional read-out without measuring widget width.
    val cells = 12
    val filled = (fraction * cells).roundToInt().coerceIn(0, cells)
    Row(modifier = GlanceModifier.fillMaxWidth().height(6.dp)) {
        repeat(cells) { index ->
            val color = if (index < filled) WidgetColors.accent else WidgetColors.track
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(color),
            ) {}
            if (index < cells - 1) Spacer(GlanceModifier.width(2.dp))
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
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "No book in progress",
            style = TextStyle(
                color = ColorProvider(WidgetColors.textPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = "Tap to open Yomu",
            style = TextStyle(
                color = ColorProvider(WidgetColors.textMuted),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}

/** Receiver that the system binds the widget to; declared in the manifest. */
class ContinueReadingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueReadingWidget()
}
