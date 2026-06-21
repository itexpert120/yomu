package com.itexpert120.yomu.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.model.DailyReading
import com.itexpert120.yomu.core.model.ReadingSessionItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsRoute(onBack: () -> Unit) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    StatsScreen(state = state, onBack = onBack)
}

@Composable
fun StatsScreen(state: StatsUiState, onBack: () -> Unit) {
    val stats = state.stats
    YomuScreenScaffold(title = "Statistics", onBack = onBack) {
        // Hero: total reading time.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(YomuTheme.radius.lg))
                .background(YomuTheme.colors.surfaceRaised)
                .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Total reading time",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
            Text(
                text = formatReadingTime(stats.totalReadingSeconds),
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.display,
            )
            if (stats.currentStreakDays > 0) {
                Text(
                    text = "🔥 ${stats.currentStreakDays}-day streak",
                    color = YomuTheme.colors.accent,
                    style = YomuTheme.type.body,
                )
            }
        }

        if (state.daily.any { it.seconds > 0L }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(YomuTheme.radius.lg))
                    .background(YomuTheme.colors.surfaceRaised)
                    .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Last 14 days",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
                )
                ReadingChart(
                    daily = state.daily,
                    barColor = YomuTheme.colors.accent,
                    trackColor = YomuTheme.colors.surfaceSunken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "14 days ago",
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.caption
                    )
                    Text(
                        "Today",
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.caption
                    )
                }
            }
        }

        val tiles = listOf(
            "${stats.currentStreakDays}" to "Day streak",
            "${stats.longestStreakDays}" to "Longest streak",
            "${stats.booksStarted}" to "Books started",
            "${stats.booksFinished}" to "Books finished",
            "${stats.chaptersRead}" to "Chapters read",
            "~${formatCount(stats.estimatedWordsRead)}" to "Words read",
            "${stats.booksInLibrary}" to "In library",
        )
        // Two tiles per row.
        tiles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (value, label) -> StatTile(value = value, label = label) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (state.history.isNotEmpty()) {
            Text(
                text = "Recent reading",
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(YomuTheme.radius.lg))
                    .background(YomuTheme.colors.surfaceRaised)
                    .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg)),
            ) {
                state.history.forEach { session -> HistoryRow(session) }
            }
        }
    }
}

/** A simple bar chart of daily reading time, drawn with Canvas (no charting dependency). */
@Composable
private fun ReadingChart(
    daily: List<DailyReading>,
    barColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxSeconds = (daily.maxOfOrNull { it.seconds } ?: 0L).coerceAtLeast(1L)
    Canvas(modifier = modifier) {
        val count = daily.size
        if (count == 0) return@Canvas
        val gap = size.width * 0.02f
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        daily.forEachIndexed { index, day ->
            val x = index * (barWidth + gap)
            // Faint full-height track behind each bar.
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius,
            )
            if (day.seconds > 0L) {
                val frac = (day.seconds.toFloat() / maxSeconds).coerceIn(0f, 1f)
                // Keep a visible minimum so any reading shows.
                val barHeight =
                    (size.height * frac).coerceAtLeast(barWidth.coerceAtMost(size.height))
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(session: ReadingSessionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .aspectRatio(1f / 1.6f)
                .clip(RoundedCornerShape(4.dp))
                .background(YomuTheme.colors.surfaceSunken),
        ) {
            if (session.coverImagePath != null) {
                AsyncImage(
                    model = File(session.coverImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.bookTitle,
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatTimestamp(session.startedAt),
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }
        Text(
            text = formatReadingTime(session.seconds),
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.mono,
        )
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(millis))

@Composable
private fun RowScope.StatTile(value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.md))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = value, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.title)
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
    }
}

private fun formatReadingTime(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "—"
    }
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
