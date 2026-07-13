package com.itexpert120.yomu.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuScreenScaffold
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.ReadingSessionItem
import com.itexpert120.yomu.core.model.ReadingStats
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    YomuScreenScaffold(title = "Statistics", onBack = onBack) {
        Overview(state.stats)
        Entries(state.stats)
        if (state.history.isNotEmpty()) History(state.history)
    }
}

@Composable
private fun ColumnScope.Overview(stats: ReadingStats) {
    SectionTitle("Overview")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(value = stats.booksInLibrary.toString(), label = "Library items")
        MetricCard(value = formatReadingTime(stats.totalReadingSeconds), label = "Read duration")
        MetricCard(value = stats.booksFinished.toString(), label = "Completed")
    }
}

@Composable
private fun ColumnScope.Entries(stats: ReadingStats) {
    SectionTitle("Entries")
    val metrics = listOf(
        stats.chaptersRead.toString() to "Chapters read",
        stats.sessionCount.toString() to "Reading sessions",
        stats.daysRead.toString() to "Active days",
        formatReadingTime(stats.averageSessionSeconds) to "Average session",
        formatReadingTime(stats.longestSessionSeconds) to "Longest session",
        formatReadingTime(stats.averageSecondsPerActiveDay) to "Average per day",
        formatCount(stats.estimatedWordsRead) to "Estimated words",
        if (stats.estimatedReadingSpeedWpm > 0) {
            "${stats.estimatedReadingSpeedWpm} wpm" to "Estimated speed"
        } else {
            "—" to "Estimated speed"
        },
    )
    metrics.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.forEach { (value, label) -> MetricCard(value = value, label = label) }
        }
    }
}

@Composable
private fun RowScope.MetricCard(value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(YomuTheme.radius.md))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.md))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = value,
            color = YomuTheme.colors.textPrimary,
            style = YomuTheme.type.title,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
        )
    }
}

@Composable
private fun ColumnScope.History(history: List<ReadingSessionItem>) {
    SectionTitle("History")
    val consolidated = remember(history) { consolidateSessions(history) }
    var visibleCount by remember { mutableIntStateOf(HistoryPage) }
    val byDay = consolidated.take(visibleCount).groupBy { session ->
        Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        byDay.forEach { (day, sessions) -> HistoryCard(day, sessions) }
    }
    if (consolidated.size > visibleCount) {
        Text(
            text = "Show more",
            color = YomuTheme.colors.accent,
            style = YomuTheme.type.control,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .yomuPressable(onClick = { visibleCount += HistoryPage })
                .clip(RoundedCornerShape(YomuTheme.radius.pill))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun HistoryCard(day: LocalDate, sessions: List<ReadingSessionItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(YomuTheme.radius.lg))
            .background(YomuTheme.colors.surfaceRaised)
            .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.lg))
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = formatDayHeader(day),
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.control,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        sessions.forEach { session -> HistoryRow(session) }
    }
}

@Composable
private fun HistoryRow(session: ReadingSessionItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverThumbnail(session.coverImagePath)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = session.bookTitle,
                color = YomuTheme.colors.textPrimary,
                style = YomuTheme.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatClock(session.startedAt),
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }
        Text(
            text = formatMinutesRead(session.seconds),
            color = YomuTheme.colors.textSecondary,
            style = YomuTheme.type.caption,
        )
    }
}

@Composable
private fun CoverThumbnail(path: String?) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .aspectRatio(1f / 1.5f)
            .clip(RoundedCornerShape(7.dp))
            .background(YomuTheme.colors.surfaceSunken),
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, color = YomuTheme.colors.textPrimary, style = YomuTheme.type.section)
}

private fun consolidateSessions(history: List<ReadingSessionItem>): List<ReadingSessionItem> {
    if (history.size < 2) return history
    val merged = mutableListOf<ReadingSessionItem>()
    var current = history.first()
    for (next in history.drop(1)) {
        val gapSeconds = current.startedAt / 1000 - (next.startedAt / 1000 + next.seconds)
        val bothShort = current.seconds <= ShortSessionSeconds && next.seconds <= ShortSessionSeconds
        current = if (current.bookTitle == next.bookTitle && bothShort && gapSeconds <= MergeGapSeconds) {
            current.copy(
                seconds = current.seconds + next.seconds,
                sessionCount = current.sessionCount + next.sessionCount,
            )
        } else {
            merged += current
            next
        }
    }
    merged += current
    return merged
}

private fun formatReadingTime(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        seconds > 0 -> "<1m"
        else -> "—"
    }
}

private fun formatMinutesRead(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes > 0) "$minutes min" else "<1 min"
}

private fun formatClock(millis: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

private fun formatDayHeader(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val date = Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant())
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
    }
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

private const val HistoryPage = 12
private const val MergeGapSeconds = 20 * 60L
private const val ShortSessionSeconds = 3 * 60L
